package ua.zefir.zefiroptimizations.process;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.zefir.zefiroptimizations.config.ConfigService;
import ua.zefir.zefiroptimizations.process.message.CastReceipt;
import ua.zefir.zefiroptimizations.process.message.MainNode;
import ua.zefir.zefiroptimizations.process.message.Msg;
import ua.zefir.zefiroptimizations.process.message.ReplySink;
import ua.zefir.zefiroptimizations.process.message.WaitNode;
import ua.zefir.zefiroptimizations.process.ownership.Ownership;
import ua.zefir.zefiroptimizations.process.profile.Profiler;
import ua.zefir.zefiroptimizations.process.snapshot.Snapshots;
import ua.zefir.zefiroptimizations.process.supervision.ProcessCallTimeoutException;
import ua.zefir.zefiroptimizations.process.supervision.ProcessKilledError;
import ua.zefir.zefiroptimizations.process.supervision.Supervisor;
import ua.zefir.zefiroptimizations.process.world.MainQueue;

public final class Processes {
  public static final Logger LOGGER = LoggerFactory.getLogger("ZefirOptimizations/processes");
  private static final int SPINS_BEFORE_YIELD = 64;
  private static final int WAIT_CHAIN_SHOWN = 12;
  private static final long MAX_WAIT_PARK_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
  private static final long BORROW_POLL_NANOS = TimeUnit.MICROSECONDS.toNanos(20);
  static final long CALL_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

  private static final Object RETRY = new Object();
  private static final Object RETRY_AFTER_BACK_OFF = new Object();

  private static final AtomicLong IDS = new AtomicLong();
  private static final AtomicLong CHAINS = new AtomicLong();

  private static final Set<Entity> TICKING = ConcurrentHashMap.newKeySet();

  private static final Set<EntityProcess> LIVE = ConcurrentHashMap.newKeySet();

  private static volatile MinecraftServer server;
  private static volatile Thread mainThread;
  private static volatile boolean enabled;

  public static void start(MinecraftServer minecraftServer) {
    server = minecraftServer;
    mainThread = minecraftServer.getRunningThread();
    MainQueue.mainThread = mainThread;
    boolean installed = Ownership.isInstalled();
    enabled = installed && ConfigService.get().entityProcesses;
    if (enabled) {
      Supervisor.start();
      LOGGER.info("Entity processes enabled");
    } else {
      LOGGER.warn("Entity processes disabled: {}", disabledReason(installed));
    }
  }

  private static String disabledReason(boolean transformerInstalled) {
    return transformerInstalled
        ? "entityProcesses=false in the config"
        : "the entity transformer is not installed";
  }

  public static void stop() {
    enabled = false;
    Supervisor.stop();
    for (EntityProcess process : LIVE) {
      process.thread.interrupt();
    }
    LIVE.clear();
    TICKING.clear();
    MainQueue.drain();
    server = null;
    mainThread = null;
    MainQueue.mainThread = null;
  }

  public static boolean serverRunning() {
    MinecraftServer current = server;
    return current != null && current.isRunning();
  }

  public static Thread mainThread() {
    return mainThread;
  }

  public static void onServerTickStart() {
    if (enabled) {
      Supervisor.onServerTickStart();
    }
  }

  public static void onServerTickEnd() {
    Supervisor.onServerTickEnd();
  }

  private static void afterServerTicked(Entity entity) {
    if (enabled && Supervisor.repromoting() && !(entity instanceof Player)) {
      spawnIfStillTicking(entity);
    }
  }

  public static void dispatchTick(
      Entity entity, DistanceManager tickets, Consumer<Entity> vanillaTick) {
    EntityProcess process = processOf(entity);
    if (process != null) {
      process.requestTick(inEntityTickingRange(tickets, entity));
      return;
    }
    if (Profiler.isActive() && inEntityTickingRange(tickets, entity)) {
      Profiler.serverEntityTicked();
    }
    vanillaTick.accept(entity);
    afterServerTicked(entity);
  }

  private static boolean inEntityTickingRange(DistanceManager tickets, Entity entity) {
    return tickets.inEntityTickingRange(entity.chunkPosition().pack());
  }

  public static boolean enabled() {
    return enabled;
  }

  public static boolean onMain() {
    return Thread.currentThread() == mainThread;
  }

  public static boolean inProcess() {
    return EntityProcess.current() != null;
  }

  static boolean runsInPlace(Thread owner) {
    return runsInPlace(owner, EntityProcess.current());
  }

  static boolean runsInPlace(Thread owner, EntityProcess self) {
    if (owner == mainThread) {
      return self == null;
    }
    return borrowedByMain(owner);
  }

  private static boolean borrowedByMain(Thread owner) {
    return !BORROWED_OWNERS.isEmpty()
        && Thread.currentThread() == mainThread
        && BORROWED_OWNERS.contains(owner);
  }

  private static final java.util.ArrayList<Thread> BORROWED_OWNERS = new java.util.ArrayList<>();

  public static int liveProcesses() {
    return LIVE.size();
  }

  public static java.util.List<EntityProcess> liveProcessList() {
    return new java.util.ArrayList<>(LIVE);
  }

  static boolean ownerAwaits(ProcessEntity entity, Thread owner, EntityProcess process) {
    WaitNode ownerNode;
    if (owner == mainThread) {
      ownerNode = MainNode.INSTANCE;
    } else if (entity.zo$process() instanceof EntityProcess p && p.thread == owner) {
      ownerNode = p;
    } else {
      return false;
    }
    WaitNode first = ownerNode.waitingOn();
    if (first == null) {
      return false;
    }
    WaitNode self = process;
    if (self == null) {
      if (Thread.currentThread() != mainThread) {
        return false;
      }
      self = MainNode.INSTANCE;
    }
    return WaitNode.reaches(first, self);
  }

  public static void atomically(Entity other, Runnable action) {
    if (EntityProcess.current() != null && Guards.route(other)) {
      call(
          other,
          target -> {
            action.run();
            return null;
          });
    } else {
      action.run();
    }
  }

  private static final Set<String> REPORTED_FOREIGN_WRITES = ConcurrentHashMap.newKeySet();

  public static void reportForeignWrite(String what) {
    if (mainThread == null || !REPORTED_FOREIGN_WRITES.add(what)) {
      return;
    }
    LOGGER.warn(
        "Thread {} wrote the {} outside the server thread; this can race with saving and ticking",
        Thread.currentThread().getName(),
        what,
        new Throwable("stack trace"));
  }

  public static EntityProcess processOf(Entity entity) {
    return ((ProcessEntity) entity).zo$process() instanceof EntityProcess process ? process : null;
  }

  static boolean isTicking(Entity entity) {
    return TICKING.contains(entity);
  }

  static void unregister(EntityProcess process) {
    LIVE.remove(process);
  }

  public static void onStartTracking(Entity entity) {
    if (!enabled) {
      return;
    }
    ProcessEntity pe = (ProcessEntity) entity;
    if (pe.zo$owner() == null) {
      pe.zo$setOwner(mainThread);
      Snapshots.publish(entity);
    }
  }

  public static void onStartTicking(Entity entity) {
    TICKING.add(entity);
    spawn(entity);
  }

  static void spawnIfStillTicking(Entity entity) {
    if (TICKING.contains(entity) && !entity.isRemoved()) {
      spawn(entity);
    }
  }

  private static void spawn(Entity entity) {
    if (!enabled || entity instanceof Player || !Supervisor.canRunInProcess(entity)) {
      return;
    }
    EntityProcess existing = processOf(entity);
    if (existing != null) {
      if (!existing.restartAfterExit()) {
        LOGGER.warn("{} already has a live process", describe(entity));
      }
      return;
    }
    ProcessEntity pe = (ProcessEntity) entity;
    if (!claimForMain(pe)) {
      return;
    }
    Snapshots.publish(entity);
    EntityProcess process = new EntityProcess(entity);
    LIVE.add(process);
    pe.zo$setProcess(process);
    pe.zo$setOwner(process.thread);
    process.start();
  }

  private static boolean claimForMain(ProcessEntity pe) {
    if (pe.zo$owner() == null) {
      pe.zo$setOwner(mainThread);
    }
    return pe.zo$owner() == mainThread;
  }

  public static void onStopTicking(Entity entity) {
    TICKING.remove(entity);
    EntityProcess process = processOf(entity);
    if (process != null) {
      process.requestExit(mainThread);
    }
  }

  public static void onStopTracking(Entity entity) {
    EntityProcess process = processOf(entity);
    if (process != null) {
      process.releaseOwnershipOnExit();
      return;
    }
    ProcessEntity pe = (ProcessEntity) entity;
    if (pe.zo$owner() == mainThread) {
      pe.zo$setOwner(null);
    }
  }

  public static Object call(Object target, Invocation invocation) {
    checkNotKilled();
    Object live = liveTarget(target);
    ProcessEntity pe = (ProcessEntity) live;
    for (int spins = 0; ; spins++) {
      Thread owner = pe.zo$owner();
      if (runsHere(owner)) {
        return invokeDirect(invocation, live);
      }
      if (!(pe.zo$process() instanceof EntityProcess process)) {
        if (owner == mainThread) {
          return callMain(invocation, live);
        }
        backOff(spins);
        continue;
      }
      if (process.thread != owner) {
        backOff(spins);
        continue;
      }
      Object result = callOwner(process, pe, live, invocation);
      if (result == RETRY_AFTER_BACK_OFF) {
        backOff(spins);
      } else if (result != RETRY) {
        return result;
      }
    }
  }

  private static Object liveTarget(Object target) {
    Object live = ((ProcessEntity) target).zo$live();
    return live != null ? live : target;
  }

  private static boolean runsHere(Thread owner) {
    return owner == null || owner == Thread.currentThread() || borrowedByMain(owner);
  }

  private static Object callOwner(
      EntityProcess process, ProcessEntity pe, Object target, Invocation invocation) {
    EntityProcess self = EntityProcess.current();
    if (self != null) {
      Msg.Reply reply = self.callAndWait(process, target, invocation);
      return reply.retry() ? RETRY : valueOrThrow(reply);
    }
    if (Thread.currentThread() == mainThread) {
      return callByBorrowing(process, pe, target, invocation);
    }
    return callFromForeignThread(process, target, invocation);
  }

  private static Object callByBorrowing(
      EntityProcess process, ProcessEntity pe, Object target, Invocation invocation) {
    if (!awaitBorrow(process, target)) {
      return RETRY_AFTER_BACK_OFF;
    }
    BORROWED_OWNERS.add(process.thread);
    try {
      process.applyUrgentCasts();
      if (pe.zo$owner() == process.thread) {
        return invokeDirect(invocation, target);
      }
    } finally {
      try {
        process.endBorrow();
      } finally {
        BORROWED_OWNERS.removeLast();
      }
    }
    return RETRY;
  }

  private static Object callFromForeignThread(
      EntityProcess process, Object target, Invocation invocation) {
    ReplySink.Waiter waiter = new ReplySink.Waiter();
    long chain = newChain();
    if (!process.offer(new Msg.Call(nextId(), chain, null, target, invocation, waiter))) {
      return RETRY_AFTER_BACK_OFF;
    }
    awaitPlain(waiter, target);
    return waiter.isRetry() ? RETRY : waiter.valueOrThrow();
  }

  private static Object valueOrThrow(Msg.Reply reply) {
    if (reply.error() != null) {
      throw sneakyThrow(reply.error());
    }
    return reply.value();
  }

  public static void cast(Object target, Invocation invocation) {
    cast(target, invocation, true);
  }

  static void cast(Object target, Invocation invocation, boolean tracked) {
    checkNotKilled();
    Object live = liveTarget(target);
    ProcessEntity pe = (ProcessEntity) live;
    for (int spins = 0; ; spins++) {
      Thread owner = pe.zo$owner();
      if (runsHere(owner)) {
        invokeDirect(invocation, live);
        return;
      }
      if (!(pe.zo$process() instanceof EntityProcess process)) {
        if (owner == mainThread) {
          castToMainOwned(live, invocation, tracked);
          return;
        }
        backOff(spins);
        continue;
      }
      if (process.thread != owner) {
        backOff(spins);
        continue;
      }
      if (offerCast(process, live, invocation, tracked)) {
        return;
      }
      backOff(spins);
    }
  }

  private static void castToMainOwned(Object target, Invocation invocation, boolean tracked) {
    CastReceipt receipt = receipt(target, tracked);
    submitFromCurrent(
        () -> {
          try {
            runCastOnMain(invocation, target);
          } finally {
            receipt.done();
          }
        });
  }

  private static boolean offerCast(
      EntityProcess process, Object target, Invocation invocation, boolean tracked) {
    CastReceipt receipt = receipt(target, tracked);
    if (!process.offer(new Msg.Cast(target, invocation, receipt))) {
      receipt.done();
      return false;
    }
    if (Profiler.isActive()) {
      profileCast(EntityProcess.current() != null, Thread.currentThread() == mainThread);
    }
    return true;
  }

  private static void profileCast(boolean fromProcess, boolean fromMain) {
    Profiler.Kind kind =
        fromProcess
            ? Profiler.Kind.ENTITY_CASTS_ENTITY
            : fromMain ? Profiler.Kind.SERVER_CASTS_ENTITY : null;
    if (kind != null) {
      Profiler.message(kind, 0L);
    }
  }

  private static CastReceipt receipt(Object target, boolean tracked) {
    return tracked ? CastReceipt.tracked(target) : CastReceipt.UNTRACKED;
  }

  public static Object callMain(Invocation invocation, Object target) {
    Thread current = Thread.currentThread();
    if (current == mainThread || mainThread == null) {
      return invokeDirect(invocation, target);
    }
    EntityProcess self = EntityProcess.current();
    if (self != null) {
      checkNotKilled();
      Msg.Reply reply = self.callMainAndWait(invocation, target);
      return reply.retry() ? call(target, invocation) : valueOrThrow(reply);
    }
    ReplySink.Waiter waiter = new ReplySink.Waiter();
    MainQueue.submitCall(mainTask(newChain(), invocation, target, waiter), 0L, null);
    awaitPlain(waiter, target);
    return waiter.isRetry() ? call(target, invocation) : waiter.valueOrThrow();
  }

  public static void castMain(Runnable task) {
    if (Thread.currentThread() == mainThread || mainThread == null) {
      task.run();
    } else {
      submitFromCurrent(task);
    }
  }

  private static void submitFromCurrent(Runnable task) {
    checkNotKilled();
    MainQueue.submit(task);
    if (Profiler.isActive() && EntityProcess.current() != null) {
      Profiler.message(Profiler.Kind.ENTITY_CASTS_SERVER, 0L);
    }
  }

  static Runnable mainTask(long chain, Invocation invocation, Object target, ReplySink sink) {
    return () -> {
      if (target != null && ownedOffMain(target)) {
        sink.reply(null, null, true);
        return;
      }
      MainNode main = MainNode.INSTANCE;
      long previous = main.chain;
      main.chain = chain;
      Object value = null;
      Throwable error = null;
      try {
        value = invocation.invoke(target);
      } catch (Throwable t) {
        error = t;
      } finally {
        main.chain = previous;
      }
      republishIfMainOwned(target);
      sink.reply(value, error, false);
    };
  }

  private static boolean ownedOffMain(Object target) {
    Thread owner = ((ProcessEntity) target).zo$owner();
    return owner != null && owner != mainThread;
  }

  private static void runCastOnMain(Invocation invocation, Object target) {
    if (ownedOffMain(target)) {
      cast(target, invocation);
      return;
    }
    try {
      invocation.invoke(target);
    } catch (Throwable t) {
      LOGGER.error("Cast to {} failed on the server thread", describe(target), t);
    }
    republishIfMainOwned(target);
  }

  public static void republish(Entity entity) {
    if (enabled) {
      republishIfMainOwned(entity);
    }
  }

  private static void republishIfMainOwned(Object target) {
    if (target instanceof Entity entity && ((ProcessEntity) entity).zo$owner() == mainThread) {
      Snapshots.publish(entity);
    }
  }

  private static boolean awaitBorrow(EntityProcess process, Object target) {
    if (process.tryBorrow()) {
      return true;
    }
    MainNode main = MainNode.INSTANCE;
    WaitNode previousNode = main.waitingOn;
    long previousChain = main.chain;
    long chain = mainChain();
    main.waitingOn = process;
    main.chain = chain;
    long started = System.nanoTime();
    long deadline = started + CALL_TIMEOUT_NANOS;
    try {
      while (!process.tryBorrow()) {
        if (process.isClosed()) {
          return false;
        }
        if (MainQueue.runOneRelevant(chain, process)) {
          continue;
        }
        long now = System.nanoTime();
        if (now - deadline > 0) {
          LOGGER.warn("{}; still waiting", waitMessage(now - started, target, process));
          deadline = now + CALL_TIMEOUT_NANOS;
        }
        LockSupport.parkNanos(BORROW_POLL_NANOS);
      }
      return true;
    } finally {
      main.waitingOn = previousNode;
      main.chain = previousChain;
      if (Profiler.isActive()) {
        profileMainWait(System.nanoTime() - started);
      }
    }
  }

  private static void profileMainWait(long waited) {
    Profiler.mainWaited(waited);
    Profiler.message(Profiler.Kind.SERVER_CALLS_ENTITY, waited);
  }

  private static void awaitPlain(ReplySink.Waiter waiter, Object target) {
    long started = System.nanoTime();
    long deadline = started + CALL_TIMEOUT_NANOS;
    while (!waiter.isDone()) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) {
        throw new ProcessCallTimeoutException(
            waitMessage(System.nanoTime() - started, target, null));
      }
      LockSupport.parkNanos(waiter, Math.min(remaining, MAX_WAIT_PARK_NANOS));
    }
  }

  private static String waitMessage(long waitedNanos, Object target, WaitNode waitingOn) {
    return Thread.currentThread().getName()
        + " waited "
        + TimeUnit.NANOSECONDS.toSeconds(waitedNanos)
        + "s for "
        + describe(target)
        + "; wait chain: "
        + describeWaitChain(waitingOn);
  }

  private static void checkNotKilled() {
    EntityProcess self = EntityProcess.current();
    if (self != null && self.isKilled()) {
      throw ProcessKilledError.INSTANCE;
    }
  }

  static long nextId() {
    return IDS.incrementAndGet();
  }

  static long newChain() {
    return CHAINS.incrementAndGet();
  }

  private static long mainChain() {
    long chain = MainNode.INSTANCE.chain;
    return chain != 0 ? chain : newChain();
  }

  private static void backOff(int spins) {
    if (spins < SPINS_BEFORE_YIELD) {
      Thread.onSpinWait();
    } else {
      Thread.yield();
    }
  }

  private static Object invokeDirect(Invocation invocation, Object target) {
    try {
      return invocation.invoke(target);
    } catch (Throwable t) {
      throw sneakyThrow(t);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends Throwable> RuntimeException sneakyThrow(Throwable t) throws T {
    throw (T) t;
  }

  public static String describe(Object target) {
    if (target instanceof Entity entity) {
      ProcessEntity pe = (ProcessEntity) entity;
      if (pe.zo$process() instanceof EntityProcess process && process.stats != null) {
        return process.stats.name;
      }
      Thread owner = pe.zo$owner();
      if (owner == null || owner == Thread.currentThread() || owner == Guards.SNAPSHOT) {
        try {
          return entity.getType().toShortString() + "#" + entity.getId();
        } catch (Throwable ignored) {
        }
      }
      return entity.getClass().getSimpleName();
    }
    return String.valueOf(target);
  }

  public static String describeWaitChain(WaitNode start) {
    StringBuilder out = new StringBuilder();
    java.util.Set<WaitNode> seen =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    WaitNode node = start;
    int shown = 0;
    while (node != null && seen.add(node)) {
      if (shown < WAIT_CHAIN_SHOWN) {
        out.append(shown == 0 ? "" : " -> ").append(describeShort(node));
      }
      shown++;
      if (seen.size() >= WaitNode.MAX_DEPTH) {
        break;
      }
      node = node.waitingOn();
    }
    if (shown > WAIT_CHAIN_SHOWN) {
      out.append(" -> ... (").append(shown - WAIT_CHAIN_SHOWN).append(" more)");
    }
    if (node != null && seen.contains(node)) {
      out.append(" -> back to ").append(describeShort(node)).append(" (cycle)");
    } else if (node == null && shown > 0) {
      out.append(" (running)");
    }
    return shown == 0 ? "none" : out.toString();
  }

  private static String describeShort(WaitNode node) {
    if (node instanceof EntityProcess process) {
      return describe(process.entity);
    }
    return node == MainNode.INSTANCE ? "server thread" : String.valueOf(node);
  }

  public static String describeNode(WaitNode node) {
    if (node instanceof EntityProcess process) {
      return "the process of " + describe(process.entity);
    }
    return node == MainNode.INSTANCE ? "the server thread" : String.valueOf(node);
  }
}
