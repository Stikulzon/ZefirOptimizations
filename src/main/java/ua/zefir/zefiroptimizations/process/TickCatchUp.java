package ua.zefir.zefiroptimizations.process;

import java.util.concurrent.TimeUnit;

public final class TickCatchUp {
  private static final long MAX_LAG_NANOS = TimeUnit.SECONDS.toNanos(2);

  public static long nextTickTime(long now, long scheduled) {
    if (now - scheduled > MAX_LAG_NANOS) {
      return now;
    }
    return scheduled;
  }
}
