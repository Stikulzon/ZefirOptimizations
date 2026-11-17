package ua.zefir.zephyrium.process.profile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import ua.zefir.zephyrium.process.Processes;

public final class Profiler {
  private static final String ZO_HANDLER_MARK = "$zo$";
  private static final String LAMBDA_PREFIX = "lambda$";
  private static final String SITE_SEPARATOR = " <- ";
  private static final int SLOWEST_KEPT = 10;
  private static final int CAST_SAMPLING = 16;
  private static final DateTimeFormatter STARTED_AT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  static final class Site {
    final LongAdder count = new LongAdder();
    final LongAdder waitNanos = new LongAdder();
    final LongAccumulator maxWaitNanos = new LongAccumulator(Math::max, 0);
  }

  public enum Kind {
    ENTITY_CALLS_ENTITY("entity -> entity"),
    ENTITY_CALLS_SERVER("entity -> server"),
    SERVER_CALLS_ENTITY("server -> entity"),
    ENTITY_CASTS_ENTITY("entity ~> entity"),
    ENTITY_CASTS_SERVER("entity ~> server"),
    SERVER_CASTS_ENTITY("server ~> entity");

    final String label;

    Kind(String label) {
      this.label = label;
    }
  }

  private record SiteKey(Kind kind, String site) {}

  private static final StackWalker WALKER = StackWalker.getInstance();
  private static final String RUNTIME_PACKAGE = "ua.zefir.zephyrium.process.";

  private static volatile boolean active;
  private static volatile long session;
  private static final ConcurrentLinkedQueue<ProcessStats> STATS = new ConcurrentLinkedQueue<>();
  private static final Map<SiteKey, Site> SITES = new ConcurrentHashMap<>();

  private static long startNanos;
  private static long stopAtNanos;
  private static int requestedSeconds;
  private static String startedAt;
  private static long serverTicks;
  private static long serverTickNanos;
  private static long serverTickMaxNanos;
  private static long tickStartedAt;
  private static long mainWaitNanos;
  private static long mainWaits;
  private static long mainTaskNanos;
  private static long mainTasks;
  private static long serverEntityTicks;
  private static long entityLoopCpuNanos;
  private static long entityLoopAllocatedBytes;
  private static long loopCpuStart;
  private static long loopAllocatedStart;
  private static CpuClock cpuAtStart;
  private static CarrierSampler carrierSampler;

  private Profiler() {}

  public static boolean isActive() {
    return active;
  }

  static long session() {
    return session;
  }

  static void register(ProcessStats stats) {
    STATS.add(stats);
  }

  public static void start(int seconds) {
    STATS.clear();
    SITES.clear();
    serverTicks = serverTickNanos = serverTickMaxNanos = tickStartedAt = 0;
    mainWaitNanos = mainWaits = mainTaskNanos = mainTasks = serverEntityTicks = 0;
    entityLoopCpuNanos = entityLoopAllocatedBytes = 0;
    loopCpuStart = loopAllocatedStart = CpuClock.UNKNOWN;
    requestedSeconds = seconds;
    startedAt = LocalDateTime.now().format(STARTED_AT);
    cpuAtStart = CpuClock.read(Processes.mainThread());
    carrierSampler = CarrierSampler.start();
    startNanos = System.nanoTime();
    stopAtNanos = seconds > 0 ? startNanos + TimeUnit.SECONDS.toNanos(seconds) : 0;
    session++;
    active = true;
  }

  public static boolean isDue() {
    return active && stopAtNanos != 0 && System.nanoTime() - stopAtNanos >= 0;
  }

  public static ProfileResult stop() {
    active = false;
    CpuClock cpuAtStop = CpuClock.read(Processes.mainThread());
    CarrierSampler.Totals carriers = carrierSampler.finish();
    return result(System.nanoTime() - startNanos, cpuAtStart, cpuAtStop, carriers);
  }

  public static void serverEntityTicked() {
    serverEntityTicks++;
  }

  public static long elapsedSeconds() {
    return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos);
  }

  public static int requestedSeconds() {
    return requestedSeconds;
  }

  public static void onServerTickStart() {
    if (active) {
      tickStartedAt = System.nanoTime();
    }
  }

  public static void onServerTickEnd() {
    if (!active || tickStartedAt == 0) {
      return;
    }
    long took = System.nanoTime() - tickStartedAt;
    serverTicks++;
    serverTickNanos += took;
    serverTickMaxNanos = Math.max(serverTickMaxNanos, took);
  }

  public static void entityLoopStarted() {
    if (!active) {
      return;
    }
    loopCpuStart = CpuClock.currentThreadCpuNanos();
    loopAllocatedStart = CpuClock.currentThreadAllocatedBytes();
  }

  public static void entityLoopEnded() {
    if (!active) {
      return;
    }
    entityLoopCpuNanos += CpuClock.since(loopCpuStart, CpuClock.currentThreadCpuNanos());
    entityLoopAllocatedBytes +=
        CpuClock.since(loopAllocatedStart, CpuClock.currentThreadAllocatedBytes());
    loopCpuStart = loopAllocatedStart = CpuClock.UNKNOWN;
  }

  public static void mainWaited(long nanos) {
    mainWaitNanos += nanos;
    mainWaits++;
  }

  public static void mainTaskRan(long nanos) {
    mainTaskNanos += nanos;
    mainTasks++;
  }

  public static void message(Kind kind, long waitNanos) {
    int weight = 1;
    if (waitNanos == 0) {
      if (ThreadLocalRandom.current().nextInt(CAST_SAMPLING) != 0) {
        return;
      }
      weight = CAST_SAMPLING;
    }
    Site site = SITES.computeIfAbsent(new SiteKey(kind, callerSite()), k -> new Site());
    site.count.add(weight);
    if (waitNanos > 0) {
      site.waitNanos.add(waitNanos);
      site.maxWaitNanos.accumulate(waitNanos);
    }
  }

  private static String callerSite() {
    return WALKER.walk(Profiler::siteOf);
  }

  private static String siteOf(Stream<StackWalker.StackFrame> frames) {
    String field = null;
    String what = null;
    for (StackWalker.StackFrame frame : (Iterable<StackWalker.StackFrame>) frames::iterator) {
      String cls = frame.getClassName();
      String method = frame.getMethodName();
      if (isRuntimeFrame(cls)) {
        continue;
      }
      String access = fieldAccess(method);
      if (access != null) {
        field = access;
        continue;
      }
      if (method.startsWith("zo$")) {
        continue;
      }
      String name = frameName(cls, method);
      if (field != null) {
        return field + SITE_SEPARATOR + name;
      }
      if (what == null) {
        what = name;
      } else if (!name.equals(what)) {
        return what + SITE_SEPARATOR + name;
      }
    }
    return what != null ? what : "(runtime)";
  }

  private static boolean isRuntimeFrame(String cls) {
    return cls.startsWith(RUNTIME_PACKAGE) || cls.startsWith("java.") || cls.startsWith("jdk.");
  }

  private static String fieldAccess(String method) {
    if (method.startsWith("zo$fg$") || method.startsWith("zo$fgi$")) {
      return "field read";
    }
    if (method.startsWith("zo$fp$") || method.startsWith("zo$fpi$")) {
      return "field write";
    }
    return null;
  }

  static String frameName(String className, String method) {
    String cls = className.substring(className.lastIndexOf('.') + 1);
    int zo = method.lastIndexOf(ZO_HANDLER_MARK);
    if (zo >= 0) {
      method = method.substring(zo + ZO_HANDLER_MARK.length());
    } else if (method.startsWith(LAMBDA_PREFIX)) {
      method = method.substring(LAMBDA_PREFIX.length());
    }
    int dollar = method.indexOf('$');
    if (dollar > 0) {
      method = method.substring(0, dollar);
    }
    return cls + "." + method;
  }

  private static ProfileResult result(
      long durationNanos, CpuClock start, CpuClock end, CarrierSampler.Totals carriers) {
    List<ProcessStats> stats = new ArrayList<>(STATS);
    List<ProfileResult.TypeTotals> types = typeTotals(stats);
    return new ProfileResult(
        new ProfileResult.Window(
            durationNanos, requestedSeconds, stats.size(), carrierThreads(), startedAt),
        new ProfileResult.ServerTotals(
            serverTicks,
            serverTickNanos,
            serverTickMaxNanos,
            mainWaitNanos,
            mainWaits,
            mainTaskNanos,
            mainTasks),
        entityCost(types, start, end, carriers),
        ProfileCounters.sum(stats),
        types,
        slowestTicks(stats),
        siteTotals());
  }

  private static ProfileResult.EntityCost entityCost(
      List<ProfileResult.TypeTotals> types,
      CpuClock start,
      CpuClock end,
      CarrierSampler.Totals carriers) {
    long allocated =
        carriers.allocatedBytes() < 0
            ? CpuClock.UNKNOWN
            : carriers.allocatedBytes() + entityLoopAllocatedBytes;
    return new ProfileResult.EntityCost(
        sum(types, ProfileResult.TypeTotals::ticks),
        serverEntityTicks,
        sum(types, ProfileResult.TypeTotals::skipped),
        carriers.cpuNanos(),
        entityLoopCpuNanos,
        mainTaskNanos,
        CpuClock.since(start.processNanos, end.processNanos),
        CpuClock.since(start.serverNanos, end.serverNanos),
        allocated,
        end.gcMillis - start.gcMillis,
        end.gcCount - start.gcCount,
        CpuClock.since(start.jitMillis, end.jitMillis));
  }

  private static int carrierThreads() {
    return Integer.getInteger(
        "jdk.virtualThreadScheduler.parallelism", Runtime.getRuntime().availableProcessors());
  }

  private static List<ProfileResult.TypeTotals> typeTotals(List<ProcessStats> stats) {
    Map<String, List<ProcessStats>> byType = new HashMap<>();
    for (ProcessStats s : stats) {
      byType.computeIfAbsent(s.type, t -> new ArrayList<>()).add(s);
    }
    List<ProfileResult.TypeTotals> types = new ArrayList<>();
    byType.forEach((type, list) -> types.add(typeTotals(type, list)));
    types.sort(Comparator.comparingLong(ProfileResult.TypeTotals::busyNanos).reversed());
    return types;
  }

  private static ProfileResult.TypeTotals typeTotals(String type, List<ProcessStats> list) {
    return new ProfileResult.TypeTotals(
        type,
        list.size(),
        sum(list, s -> s.ticks),
        sum(list, s -> s.nanos[ProcessStats.TICK]),
        list.stream().mapToLong(s -> s.tickMaxNanos).max().orElse(0),
        sum(list, s -> s.nanos[ProcessStats.SERVE]),
        sum(list, s -> s.nanos[ProcessStats.WAIT]),
        sum(list, s -> s.nanos[ProcessStats.PUBLISH]),
        sum(list, s -> s.callsToEntities),
        sum(list, s -> s.callsToMain),
        sum(list, s -> s.callsServed + s.castsServed),
        sum(list, ProcessStats::skippedTicks),
        sum(list, s -> s.timeouts),
        sum(list, s -> s.crashes));
  }

  private static List<ProfileResult.SlowTick> slowestTicks(List<ProcessStats> stats) {
    return stats.stream()
        .sorted(Comparator.comparingLong((ProcessStats s) -> s.tickMaxNanos).reversed())
        .limit(SLOWEST_KEPT)
        .map(
            s ->
                new ProfileResult.SlowTick(
                    s.name,
                    s.tickMaxNanos,
                    s.nanos[ProcessStats.TICK] / Math.max(1, s.ticks),
                    s.ticks,
                    s.nanos[ProcessStats.WAIT]))
        .toList();
  }

  private static List<ProfileResult.SiteTotals> siteTotals() {
    return SITES.entrySet().stream()
        .map(
            e ->
                new ProfileResult.SiteTotals(
                    e.getKey().kind().label,
                    e.getKey().site(),
                    e.getValue().count.sum(),
                    e.getValue().waitNanos.sum(),
                    e.getValue().maxWaitNanos.get()))
        .sorted(
            Comparator.comparingLong(ProfileResult.SiteTotals::waitNanos)
                .thenComparingLong(ProfileResult.SiteTotals::count)
                .reversed())
        .toList();
  }

  private static <T> long sum(List<T> list, ToLongFunction<T> value) {
    long total = 0;
    for (T item : list) {
      total += value.applyAsLong(item);
    }
    return total;
  }

  public static String typeId(Entity entity) {
    return EntityType.getKey(entity.getType()).toString();
  }
}
