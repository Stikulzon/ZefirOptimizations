package ua.zefir.zephyrium.process;

import ua.zefir.zephyrium.process.message.PendingCasts;
import ua.zefir.zephyrium.process.snapshot.SnapshotReadScope;
import ua.zefir.zephyrium.process.snapshot.SnapshotReads;
import ua.zefir.zephyrium.process.supervision.ProcessKilledError;

public final class Guards {
  public static final Thread SNAPSHOT = new Thread(() -> {}, "zephyrium-snapshot-marker");

  public static final int DIRECT = 0;
  public static final int FROM_SNAPSHOT = 1;
  public static final int CALL = 2;

  public static boolean route(Object entity) {
    Thread owner = ((ProcessEntity) entity).zo$owner();
    return owner != null && owner != Thread.currentThread() && !Processes.runsInPlace(owner);
  }

  public static int readMode(Object entity) {
    ProcessEntity pe = (ProcessEntity) entity;
    Thread owner = pe.zo$owner();
    Thread current = Thread.currentThread();
    if (owner == null || owner == SNAPSHOT || owner == current) {
      return DIRECT;
    }
    Thread main = Processes.mainThread();
    if (owner != main
        && current != main
        && pe.zo$readGate() == 0
        && pe.zo$snapshot() != null
        && !servesCalls()) {
      return FROM_SNAPSHOT;
    }
    return readModeOther(pe, owner);
  }

  public static int atomicReadMode(Object entity) {
    ProcessEntity pe = (ProcessEntity) entity;
    Thread owner = pe.zo$owner();
    if (owner == null || owner == SNAPSHOT || owner == Thread.currentThread()) {
      return DIRECT;
    }
    if (pe.zo$readGate() == 0) {
      return DIRECT;
    }
    return readModeOther(pe, owner);
  }

  private static boolean servesCalls() {
    EntityProcess self = EntityProcess.current();
    return self == null || self.isServingCalls();
  }

  private static int readModeOther(ProcessEntity pe, Thread owner) {
    EntityProcess self = EntityProcess.current();
    if (self != null) {
      if (self.isKilled()) {
        throw ProcessKilledError.INSTANCE;
      }
    } else if (Processes.runsInPlace(owner, null)) {
      return DIRECT;
    }
    if (Processes.ownerAwaits(pe, owner, self)) {
      return DIRECT;
    }
    if (pe.zo$snapshot() == null || PendingCasts.of(self).has(pe)) {
      return CALL;
    }
    return FROM_SNAPSHOT;
  }

  public static int lazyReadMode(Object entity, int site) {
    ProcessEntity pe = (ProcessEntity) entity;
    Thread owner = pe.zo$owner();
    if (owner == null || owner == Thread.currentThread()) {
      return DIRECT;
    }
    if (!isReadable(site)) {
      return route(entity) ? CALL : DIRECT;
    }
    return readMode(entity);
  }

  private static boolean isReadable(int site) {
    byte state = SnapshotReads.state(site);
    if (state != SnapshotReads.UNKNOWN) {
      return state == SnapshotReads.READABLE;
    }
    if (Processes.onMain()) {
      SnapshotReads.decideLater(site);
      return false;
    }
    return SnapshotReads.decide(site);
  }

  public static Object readSnapshot(Object entity, Invocation invocation) throws Throwable {
    Object snapshot = ((ProcessEntity) entity).zo$snapshot();
    if (snapshot != null) {
      Object scope = SnapshotReadScope.enter();
      try {
        return invocation.invoke(snapshot);
      } catch (RuntimeException e) {
        if (e != SnapshotReadScope.ABORTED) {
          Processes.LOGGER.debug(
              "Snapshot read of {} failed, asking its owner", Processes.describe(entity), e);
        }
      } finally {
        SnapshotReadScope.exit(scope);
      }
      SnapshotReadScope.refuseMessages();
    }
    return Processes.call(entity, invocation);
  }

  public static Object snapshotOf(Object entity) {
    return ((ProcessEntity) entity).zo$snapshot();
  }

  public static Object call(Object entity, Invocation invocation) {
    SnapshotReadScope.refuseMessages();
    return Processes.call(entity, invocation);
  }

  public static void cast(Object entity, Invocation invocation) {
    SnapshotReadScope.refuseMessages();
    Processes.cast(entity, invocation, true);
  }

  public static void castUntracked(Object entity, Invocation invocation) {
    SnapshotReadScope.refuseMessages();
    Processes.cast(entity, invocation, false);
  }

  public static Object unsnapshot(Object value) {
    if (value instanceof ProcessEntity pe) {
      Object live = pe.zo$live();
      if (live != null) {
        return live;
      }
    }
    return value;
  }

  public static Object worldRandom(Object world) {
    return McBridge.worldRandom(world);
  }
}
