package ua.zefir.zefiroptimizations.process.message;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import ua.zefir.zefiroptimizations.process.EntityProcess;

public final class PendingCasts {
  private static final int SWEEP_INTERVAL = 1024;

  private static final ThreadLocal<PendingCasts> CURRENT =
      ThreadLocal.withInitial(PendingCasts::new);

  private final Map<Object, AtomicInteger> pending = new IdentityHashMap<>();
  private int opsSinceSweep;

  static PendingCasts current() {
    return of(EntityProcess.current());
  }

  public static PendingCasts of(EntityProcess process) {
    return process != null ? process.pendingCasts : CURRENT.get();
  }

  public boolean has(Object target) {
    if (pending.isEmpty()) {
      return false;
    }
    AtomicInteger counter = pending.get(target);
    if (counter == null) {
      return false;
    }
    if (counter.get() <= 0) {
      pending.remove(target);
      return false;
    }
    return true;
  }

  AtomicInteger increment(Object target) {
    AtomicInteger counter = pending.computeIfAbsent(target, t -> new AtomicInteger());
    counter.incrementAndGet();
    if (++opsSinceSweep >= SWEEP_INTERVAL) {
      opsSinceSweep = 0;
      sweep();
    }
    return counter;
  }

  private void sweep() {
    pending.values().removeIf(counter -> counter.get() <= 0);
  }
}
