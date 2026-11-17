package ua.zefir.zefiroptimizations.process;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ua.zefir.zefiroptimizations.process.affinity.CpuAffinity;
import ua.zefir.zefiroptimizations.process.message.CastReceipt;
import ua.zefir.zefiroptimizations.process.message.Mailbox;
import ua.zefir.zefiroptimizations.process.message.MainNode;
import ua.zefir.zefiroptimizations.process.message.Msg;
import ua.zefir.zefiroptimizations.process.message.PendingCasts;
import ua.zefir.zefiroptimizations.process.message.ReplySink;
import ua.zefir.zefiroptimizations.process.message.WaitNode;
import ua.zefir.zefiroptimizations.process.neighbour.NeighbourBuffer;
import ua.zefir.zefiroptimizations.process.profile.ProcessStats;
import ua.zefir.zefiroptimizations.process.profile.ProfileCounters;
import ua.zefir.zefiroptimizations.process.profile.ProfileCounters.Counter;
import ua.zefir.zefiroptimizations.process.profile.Profiler;
import ua.zefir.zefiroptimizations.process.push.Impulse;
import ua.zefir.zefiroptimizations.process.sharedstate.SharedStateError;
import ua.zefir.zefiroptimizations.process.snapshot.Snapshots;
import ua.zefir.zefiroptimizations.process.supervision.ProcessCallTimeoutException;
import ua.zefir.zefiroptimizations.process.supervision.ProcessKilledError;
import ua.zefir.zefiroptimizations.process.supervision.Supervisor;
import ua.zefir.zefiroptimizations.process.world.ChunkCache;
import ua.zefir.zefiroptimizations.process.world.MainQueue;
import ua.zefir.zefiroptimizations.process.world.ScratchLists;
import ua.zefir.zefiroptimizations.process.world.WorldSeq;

public final class EntityProcess implements Runnable, WaitNode {
  public final PendingCasts pendingCasts = new PendingCasts();

  public final ChunkCache chunkCache = new ChunkCache();
  public final ScratchLists scratchLists = new ScratchLists();
  public final NeighbourBuffer neighbours = new NeighbourBuffer();
  public final Impulse impulse = new Impulse();
  private static final long POLL_SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(2);
  private static final int CHECKPOINT_INTERVAL = 100;
  private static final Msg.Reply RETRY = new Msg.Reply(-1, null, null, true);
  static final int MAX_NESTED_WAITS = 32;
  private static final int SNAPSHOT_BATCH_MESSAGES = 8;
  private static final Set<String> UNCHECKPOINTED_TYPES = ConcurrentHashMap.newKeySet();

  public final Entity entity;
  public final Thread thread;

  final RandomSource random = RandomSource.create();

  private final Mailbox mailbox = new Mailbox(this);

  private final ConcurrentLinkedQueue<Msg> urgent = new ConcurrentLinkedQueue<>();

  private final ConcurrentLinkedDeque<Msg> stash = new ConcurrentLinkedDeque<>();

  private final ConcurrentLinkedDeque<Msg.Call> serving = new ConcurrentLinkedDeque<>();
  private int servingDepth;
  private int affinityCountdown;

  private final AtomicBoolean tickQueued = new AtomicBoolean();
  private final AtomicReference<Runnable> trackerTick = new AtomicReference<>();

  private final Map<Object, Runnable> positionUpdates = new LinkedHashMap<>();

  private final ConcurrentLinkedQueue<CastReceipt> unpublishedCasts = new ConcurrentLinkedQueue<>();
  private int unpublishedMessages;

  private volatile boolean closed;

  private final java.util.concurrent.atomic.AtomicInteger lane =
      new java.util.concurrent.atomic.AtomicInteger(1);

  private static final int IDLE = 0;
  private static final int BUSY = 1;
  private static final int BORROWED = 2;
  private static final int PARKED = 3;
  private static final long LANE_WAIT_NANOS = TimeUnit.MICROSECONDS.toNanos(20);

  private int borrowedFrom;
  private volatile boolean killed;

  public volatile long activeSince;

  private volatile boolean tickable;
  private volatile WaitNode waitingOn;

  private long chain;

  private int nestedWaits;

  public int snapshotReads;

  private boolean exitRequested;
  private volatile boolean retiring;

  private Thread ownerAfterExit;
  private boolean exited;
  private boolean restartAfterExit;

  public final ProcessStats stats;

  private CompoundTag checkpoint;
  private int ticksSinceCheckpoint;
  private long skippedTicks;

  EntityProcess(Entity entity) {
    this.entity = entity;
    this.thread =
        Thread.ofVirtual().name("zefiroptimizations-entity-" + entity.getId()).unstarted(this);
    this.stats = new ProcessStats(this, Profiler.typeId(entity), Processes.describe(entity));
  }

  public static EntityProcess current() {
    return ProcessTable.current();
  }

  public Entity entity() {
    return entity;
  }

  @Override
  public WaitNode waitingOn() {
    return waitingOn;
  }

  public CompoundTag checkpoint() {
    return checkpoint;
  }

  public long skippedTicks() {
    return skippedTicks;
  }

  public int mailboxSize() {
    return mailbox.size();
  }

  boolean isServingCalls() {
    return servingDepth > 0;
  }

  public int stashSize() {
    return stash.size();
  }

  public boolean isTickable() {
    return tickable;
  }

  public boolean isKilled() {
    return killed;
  }

  public int backlog() {
    return mailbox.size() + urgent.size() + stash.size();
  }

  void start() {
    thread.start();
  }

  boolean offer(Msg msg) {
    if (closed) {
      return false;
    }
    if ((msg instanceof Msg.Call || msg instanceof Msg.Cast)
        && Thread.currentThread() == Processes.mainThread()) {
      urgent.add(msg);
      mailbox.add(Msg.Wake.INSTANCE);
      return !(closed && urgent.remove(msg));
    }
    mailbox.add(msg);
    return !(closed && mailbox.remove(msg));
  }

  public void deliver(Msg.Reply reply) {
    mailbox.add(reply);
  }

  public void requestTick(boolean tickable) {
    this.tickable = tickable;
    if (tickQueued.compareAndSet(false, true)) {
      if (!offer(Msg.Tick.INSTANCE)) {
        tickQueued.set(false);
      }
    } else {
      skippedTicks++;
    }
  }

  public void deferTrackerTick(Runnable tick) {
    trackerTick.set(tick);
  }

  public void retire() {
    retiring = true;
    requestExit(Processes.mainThread());
  }

  public synchronized void requestExit(Thread nextOwner) {
    ownerAfterExit = nextOwner;
    offer(Msg.Exit.INSTANCE);
  }

  synchronized void releaseOwnershipOnExit() {
    if (exited) {
      ProcessEntity pe = (ProcessEntity) entity;
      if (pe.zo$process() == null && pe.zo$owner() == Processes.mainThread()) {
        pe.zo$setOwner(null);
      }
    } else {
      ownerAfterExit = null;
    }
  }

  synchronized boolean restartAfterExit() {
    if (exited) {
      return false;
    }
    restartAfterExit = true;
    return true;
  }

  public RandomSource random() {
    return random;
  }

  public void deferPositionUpdate(Object listener, Runnable update) {
    positionUpdates.put(listener, update);
  }

  public void deferRemoval(Runnable removal) {
    flushPositionUpdates();
    Processes.castMain(() -> WorldSeq.write(removal));
  }

  private void flushPositionUpdates() {
    if (positionUpdates.isEmpty()) {
      return;
    }
    List<Runnable> updates = new ArrayList<>(positionUpdates.values());
    positionUpdates.clear();
    Processes.castMain(() -> WorldSeq.write(() -> runAll(updates)));
  }

  private static void runAll(List<Runnable> updates) {
    for (Runnable update : updates) {
      update.run();
    }
  }

  @Override
  public void run() {
    ProcessTable.register(this);
    activeSince = System.nanoTime();
    try {
      takeCheckpoint();
      lane.set(IDLE);
      loop();
    } catch (ProcessKilledError killedError) {
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Throwable t) {
      if (!killed) {
        Processes.LOGGER.error("Entity process for {} died", Processes.describe(entity), t);
      }
    } finally {
      activeSince = 0L;
      awaitLaneForExit();
      if (killed) {
        failPending();
      } else {
        shutdown();
      }
      ProcessTable.unregister(this);
    }
  }

  private void loop() throws InterruptedException {
    while (!exitRequested && !killed) {
      Msg msg = nextMessage();
      ProfileCounters.count(this, Counter.MESSAGES);
      pinCarrierPeriodically();
      enterBusy();
      try {
        handle(msg);
        publishBeforeIdle();
      } finally {
        lane.set(IDLE);
      }
    }
  }

  private void pinCarrierPeriodically() {
    if (--affinityCountdown >= 0) {
      return;
    }
    affinityCountdown = CpuAffinity.CARRIER_CHECK_INTERVAL;
    CpuAffinity.pinCarrier();
  }

  private void awaitLaneForExit() {
    while (!killed && !lane.compareAndSet(IDLE, BUSY) && lane.get() != BUSY) {
      LockSupport.parkNanos(LANE_WAIT_NANOS);
    }
  }

  private void enterBusy() {
    takeLane(IDLE);
  }

  private void takeLane(int expected) {
    while (!lane.compareAndSet(expected, BUSY)) {
      if (killed) {
        throw ProcessKilledError.INSTANCE;
      }
      LockSupport.parkNanos(LANE_WAIT_NANOS);
    }
  }

  boolean isClosed() {
    return closed || killed;
  }

  boolean tryBorrow() {
    if (closed || killed) {
      return false;
    }
    int from;
    if (lane.compareAndSet(IDLE, BORROWED)) {
      from = IDLE;
    } else if (lane.compareAndSet(PARKED, BORROWED)) {
      from = PARKED;
    } else {
      return false;
    }
    if (closed || killed) {
      lane.set(from);
      return false;
    }
    borrowedFrom = from;
    return true;
  }

  void applyUrgentCasts() {
    for (Iterator<Msg> it = urgent.iterator(); it.hasNext(); ) {
      if (it.next() instanceof Msg.Cast cast) {
        it.remove();
        try {
          serveCast(cast);
        } finally {
          cast.receipt().done();
        }
      }
    }
  }

  void endBorrow() {
    int from = borrowedFrom;
    try {
      if (from == IDLE) {
        publishSnapshot();
      }
    } catch (Throwable t) {
      Processes.LOGGER.error(
          "Publishing {} after a borrowed call failed", Processes.describe(entity), t);
    } finally {
      lane.set(from);
    }
  }

  private Msg nextMessage() throws InterruptedException {
    Msg msg = urgent.poll();
    if (msg == null) {
      msg = stash.poll();
    }
    if (msg == null) {
      activeSince = 0L;
      msg = mailbox.take();
      activeSince = System.nanoTime();
    }
    return msg;
  }

  private void handle(Msg msg) {
    if (killed) {
      throw ProcessKilledError.INSTANCE;
    }
    chunkCache.clear();
    impulse.applyTo(entity);
    switch (msg) {
      case Msg.Tick tick -> {
        tickQueued.set(false);
        profiledTick();
        afterMessage(true);
      }
      case Msg.Call call -> {
        ProfileCounters.count(this, Counter.CALLS);
        serveCall(call);
        afterMessage(false);
      }
      case Msg.Cast cast -> {
        ProfileCounters.count(this, Counter.CASTS);
        try {
          serveCast(cast);
        } finally {
          unpublishedCasts.add(cast.receipt());
        }
        afterMessage(false);
      }
      case Msg.Exit exit -> exitRequested = true;
      case Msg.Wake wake -> ProfileCounters.count(this, Counter.EMPTY_MESSAGES);
      case Msg.Reply reply -> ProfileCounters.count(this, Counter.EMPTY_MESSAGES);
    }
  }

  private void profiledTick() {
    long previous = chain;
    chain = Processes.newChain();
    ProcessStats s = ProcessStats.of(this);
    long start = s != null ? s.enter(ProcessStats.TICK) : 0L;
    try {
      tickOnce();
    } finally {
      chain = previous;
      if (s != null) {
        s.ticks++;
        s.tickMaxNanos = Math.max(s.tickMaxNanos, s.exit() - start);
      }
    }
  }

  private void serveCall(Msg.Call call) {
    if (killed || retiring) {
      call.sink().reply(null, null, true);
      return;
    }
    serving.addLast(call);
    servingDepth++;
    long previous = chain;
    chain = call.chain();
    ProcessStats s = ProcessStats.of(this);
    if (s != null) {
      s.callsServed++;
      s.enter(ProcessStats.SERVE);
    }
    Object value = null;
    Throwable error = null;
    try {
      value = call.invocation().invoke(call.target());
    } catch (Throwable t) {
      error = t;
    } finally {
      chain = previous;
      serving.removeLastOccurrence(call);
      servingDepth--;
      if (s != null) {
        s.exit();
      }
    }
    if (killed || error instanceof ProcessKilledError) {
      throw ProcessKilledError.INSTANCE;
    }
    if (error instanceof SharedStateError) {
      call.sink().reply(null, null, true);
      return;
    }
    call.sink().reply(value, error, false);
  }

  private void serveCast(Msg.Cast cast) {
    long previous = chain;
    chain = Processes.newChain();
    ProcessStats s = ProcessStats.of(this);
    if (s != null) {
      s.castsServed++;
      s.enter(ProcessStats.SERVE);
    }
    try {
      cast.invocation().invoke(cast.target());
    } catch (ProcessKilledError killedError) {
      throw killedError;
    } catch (SharedStateError stopped) {
      if (killed) {
        throw ProcessKilledError.INSTANCE;
      }
    } catch (Throwable t) {
      if (killed) {
        throw ProcessKilledError.INSTANCE;
      }
      Processes.LOGGER.error("Cast to {} failed", Processes.describe(entity), t);
    } finally {
      chain = previous;
      if (s != null) {
        s.exit();
      }
    }
  }

  private void afterMessage(boolean tick) {
    ProcessStats s = ProcessStats.of(this);
    if (s != null) {
      s.enter(ProcessStats.PUBLISH);
    }
    try {
      publishAndFlush(tick);
    } finally {
      if (s != null) {
        s.exit();
      }
    }
  }

  private void publishBeforeIdle() {
    if (unpublishedMessages == 0 || !urgent.isEmpty() || !stash.isEmpty() || !mailbox.isEmpty()) {
      return;
    }
    ProcessStats s = ProcessStats.of(this);
    if (s != null) {
      s.enter(ProcessStats.PUBLISH);
    }
    try {
      publishSnapshot();
    } finally {
      if (s != null) {
        s.exit();
      }
    }
  }

  private void publishSnapshot() {
    unpublishedMessages = 0;
    ProfileCounters.count(this, Counter.PUBLISHES);
    try {
      Snapshots.publish(entity);
    } finally {
      settleCasts();
    }
  }

  private void settleCasts() {
    CastReceipt receipt;
    while ((receipt = unpublishedCasts.poll()) != null) {
      receipt.done();
    }
  }

  private void publishAndFlush(boolean tick) {
    if (tick || ++unpublishedMessages >= SNAPSHOT_BATCH_MESSAGES) {
      publishSnapshot();
    }
    flushPositionUpdates();
    if (!tick) {
      return;
    }
    runTrackerTick();
    if (++ticksSinceCheckpoint >= CHECKPOINT_INTERVAL) {
      takeCheckpoint();
    }
  }

  private void runTrackerTick() {
    Runnable tracker = trackerTick.getAndSet(null);
    if (tracker == null) {
      return;
    }
    try {
      tracker.run();
    } catch (Throwable t) {
      Processes.LOGGER.error("Tracker tick of {} failed", Processes.describe(entity), t);
    }
  }

  private void takeCheckpoint() {
    ticksSinceCheckpoint = 0;
    try {
      CompoundTag saved = save(entity);
      if (saved != null) {
        checkpoint = saved;
      }
    } catch (Throwable t) {
      if (t instanceof ProcessKilledError || !UNCHECKPOINTED_TYPES.add(stats.type)) {
        return;
      }
      Processes.LOGGER.error(
          "Could not checkpoint {}; {} entities cannot be restarted from a checkpoint",
          Processes.describe(entity),
          stats.type,
          t);
    }
  }

  private static CompoundTag save(Entity entity) {
    /*? if >=1.21.6 {*/
    net.minecraft.world.level.storage.TagValueOutput output =
        net.minecraft.world.level.storage.TagValueOutput.createWithContext(
            net.minecraft.util.ProblemReporter.DISCARDING, entity.registryAccess());
    return entity.save(output) ? output.buildResult() : null;
    /*?} else {*/
    /*CompoundTag nbt = new CompoundTag();
    return entity.save(nbt) ? nbt : null;
    *//*?}*/
  }

  private void tickOnce() {
    try {
      if (entity.isRemoved() || !Processes.serverRunning()) {
        return;
      }
      tickInWorld(entity);
    } catch (ProcessKilledError killedError) {
      throw killedError;
    } catch (SharedStateError stopped) {
      if (killed) {
        throw ProcessKilledError.INSTANCE;
      }
    } catch (ProcessCallTimeoutException timeout) {
      ProcessStats s = ProcessStats.of(this);
      if (s != null) {
        s.timeouts++;
      }
      Supervisor.onTimeout(this, timeout);
    } catch (Throwable t) {
      if (killed) {
        throw ProcessKilledError.INSTANCE;
      }
      Supervisor.onCrash(this, t);
    }
  }

  private void tickInWorld(Entity e) {
    ServerLevel world = (ServerLevel) e.level();
    /*? if <26.1 {*/
    /*if (((ua.zefir.zefiroptimizations.process.mixin.ServerLevelInvoker) world)
        .invokeShouldDiscardEntity(e)) {
      e.discard();
      return;
    }
    *//*?}*/
    if (world.tickRateManager().isEntityFrozen(e)) {
      return;
    }
    e.checkDespawn();
    if (e.isRemoved() || !tickable || dismountUnlessTickedByVehicle(e)) {
      return;
    }
    e.setOldPosAndRot();
    e.tickCount++;
    e.tick();
    for (Entity passenger : e.getPassengers()) {
      tickPassenger(e, passenger);
    }
  }

  private static boolean dismountUnlessTickedByVehicle(Entity e) {
    Entity vehicle = e.getVehicle();
    if (vehicle == null) {
      return false;
    }
    if (!vehicle.isRemoved() && vehicle.hasPassenger(e)) {
      return true;
    }
    e.stopRiding();
    return false;
  }

  static void tickPassenger(Entity vehicle, Entity passenger) {
    if (passenger.isRemoved() || passenger.getVehicle() != vehicle) {
      passenger.stopRiding();
      return;
    }
    if (!(passenger instanceof Player) && !Processes.isTicking(passenger)) {
      return;
    }
    Processes.call(
        passenger,
        self -> {
          Entity p = (Entity) self;
          p.setOldPosAndRot();
          p.tickCount++;
          p.rideTick();
          for (Entity next : p.getPassengers()) {
            tickPassenger(p, next);
          }
          return null;
        });
  }

  Msg.Reply callAndWait(EntityProcess target, Object entityTarget, Invocation invocation) {
    long id = Processes.nextId();
    if (!target.offer(
        new Msg.Call(
            id, chain, this, entityTarget, invocation, new ReplySink.ToProcess(this, id)))) {
      return RETRY;
    }
    return profiledAwait(id, target, Profiler.Kind.ENTITY_CALLS_ENTITY);
  }

  Msg.Reply callMainAndWait(Invocation invocation, Object target) {
    long id = Processes.nextId();
    MainQueue.submitCall(
        Processes.mainTask(chain, invocation, target, new ReplySink.ToProcess(this, id)),
        chain,
        this);
    return profiledAwait(id, MainNode.INSTANCE, Profiler.Kind.ENTITY_CALLS_SERVER);
  }

  private Msg.Reply profiledAwait(long id, WaitNode node, Profiler.Kind kind) {
    ProcessStats s = ProcessStats.of(this);
    if (s == null) {
      return awaitReply(id, node);
    }
    if (kind == Profiler.Kind.ENTITY_CALLS_SERVER) {
      s.callsToMain++;
    } else {
      s.callsToEntities++;
    }
    long start = s.enter(ProcessStats.WAIT);
    try {
      return awaitReply(id, node);
    } finally {
      Profiler.message(kind, s.exit() - start);
    }
  }

  private Msg.Reply awaitReply(long id, WaitNode node) {
    WaitNode previous = waitingOn;
    waitingOn = node;
    nestedWaits++;
    long deadline = System.nanoTime() + Processes.CALL_TIMEOUT_NANOS;
    try {
      while (true) {
        if (killed) {
          throw ProcessKilledError.INSTANCE;
        }
        Msg.Reply fromStash = takeReplyFromStash(id);
        if (fromStash != null) {
          return fromStash;
        }
        if (serveOneServable(urgent) || serveOneServable(stash)) {
          continue;
        }
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          throw waitTimedOut(node);
        }
        Msg.Reply reply = acceptWhileWaiting(pollParked(remaining), id);
        if (reply != null) {
          return reply;
        }
      }
    } catch (InterruptedException e) {
      if (killed) {
        throw ProcessKilledError.INSTANCE;
      }
      Thread.currentThread().interrupt();
      throw new ProcessCallTimeoutException(
          "Process of " + Processes.describe(entity) + " was interrupted");
    } finally {
      nestedWaits--;
      waitingOn = previous;
    }
  }

  private ProcessCallTimeoutException waitTimedOut(WaitNode node) {
    return new ProcessCallTimeoutException(
        "Process of "
            + Processes.describe(entity)
            + " waited "
            + TimeUnit.NANOSECONDS.toSeconds(Processes.CALL_TIMEOUT_NANOS)
            + "s for "
            + Processes.describeNode(node)
            + "; wait chain: "
            + Processes.describeWaitChain(node),
        WaitNode.reaches(node, MainNode.INSTANCE));
  }

  private Msg pollParked(long remaining) throws InterruptedException {
    activeSince = 0L;
    lane.set(PARKED);
    try {
      return mailbox.poll(Math.min(remaining, POLL_SLICE_NANOS));
    } finally {
      takeLane(PARKED);
      activeSince = System.nanoTime();
    }
  }

  private Msg.Reply acceptWhileWaiting(Msg msg, long id) {
    if (msg == null || msg instanceof Msg.Wake) {
      return null;
    }
    if (msg instanceof Msg.Reply reply && reply.id() == id) {
      return reply;
    }
    if (msg instanceof Msg.Call call && canServeWhileWaiting(call)) {
      serveCall(call);
      return null;
    }
    stash.add(msg);
    return null;
  }

  private boolean canServeWhileWaiting(Msg.Call call) {
    if (nestedWaits <= MAX_NESTED_WAITS || call.chain() == chain) {
      return true;
    }
    WaitNode caller = call.caller();
    return caller != null && WaitNode.reaches(waitingOn, caller);
  }

  private Msg.Reply takeReplyFromStash(long id) {
    for (Iterator<Msg> it = stash.iterator(); it.hasNext(); ) {
      if (it.next() instanceof Msg.Reply reply && reply.id() == id) {
        it.remove();
        return reply;
      }
    }
    return null;
  }

  private boolean serveOneServable(Queue<Msg> queue) {
    if (queue.isEmpty()) {
      return false;
    }
    for (Msg msg : queue) {
      if (msg instanceof Msg.Call call && canServeWhileWaiting(call) && queue.remove(call)) {
        serveCall(call);
        return true;
      }
    }
    return false;
  }

  public boolean kill() {
    synchronized (this) {
      if (exited || killed) {
        return false;
      }
      killed = true;
      closed = true;
      exited = true;
      ProcessEntity pe = (ProcessEntity) entity;
      pe.zo$setProcess(null);
      pe.zo$setOwner(Processes.mainThread());
    }
    Processes.unregister(this);
    failPending();
    thread.interrupt();
    return true;
  }

  private void failPending() {
    settleCasts();
    for (Msg.Call call : serving) {
      call.sink().reply(null, null, true);
    }
    drainDead(urgent);
    drainDead(stash);
    drainDead(mailbox);
  }

  private static void drainDead(Queue<Msg> queue) {
    Msg msg;
    while ((msg = queue.poll()) != null) {
      if (msg instanceof Msg.Call call) {
        call.sink().reply(null, null, true);
      } else if (msg instanceof Msg.Cast cast) {
        cast.receipt().done();
      }
    }
  }

  private void shutdown() {
    boolean restart = handOverOwnership();
    flushPositionUpdates();
    Processes.unregister(this);
    forwardRemainingMessages();
    if (restart) {
      Processes.castMain(() -> Processes.spawnIfStillTicking(entity));
    }
  }

  private synchronized boolean handOverOwnership() {
    closed = true;
    try {
      publishSnapshot();
    } catch (Throwable t) {
      Processes.LOGGER.error(
          "Publishing {} before its process exits failed", Processes.describe(entity), t);
    }
    ProcessEntity pe = (ProcessEntity) entity;
    pe.zo$setOwner(ownerAfterExit);
    pe.zo$setProcess(null);
    exited = true;
    return restartAfterExit;
  }

  private void forwardRemainingMessages() {
    Msg msg;
    while ((msg = urgent.poll()) != null
        || (msg = stash.poll()) != null
        || (msg = mailbox.poll()) != null) {
      if (msg instanceof Msg.Call call) {
        call.sink().reply(null, null, true);
      } else if (msg instanceof Msg.Cast cast) {
        cast.receipt().done();
        Processes.castMain(() -> Processes.cast(cast.target(), cast.invocation()));
      }
    }
  }
}
