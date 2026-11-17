package ua.zefir.zephyrium.process.profile;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import ua.zefir.zephyrium.process.EntityProcess;

public final class ProfileCounters {
  public enum Counter {
    MESSAGES,
    CALLS,
    CASTS,
    EMPTY_MESSAGES,
    WAKES,
    PUBLISHES,
    WORLD_READS,
    WORLD_RETRIES,
    WORLD_FALLBACKS
  }

  static final int COUNTERS = Counter.values().length;

  private ProfileCounters() {}

  public static void count(EntityProcess process, Counter counter) {
    if (process == null) {
      return;
    }
    ProcessStats stats = ProcessStats.of(process);
    if (stats != null) {
      stats.counts[counter.ordinal()]++;
    }
  }

  public static void countCurrent(Counter counter) {
    if (!Profiler.isActive()) {
      return;
    }
    count(EntityProcess.current(), counter);
  }

  static Map<Counter, Long> sum(List<ProcessStats> stats) {
    Map<Counter, Long> totals = new EnumMap<>(Counter.class);
    for (Counter counter : Counter.values()) {
      long total = 0;
      for (ProcessStats s : stats) {
        total += s.counts[counter.ordinal()];
      }
      totals.put(counter, total);
    }
    return totals;
  }
}
