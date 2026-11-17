package ua.zefir.zefiroptimizations.process.profile;

import ua.zefir.zefiroptimizations.process.EntityProcess;

public final class ProcessStats {
  public static final int TICK = 0;
  public static final int SERVE = 1;
  public static final int WAIT = 2;
  public static final int PUBLISH = 3;
  private static final int ACTIVITIES = PUBLISH + 1;
  private static final int MAX_DEPTH = 64;

  final EntityProcess process;
  public final String type;
  public final String name;

  private long session = -1;
  private final int[] stack = new int[MAX_DEPTH];
  private int depth;
  private long lastSwitch;

  final long[] nanos = new long[ACTIVITIES];
  final long[] counts = new long[ProfileCounters.COUNTERS];

  public long ticks;

  public long tickMaxNanos;

  public long callsToEntities;
  public long callsToMain;
  public long callsServed;
  public long castsServed;
  public long timeouts;
  public long crashes;
  private long skippedAtStart;

  public ProcessStats(EntityProcess process, String type, String name) {
    this.process = process;
    this.type = type;
    this.name = name;
  }

  public static ProcessStats of(EntityProcess process) {
    return Profiler.isActive() ? process.stats.current() : null;
  }

  private ProcessStats current() {
    long active = Profiler.session();
    if (session != active) {
      session = active;
      java.util.Arrays.fill(nanos, 0);
      java.util.Arrays.fill(counts, 0);
      depth = 0;
      ticks = tickMaxNanos = 0;
      callsToEntities = callsToMain = callsServed = castsServed = timeouts = crashes = 0;
      skippedAtStart = process.skippedTicks();
      Profiler.register(this);
    }
    return this;
  }

  public long enter(int activity) {
    long now = System.nanoTime();
    if (depth > 0) {
      nanos[stack[depth - 1]] += now - lastSwitch;
    }
    if (depth < MAX_DEPTH) {
      stack[depth++] = activity;
    }
    lastSwitch = now;
    return now;
  }

  public long exit() {
    long now = System.nanoTime();
    if (depth > 0) {
      nanos[stack[--depth]] += now - lastSwitch;
    }
    lastSwitch = now;
    return now;
  }

  long skippedTicks() {
    return process.skippedTicks() - skippedAtStart;
  }
}
