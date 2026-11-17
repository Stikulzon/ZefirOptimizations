package ua.zefir.zefiroptimizations.process.profile;

import java.util.List;
import java.util.Map;

public record ProfileResult(
    Window window,
    ServerTotals server,
    EntityCost cost,
    Map<ProfileCounters.Counter, Long> counters,
    List<TypeTotals> types,
    List<SlowTick> slowest,
    List<SiteTotals> sites) {
  private static final double HEALTHY_TICK_MILLIS = 25.0;
  private static final double BUSY_TICK_MILLIS = 45.0;
  private static final double HEALTHY_SKIPPED_SHARE = 0.01;
  private static final double BUSY_SKIPPED_SHARE = 0.05;
  private static final double NANOS_PER_MILLI = 1e6;
  private static final double NANOS_PER_SECOND = 1e9;
  private static final double MIN_SECONDS = 1e-9;
  private static final double SETTLED_JIT_SHARE = 0.02;
  private static final double MILLIS_PER_SECOND = 1e3;

  boolean warmingUp() {
    return cost.jitMillis() > window.seconds() * MILLIS_PER_SECOND * SETTLED_JIT_SHARE;
  }

  public enum Verdict {
    HEALTHY,
    BUSY,
    OVERLOADED
  }

  public record Window(
      long nanos, int requestedSeconds, int processes, int carrierThreads, String startedAt) {
    double seconds() {
      return nanos / NANOS_PER_SECOND;
    }
  }

  public record ServerTotals(
      long ticks,
      long tickNanos,
      long tickMaxNanos,
      long waitNanos,
      long waits,
      long taskNanos,
      long tasks) {
    double tps(Window window) {
      return ticks / Math.max(window.seconds(), MIN_SECONDS);
    }

    long tickAvgNanos() {
      return tickNanos / Math.max(1, ticks);
    }
  }

  public record EntityCost(
      long processTicks,
      long serverEntityTicks,
      long skippedTicks,
      long carrierCpuNanos,
      long serverEntityCpuNanos,
      long serverTaskNanos,
      long jvmCpuNanos,
      long serverCpuNanos,
      long allocatedBytes,
      long gcMillis,
      long gcCount,
      long jitMillis) {
    long entityTicks() {
      return processTicks + serverEntityTicks;
    }

    long entityCpuNanos() {
      if (carrierCpuNanos < 0 || serverEntityCpuNanos < 0) {
        return CpuClock.UNKNOWN;
      }
      return carrierCpuNanos + serverEntityCpuNanos + serverTaskNanos;
    }

    long cpuPerEntityTickNanos() {
      long cpu = entityCpuNanos();
      return cpu < 0 || entityTicks() == 0 ? CpuClock.UNKNOWN : cpu / entityTicks();
    }

    long bytesPerEntityTick() {
      return allocatedBytes < 0 || entityTicks() == 0
          ? CpuClock.UNKNOWN
          : allocatedBytes / entityTicks();
    }

    double skippedShare() {
      long due = processTicks + skippedTicks;
      return due == 0 ? 0.0 : (double) skippedTicks / due;
    }
  }

  public record TypeTotals(
      String type,
      int processes,
      long ticks,
      long tickNanos,
      long tickMaxNanos,
      long serveNanos,
      long waitNanos,
      long publishNanos,
      long callsToEntities,
      long callsToMain,
      long served,
      long skipped,
      long timeouts,
      long crashes) {
    long busyNanos() {
      return tickNanos + serveNanos + publishNanos;
    }

    long perTick(long nanos) {
      return nanos / Math.max(1, ticks);
    }

    double perTick(double count) {
      return count / Math.max(1, ticks);
    }
  }

  public record SlowTick(String name, long maxNanos, long avgNanos, long ticks, long waitNanos) {}

  public record SiteTotals(String kind, String site, long count, long waitNanos, long maxNanos) {}

  public Verdict verdict() {
    double tickMillis = server.tickAvgNanos() / NANOS_PER_MILLI;
    double skipped = cost.skippedShare();
    if (tickMillis <= HEALTHY_TICK_MILLIS && skipped <= HEALTHY_SKIPPED_SHARE) {
      return Verdict.HEALTHY;
    }
    if (tickMillis <= BUSY_TICK_MILLIS && skipped <= BUSY_SKIPPED_SHARE) {
      return Verdict.BUSY;
    }
    return Verdict.OVERLOADED;
  }

  long counter(ProfileCounters.Counter counter) {
    return counters.getOrDefault(counter, 0L);
  }
}
