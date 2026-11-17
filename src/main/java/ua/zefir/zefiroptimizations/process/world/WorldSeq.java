package ua.zefir.zefiroptimizations.process.world;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import ua.zefir.zefiroptimizations.process.EntityProcess;
import ua.zefir.zefiroptimizations.process.Processes;
import ua.zefir.zefiroptimizations.process.message.MainNode;
import ua.zefir.zefiroptimizations.process.message.WaitNode;
import ua.zefir.zefiroptimizations.process.profile.ProfileCounters;
import ua.zefir.zefiroptimizations.process.profile.ProfileCounters.Counter;

public final class WorldSeq {
  private static final long FIRST_PARK_NANOS = TimeUnit.MICROSECONDS.toNanos(20);
  private static final long MAX_PARK_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
  private static final long READ_PATIENCE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
  private static final int SPINS_BEFORE_PARKING = 16;
  private static final int PARK_GROWTH = 2;

  private static volatile long version;

  private static int depth;

  public static void beginWrite() {
    if (depth++ == 0) {
      version = version + 1;
    }
  }

  public static void endWrite() {
    if (--depth == 0) {
      version = version + 1;
    }
  }

  public static void write(Runnable mutation) {
    beginWrite();
    try {
      mutation.run();
    } finally {
      endWrite();
    }
  }

  public static <T> T read(Supplier<T> attempt) {
    long deadline = 0L;
    long park = FIRST_PARK_NANOS;
    ProfileCounters.countCurrent(Counter.WORLD_READS);
    for (int i = 0; ; i++) {
      if (i == 1) {
        ProfileCounters.countCurrent(Counter.WORLD_RETRIES);
      }
      long before = version;
      if ((before & 1L) == 0L) {
        try {
          T result = attempt.get();
          if (version == before) {
            return result;
          }
        } catch (VirtualMachineError e) {
          throw e;
        } catch (Throwable e) {
          if (version == before) {
            return readOnMain(attempt);
          }
        }
      }
      if (i < SPINS_BEFORE_PARKING) {
        Thread.onSpinWait();
        continue;
      }
      long now = System.nanoTime();
      if (deadline == 0L) {
        deadline = now + READ_PATIENCE_NANOS;
      } else if (now - deadline > 0 || writerBlocked()) {
        return readOnMain(attempt);
      }
      while (((version & 1L) != 0L || version == before)
          && System.nanoTime() - deadline < 0
          && !writerBlocked()) {
        LockSupport.parkNanos(park);
        park = Math.min(park * PARK_GROWTH, MAX_PARK_NANOS);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T readOnMain(Supplier<T> attempt) {
    ProfileCounters.countCurrent(Counter.WORLD_FALLBACKS);
    return (T) Processes.callMain(self -> attempt.get(), null);
  }

  private static boolean writerBlocked() {
    if ((version & 1L) == 0L) {
      return false;
    }
    WaitNode on = MainNode.INSTANCE.waitingOn;
    EntityProcess self = EntityProcess.current();
    return on != null && (self == null || WaitNode.reaches(on, self));
  }
}
