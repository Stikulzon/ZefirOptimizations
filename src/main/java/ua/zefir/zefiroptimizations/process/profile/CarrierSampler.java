package ua.zefir.zefiroptimizations.process.profile;

import java.util.HashMap;
import java.util.Map;

final class CarrierSampler {
  private static final long INTERVAL_MILLIS = 100L;
  private static final String THREAD_NAME = "zefiroptimizations-carrier-sampler";

  record Totals(long cpuNanos, long allocatedBytes) {}

  private final Map<Long, CpuClock.Reading> baseline;
  private final Map<Long, CpuClock.Reading> latest = new HashMap<>();
  private volatile boolean running = true;
  private Thread thread;

  private CarrierSampler(Map<Long, CpuClock.Reading> baseline) {
    this.baseline = baseline;
    latest.putAll(baseline);
  }

  static CarrierSampler start() {
    CarrierSampler sampler = new CarrierSampler(CpuClock.readCarriers());
    sampler.thread = Thread.ofPlatform().daemon().name(THREAD_NAME).start(sampler::run);
    return sampler;
  }

  Totals finish() {
    running = false;
    thread.interrupt();
    sample();
    return totals();
  }

  private void run() {
    while (running) {
      sample();
      try {
        Thread.sleep(INTERVAL_MILLIS);
      } catch (InterruptedException e) {
        return;
      }
    }
  }

  private synchronized void sample() {
    latest.putAll(CpuClock.readCarriers());
  }

  private synchronized Totals totals() {
    long cpu = 0;
    long allocated = 0;
    for (Map.Entry<Long, CpuClock.Reading> entry : latest.entrySet()) {
      CpuClock.Reading now = entry.getValue();
      CpuClock.Reading before = baseline.get(entry.getKey());
      cpu += Math.max(0, now.cpuNanos() - (before == null ? 0 : before.cpuNanos()));
      if (allocated == CpuClock.UNKNOWN || now.allocatedBytes() < 0) {
        allocated = CpuClock.UNKNOWN;
        continue;
      }
      allocated +=
          Math.max(0, now.allocatedBytes() - (before == null ? 0 : before.allocatedBytes()));
    }
    return new Totals(cpu, allocated);
  }
}
