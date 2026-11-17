package ua.zefir.zefiroptimizations.process.snapshot;

import ua.zefir.zefiroptimizations.process.EntityProcess;
import ua.zefir.zefiroptimizations.process.Processes;

public final class SnapshotReadScope {
  public static final RuntimeException ABORTED = new Aborted();

  private static final ThreadLocal<int[]> OTHER_THREADS = ThreadLocal.withInitial(() -> new int[1]);

  private static int mainDepth;

  public static Object enter() {
    EntityProcess process = EntityProcess.current();
    if (process != null) {
      process.snapshotReads++;
      return process;
    }
    if (Processes.onMain()) {
      mainDepth++;
      return null;
    }
    int[] depth = OTHER_THREADS.get();
    depth[0]++;
    return depth;
  }

  public static void exit(Object scope) {
    if (scope instanceof EntityProcess process) {
      process.snapshotReads--;
    } else if (scope instanceof int[] depth) {
      depth[0]--;
    } else {
      mainDepth--;
    }
  }

  static boolean active() {
    EntityProcess process = EntityProcess.current();
    if (process != null) {
      return process.snapshotReads > 0;
    }
    if (Processes.onMain()) {
      return mainDepth > 0;
    }
    return OTHER_THREADS.get()[0] > 0;
  }

  public static void refuseMessages() {
    if (active()) {
      throw ABORTED;
    }
  }

  private static final class Aborted extends RuntimeException {
    private Aborted() {
      super("a snapshot read tried to send a message", null, false, false);
    }
  }
}
