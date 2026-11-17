package ua.zefir.zefiroptimizations.process.supervision;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import ua.zefir.zefiroptimizations.process.EntityProcess;
import ua.zefir.zefiroptimizations.process.Processes;
import ua.zefir.zefiroptimizations.process.message.MainNode;
import ua.zefir.zefiroptimizations.process.message.WaitNode;
import ua.zefir.zefiroptimizations.process.ownership.Ownership;
import ua.zefir.zefiroptimizations.process.profile.ProcessStats;
import ua.zefir.zefiroptimizations.process.profile.Profiler;
import ua.zefir.zefiroptimizations.process.sharedstate.SharedState;
import ua.zefir.zefiroptimizations.process.world.MainQueue;

public final class Supervisor {
  private static final long RESTART_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(60);
  private static final int MAX_RESTARTS = 3;
  private static final int TYPE_MAX_FAILURES = 20;
  private static final long TYPE_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(30);
  private static final long DEMOTION_NANOS = TimeUnit.SECONDS.toNanos(120);
  private static final long BLOCKING_SERVER_NANOS = TimeUnit.SECONDS.toNanos(2);
  private static final long HANG_NANOS = TimeUnit.SECONDS.toNanos(5);
  private static final long FREEZE_REPORT_NANOS = TimeUnit.SECONDS.toNanos(3);
  private static final long HEARTBEAT_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
  private static final long LOG_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(10);
  private static final int LOGGED_PER_WINDOW = 3;
  private static final long STALL_SLACK_NANOS = TimeUnit.SECONDS.toNanos(1);
  private static final int PARKED_FRAMES_CHECKED = 8;
  private static final int BUSIEST_SHOWN = 5;
  private static final int GC_SAMPLE_TICKS = 20;
  private static final long FREEZE_REPORT_REPEAT_NANOS = TimeUnit.SECONDS.toNanos(10);
  private static final int KILL_STACK_FRAMES = 12;
  private static final int FREEZE_STACK_FRAMES = 40;
  private static final int BYTES_TO_MB_SHIFT = 20;
  private static final double NANOS_PER_SECOND = 1e9;
  private static final double MILLIS_PER_SECOND = 1e3;

  private static final Map<UUID, ArrayDeque<Long>> CRASHES = new ConcurrentHashMap<>();
  private static final Map<String, ArrayDeque<Long>> TYPE_FAILURES = new ConcurrentHashMap<>();

  private static final Map<String, Long> DEMOTED_UNTIL = new ConcurrentHashMap<>();

  private static final Map<String, AtomicInteger[]> LOG_COUNTS = new ConcurrentHashMap<>();

  private static final AtomicLong RESTARTS = new AtomicLong();
  private static final AtomicLong ROLLBACKS = new AtomicLong();
  private static final AtomicLong KILLS = new AtomicLong();
  private static final AtomicLong DEATHS = new AtomicLong();

  private static boolean repromote;

  private static volatile long tickStartedAt;
  private static volatile long tickEndedAt;
  private static volatile Thread heart;

  private static final ClassValue<Boolean> GUARDED =
      new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
          for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (Ownership.failedClasses().contains(c.getName().replace('.', '/'))) {
              return false;
            }
          }
          return true;
        }
      };

  public static void start() {
    SharedState.arm();
    CRASHES.clear();
    TYPE_FAILURES.clear();
    DEMOTED_UNTIL.clear();
    LOG_COUNTS.clear();
    tickStartedAt = 0L;
    tickEndedAt = 0L;
    Thread thread =
        Thread.ofPlatform()
            .daemon()
            .name("zefiroptimizations-supervisor")
            .unstarted(Supervisor::heartbeat);
    heart = thread;
    thread.start();
  }

  public static void stop() {
    Thread thread = heart;
    heart = null;
    if (thread != null) {
      thread.interrupt();
    }
  }

  public static void onServerTickStart() {
    long now = System.nanoTime();
    tickStartedAt = now;
    sampleGcPeriodically();
    repromote = expireDemotions(now);
  }

  private static void sampleGcPeriodically() {
    if (--gcSampleCountdown > 0) {
      return;
    }
    gcSampleCountdown = GC_SAMPLE_TICKS;
    gcMillisAtTickStart = gcMillis();
  }

  private static boolean expireDemotions(long now) {
    if (DEMOTED_UNTIL.isEmpty()) {
      return false;
    }
    boolean expired = false;
    for (Iterator<Map.Entry<String, Long>> it = DEMOTED_UNTIL.entrySet().iterator();
        it.hasNext(); ) {
      Map.Entry<String, Long> entry = it.next();
      if (now - entry.getValue() < 0) {
        continue;
      }
      it.remove();
      Processes.LOGGER.info("{} entities run in processes again", entry.getKey());
      expired = true;
    }
    return expired;
  }

  public static void onServerTickEnd() {
    tickStartedAt = 0L;
    tickEndedAt = System.nanoTime();
  }

  public static boolean repromoting() {
    return repromote;
  }

  public static boolean canRunInProcess(Entity entity) {
    return GUARDED.get(entity.getClass())
        && SharedState.isSafe(entity.getClass())
        && (DEMOTED_UNTIL.isEmpty() || !DEMOTED_UNTIL.containsKey(Profiler.typeId(entity)));
  }

  public static String whyNotInProcess(Entity entity) {
    if (!GUARDED.get(entity.getClass())) {
      return "its class could not be guarded";
    }
    String sharedState = SharedState.reason(entity.getClass());
    if (sharedState != null) {
      return "it touches shared state: " + sharedState;
    }
    return "its type was demoted after repeated failures";
  }

  public static void retireUnsafeProcesses() {
    for (EntityProcess process : Processes.liveProcessList()) {
      if (!SharedState.isSafe(process.entity.getClass())) {
        process.retire();
      }
    }
  }

  public static void onCrash(EntityProcess process, Throwable crash) {
    Entity entity = process.entity;
    String name = Processes.describe(entity);
    String type = Profiler.typeId(entity);
    countCrash(process);
    log("crash", type, () -> "Entity process of " + name + " crashed", crash);
    recordTypeFailure(type);

    boolean mayRestart = recordCrash(entity.getUUID());
    CompoundTag checkpoint = process.checkpoint();
    if (entity.isRemoved()) {
      Processes.LOGGER.warn("{} crashed after it left the world, so it is not restarted", name);
      return;
    }
    boolean restart = canRespawn(entity, mayRestart, checkpoint);
    ServerLevel world = (ServerLevel) entity.level();
    try {
      entity.discard();
    } catch (Throwable t) {
      Processes.LOGGER.error("Could not discard crashed {}", name, t);
      return;
    }
    if (!restart) {
      stayDead(name, mayRestart);
      return;
    }
    Processes.castMain(() -> respawn(checkpoint, world, name, type));
  }

  private static void countCrash(EntityProcess process) {
    ProcessStats stats = ProcessStats.of(process);
    if (stats != null) {
      stats.crashes++;
    }
  }

  private static boolean canRespawn(Entity entity, boolean mayRestart, CompoundTag checkpoint) {
    return mayRestart && checkpoint != null && !entity.isPassenger() && !entity.isVehicle();
  }

  private static void stayDead(String name, boolean mayRestart) {
    DEATHS.incrementAndGet();
    Processes.LOGGER.error("{} stays dead: {}", name, stayDeadReason(mayRestart));
  }

  private static String stayDeadReason(boolean mayRestart) {
    return mayRestart
        ? "no usable checkpoint"
        : "failed more than " + MAX_RESTARTS + " times in a minute";
  }

  public static void onTimeout(EntityProcess process, ProcessCallTimeoutException timeout) {
    Entity entity = process.entity;
    String name = Processes.describe(entity);
    String type = Profiler.typeId(entity);
    boolean entityToBlame = !timeout.blamesServer();
    if (entityToBlame) {
      recordTypeFailure(type);
      if (!recordCrash(entity.getUUID())) {
        onCrash(process, timeout);
        return;
      }
    }
    CompoundTag checkpoint = process.checkpoint();
    if (checkpoint != null) {
      try {
        loadCheckpoint(entity, checkpoint);
      } catch (Throwable t) {
        onCrash(process, t);
        return;
      }
    }
    ROLLBACKS.incrementAndGet();
    log(
        entityToBlame ? "timeout" : "timeout on the server thread",
        type,
        () ->
            "Tick of "
                + name
                + " gave up waiting and was rolled back to its last checkpoint: "
                + timeout.getMessage(),
        null);
  }

  private static void loadCheckpoint(Entity entity, CompoundTag checkpoint) {
    /*? if >=1.21.6 {*/
    entity.load(
        net.minecraft.world.level.storage.TagValueInput.create(
            net.minecraft.util.ProblemReporter.DISCARDING, entity.registryAccess(), checkpoint));
    /*?} else {*/
    /*entity.load(checkpoint);
    *//*?}*/
  }

  private static void respawn(CompoundTag checkpoint, ServerLevel world, String name, String type) {
    /*? if >=26.2 {*/
    EntityType.create(
            net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, world.registryAccess(), checkpoint),
            world,
            new net.minecraft.world.entity.EntitySpawnRequest(
                net.minecraft.world.entity.EntitySpawnReason.LOAD, false))
        /*?} else {*/
        /*EntityType.create(checkpoint, world)
        *//*?}*/
        .ifPresentOrElse(
            restarted -> {
              if (world.getEntity(restarted.getUUID()) != null) {
                Processes.LOGGER.warn(
                    "{} is still in the world, so it is not respawned from its checkpoint", name);
                return;
              }
              if (world.addFreshEntity(restarted)) {
                RESTARTS.incrementAndGet();
                log("restart", type, () -> "Restarted " + name + " from its last checkpoint", null);
              } else {
                Processes.LOGGER.error("Could not respawn {} from its checkpoint", name);
              }
            },
            () -> Processes.LOGGER.error("Checkpoint of {} could not be loaded", name));
  }

  private static boolean recordCrash(UUID uuid) {
    return withinIntensity(
        CRASHES.computeIfAbsent(uuid, u -> new ArrayDeque<>()),
        System.nanoTime(),
        RESTART_WINDOW_NANOS,
        MAX_RESTARTS);
  }

  private static void recordTypeFailure(String type) {
    if (!withinIntensity(
        TYPE_FAILURES.computeIfAbsent(type, t -> new ArrayDeque<>()),
        System.nanoTime(),
        TYPE_WINDOW_NANOS,
        TYPE_MAX_FAILURES)) {
      demote(type);
    }
  }

  private static boolean withinIntensity(ArrayDeque<Long> history, long now, long window, int max) {
    synchronized (history) {
      while (!history.isEmpty() && now - history.peekFirst() > window) {
        history.pollFirst();
      }
      history.addLast(now);
      return history.size() <= max;
    }
  }

  private static void demote(String type) {
    if (DEMOTED_UNTIL.putIfAbsent(type, System.nanoTime() + DEMOTION_NANOS) != null) {
      return;
    }
    TYPE_FAILURES.remove(type);
    Processes.LOGGER.warn(
        "{} entities failed more than {} times in {} s; the server thread ticks them for the next {} s",
        type,
        TYPE_MAX_FAILURES,
        TimeUnit.NANOSECONDS.toSeconds(TYPE_WINDOW_NANOS),
        TimeUnit.NANOSECONDS.toSeconds(DEMOTION_NANOS));
    exitProcessesOfType(type, Processes.liveProcessList(), Processes.mainThread());
  }

  private static void exitProcessesOfType(
      String type, Iterable<EntityProcess> processes, Thread nextOwner) {
    for (EntityProcess process : processes) {
      if (process.stats.type.equals(type)) {
        process.requestExit(nextOwner);
      }
    }
  }

  private static void heartbeat() {
    Beat beat = new Beat(System.nanoTime());
    while (heart == Thread.currentThread()) {
      try {
        TimeUnit.NANOSECONDS.sleep(HEARTBEAT_NANOS);
      } catch (InterruptedException e) {
        return;
      }
      long now = System.nanoTime();
      beat.advanceTo(now);
      try {
        beatOnce(now, beat);
      } catch (Throwable t) {
        Processes.LOGGER.error("Supervisor heartbeat failed", t);
      }
    }
  }

  private static void beatOnce(long now, Beat beat) {
    killHungProcesses(now, beat.resumedAt);
    reportFreezeIfStuck(now, beat);
    flushLogIfDue(now, beat);
  }

  private static void reportFreezeIfStuck(long now, Beat beat) {
    long tickStart = tickStartedAt;
    boolean inTick = tickStart != 0L;
    long started = inTick ? tickStart : Processes.serverRunning() ? tickEndedAt : 0L;
    if (started == 0L || now - started <= FREEZE_REPORT_NANOS) {
      beat.nextFreezeReport = 0L;
      return;
    }
    if (now - beat.nextFreezeReport < 0) {
      return;
    }
    reportFreeze(now - started, started, inTick);
    beat.nextFreezeReport = now + FREEZE_REPORT_REPEAT_NANOS;
  }

  private static void flushLogIfDue(long now, Beat beat) {
    if (now - beat.nextLogFlush < 0) {
      return;
    }
    flushLog();
    beat.nextLogFlush = now + LOG_WINDOW_NANOS;
  }

  private static boolean isParked(StackTraceElement[] stack) {
    for (int i = 0; i < Math.min(stack.length, PARKED_FRAMES_CHECKED); i++) {
      if (isParkFrame(stack[i].getClassName(), stack[i].getMethodName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isParkFrame(String cls, String method) {
    return cls.equals("jdk.internal.vm.Continuation")
        || cls.equals("jdk.internal.misc.Unsafe") && method.equals("park")
        || cls.equals("java.lang.VirtualThread")
            && (method.startsWith("park") || method.contains("yield") || method.contains("Yield"));
  }

  private static EntityProcess blockingServer() {
    WaitNode node = MainNode.INSTANCE.waitingOn;
    for (int i = 0; node instanceof EntityProcess process && i < WaitNode.MAX_DEPTH; i++) {
      WaitNode next = process.waitingOn();
      if (next == null) {
        return process;
      }
      if (next == MainNode.INSTANCE) {
        return null;
      }
      node = next;
    }
    return null;
  }

  private static void killHungProcesses(long now, long resumedAt) {
    EntityProcess blocking = blockingServer();
    for (EntityProcess process : Processes.liveProcessList()) {
      long since = process.activeSince;
      boolean blocksServer = process == blocking;
      if (since == 0L || now - Math.max(since, resumedAt) < hangLimit(blocksServer)) {
        continue;
      }
      StackTraceElement[] stack = process.thread.getStackTrace();
      if (isParked(stack) || !process.kill()) {
        continue;
      }
      onKilled(process, stack, now - since, blocksServer);
    }
  }

  private static long hangLimit(boolean blocksServer) {
    return blocksServer ? BLOCKING_SERVER_NANOS : HANG_NANOS;
  }

  private static void onKilled(
      EntityProcess process, StackTraceElement[] stack, long ranNanos, boolean blocksServer) {
    KILLS.incrementAndGet();
    Entity entity = process.entity;
    String name = process.stats.name;
    String type = process.stats.type;
    Processes.LOGGER.error(
        "Killed the process of {}: it ran for {} s without ever waiting{}. It was at:{}",
        name,
        String.format(Locale.ROOT, "%.1f", ranNanos / NANOS_PER_SECOND),
        blocksServer ? " while the server thread waited for it" : "",
        formatStack(stack, KILL_STACK_FRAMES));
    if (!blocksServer || ranNanos >= HANG_NANOS) {
      recordTypeFailure(type);
    }
    boolean mayRestart = recordCrash(entity.getUUID());
    CompoundTag checkpoint = process.checkpoint();
    Processes.castMain(() -> discardKilled(entity, name, type, mayRestart, checkpoint));
  }

  private static void discardKilled(
      Entity entity, String name, String type, boolean mayRestart, CompoundTag checkpoint) {
    ServerLevel world = (ServerLevel) entity.level();
    if (entity.isRemoved()) {
      Processes.LOGGER.warn("{} was killed after it left the world, so it is not restarted", name);
      return;
    }
    boolean restart = canRespawn(entity, mayRestart, checkpoint);
    entity.discard();
    if (restart) {
      respawn(checkpoint, world, name, type);
    } else {
      stayDead(name, mayRestart);
    }
  }

  private static void reportFreeze(long stuckNanos, long tickStart, boolean inTick) {
    Thread main = Processes.mainThread();
    if (main == null) {
      return;
    }
    long gcDuringTick = gcMillisSince(tickStart, gcMillis());
    List<EntityProcess> processes = Processes.liveProcessList();
    int waitingOnMain = countWaitingOnMain(processes);
    String busiest = describeLargestMailboxes(processes);
    Runtime runtime = Runtime.getRuntime();
    Processes.LOGGER.warn(
        String.format(
            Locale.ROOT,
            "%s for %.1f s (%.1f s of it in GC; heap %d of %d MB). Server thread waits on: %s. "
                + "%d entity processes, %d waiting on the server thread, %d tasks queued for it. Largest mailboxes: %s. "
                + "Server thread is at:%s",
            inTick ? "Server tick stuck" : "Server thread stuck between ticks",
            stuckNanos / NANOS_PER_SECOND,
            gcDuringTick / MILLIS_PER_SECOND,
            (runtime.totalMemory() - runtime.freeMemory()) >> BYTES_TO_MB_SHIFT,
            runtime.maxMemory() >> BYTES_TO_MB_SHIFT,
            Processes.describeWaitChain(MainNode.INSTANCE.waitingOn),
            processes.size(),
            waitingOnMain,
            MainQueue.size(),
            busiest.isEmpty() ? "none" : busiest,
            formatStack(main.getStackTrace(), FREEZE_STACK_FRAMES)));
  }

  private static long gcMillisSince(long tickStart, long gcMillis) {
    if (tickStart != gcTickStart) {
      gcTickStart = tickStart;
      gcAtTickStart = gcMillisAtTickStart;
    }
    return Math.max(0L, gcMillis - gcAtTickStart);
  }

  private static int countWaitingOnMain(List<EntityProcess> processes) {
    int waitingOnMain = 0;
    for (EntityProcess process : processes) {
      if (process.waitingOn() == MainNode.INSTANCE) {
        waitingOnMain++;
      }
    }
    return waitingOnMain;
  }

  private static String describeLargestMailboxes(List<EntityProcess> processes) {
    List<Backlog> backlogs = new ArrayList<>(processes.size());
    for (EntityProcess process : processes) {
      backlogs.add(new Backlog(process, process.backlog()));
    }
    backlogs.sort(Comparator.comparingInt(Backlog::size).reversed());
    StringBuilder busiest = new StringBuilder();
    for (int i = 0;
        i < Math.min(BUSIEST_SHOWN, backlogs.size()) && backlogs.get(i).size() > 0;
        i++) {
      Backlog backlog = backlogs.get(i);
      busiest
          .append(i == 0 ? "" : ", ")
          .append(Processes.describe(backlog.process().entity))
          .append(" (")
          .append(backlog.size())
          .append(')');
    }
    return busiest.toString();
  }

  private static long gcTickStart;
  private static long gcAtTickStart;

  private static volatile long gcMillisAtTickStart;

  private static int gcSampleCountdown;

  private static long gcMillis() {
    long total = 0L;
    for (java.lang.management.GarbageCollectorMXBean gc :
        java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
      total += Math.max(0L, gc.getCollectionTime());
    }
    return total;
  }

  private static String formatStack(StackTraceElement[] stack, int max) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < Math.min(max, stack.length); i++) {
      out.append("\n\tat ").append(stack[i]);
    }
    if (stack.length > max) {
      out.append("\n\t... ").append(stack.length - max).append(" more");
    }
    return out.toString();
  }

  private static void log(
      String kind, String type, java.util.function.Supplier<String> message, Throwable error) {
    AtomicInteger[] counts =
        LOG_COUNTS.computeIfAbsent(
            kind + ' ' + type, k -> new AtomicInteger[] {new AtomicInteger(), new AtomicInteger()});
    if (counts[0].incrementAndGet() <= LOGGED_PER_WINDOW) {
      if (error != null) {
        Processes.LOGGER.error(message.get(), error);
      } else {
        Processes.LOGGER.warn(message.get());
      }
    } else {
      counts[1].incrementAndGet();
    }
  }

  private static void flushLog() {
    for (Map.Entry<String, AtomicInteger[]> entry : LOG_COUNTS.entrySet()) {
      AtomicInteger[] counts = entry.getValue();
      int suppressed = counts[1].getAndSet(0);
      counts[0].set(0);
      if (suppressed == 0) {
        continue;
      }
      String[] kindType = splitKindType(entry.getKey());
      Processes.LOGGER.warn(
          "... and {} more {} of {} entities in the last {} s",
          suppressed,
          kindType[0],
          kindType[1],
          TimeUnit.NANOSECONDS.toSeconds(LOG_WINDOW_NANOS));
    }
  }

  private static String[] splitKindType(String key) {
    int space = key.lastIndexOf(' ');
    String kind = key.substring(0, space);
    return new String[] {
      kind.endsWith("crash") ? kind + "es" : kind + "s", key.substring(space + 1)
    };
  }

  public record Status(
      long restarts, long rollbacks, long kills, long deaths, Map<String, Long> demotedSeconds) {}

  public static Status status() {
    long now = System.nanoTime();
    Map<String, Long> demoted = new TreeMap<>();
    for (Map.Entry<String, Long> entry : DEMOTED_UNTIL.entrySet()) {
      demoted.put(
          entry.getKey(), Math.max(0, TimeUnit.NANOSECONDS.toSeconds(entry.getValue() - now)));
    }
    return new Status(RESTARTS.get(), ROLLBACKS.get(), KILLS.get(), DEATHS.get(), demoted);
  }

  private record Backlog(EntityProcess process, int size) {}

  private static final class Beat {
    private long last;
    private long resumedAt;
    private long nextLogFlush;
    private long nextFreezeReport;

    private Beat(long now) {
      last = now;
      resumedAt = now;
      nextLogFlush = now + LOG_WINDOW_NANOS;
    }

    private void advanceTo(long now) {
      if (now - last > HEARTBEAT_NANOS + STALL_SLACK_NANOS) {
        resumedAt = now;
      }
      last = now;
    }
  }
}
