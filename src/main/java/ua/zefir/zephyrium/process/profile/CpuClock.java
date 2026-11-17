package ua.zefir.zephyrium.process.profile;

import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ua.zefir.zephyrium.process.Processes;

final class CpuClock {
  static final long UNKNOWN = -1L;
  private static final String CARRIER_PREFIX = "ForkJoinPool-";
  private static final String CARRIER_INFIX = "-worker-";
  private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
  private static final OperatingSystemMXBean OS = ManagementFactory.getOperatingSystemMXBean();
  private static final CompilationMXBean COMPILER = ManagementFactory.getCompilationMXBean();
  private static final List<GarbageCollectorMXBean> COLLECTORS =
      ManagementFactory.getGarbageCollectorMXBeans();

  record Reading(long cpuNanos, long allocatedBytes) {}

  final long processNanos;
  final long serverNanos;
  final long gcMillis;
  final long gcCount;
  final long jitMillis;

  private CpuClock(
      long processNanos, long serverNanos, long gcMillis, long gcCount, long jitMillis) {
    this.processNanos = processNanos;
    this.serverNanos = serverNanos;
    this.gcMillis = gcMillis;
    this.gcCount = gcCount;
    this.jitMillis = jitMillis;
  }

  static CpuClock read(Thread server) {
    long serverNanos = UNKNOWN;
    try {
      if (cpuTimeAvailable() && server != null) {
        serverNanos = THREADS.getThreadCpuTime(server.threadId());
      }
    } catch (RuntimeException e) {
      Processes.LOGGER.error("Could not read the server thread's CPU time for the profile", e);
    }
    return new CpuClock(processNanos(), serverNanos, gcMillis(), gcCount(), jitMillis());
  }

  static long currentThreadCpuNanos() {
    return cpuTimeAvailable() ? THREADS.getCurrentThreadCpuTime() : UNKNOWN;
  }

  static long currentThreadAllocatedBytes() {
    if (THREADS instanceof com.sun.management.ThreadMXBean threads
        && threads.isThreadAllocatedMemoryEnabled()) {
      return threads.getCurrentThreadAllocatedBytes();
    }
    return UNKNOWN;
  }

  private static boolean cpuTimeAvailable() {
    return THREADS.isThreadCpuTimeSupported() && THREADS.isThreadCpuTimeEnabled();
  }

  static Map<Long, Reading> readCarriers() {
    Map<Long, Reading> carriers = new HashMap<>();
    if (!cpuTimeAvailable()) {
      return carriers;
    }
    try {
      for (ThreadInfo info : THREADS.getThreadInfo(THREADS.getAllThreadIds(), 0)) {
        if (info == null || !isCarrier(info.getThreadName())) {
          continue;
        }
        long id = info.getThreadId();
        long nanos = THREADS.getThreadCpuTime(id);
        if (nanos >= 0) {
          carriers.put(id, new Reading(nanos, allocatedBytes(id)));
        }
      }
    } catch (RuntimeException e) {
      Processes.LOGGER.error("Could not read carrier CPU times for the profile", e);
    }
    return carriers;
  }

  private static long allocatedBytes(long threadId) {
    if (THREADS instanceof com.sun.management.ThreadMXBean threads
        && threads.isThreadAllocatedMemoryEnabled()) {
      return threads.getThreadAllocatedBytes(threadId);
    }
    return UNKNOWN;
  }

  private static boolean isCarrier(String name) {
    return name != null && name.startsWith(CARRIER_PREFIX) && name.contains(CARRIER_INFIX);
  }

  private static long processNanos() {
    if (OS instanceof com.sun.management.OperatingSystemMXBean os) {
      return os.getProcessCpuTime();
    }
    return UNKNOWN;
  }

  private static long jitMillis() {
    if (COMPILER == null || !COMPILER.isCompilationTimeMonitoringSupported()) {
      return UNKNOWN;
    }
    return COMPILER.getTotalCompilationTime();
  }

  private static long gcMillis() {
    long total = 0;
    for (GarbageCollectorMXBean collector : COLLECTORS) {
      total += Math.max(0, collector.getCollectionTime());
    }
    return total;
  }

  private static long gcCount() {
    long total = 0;
    for (GarbageCollectorMXBean collector : COLLECTORS) {
      total += Math.max(0, collector.getCollectionCount());
    }
    return total;
  }

  static long since(long start, long end) {
    return start < 0 || end < 0 ? UNKNOWN : Math.max(0, end - start);
  }
}
