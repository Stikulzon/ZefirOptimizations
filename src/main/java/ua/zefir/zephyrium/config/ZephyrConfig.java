package ua.zefir.zephyrium.config;

public final class ZephyrConfig {
  public static final int AUTO_CARRIER_THREADS = 0;
  public static final int MAX_CARRIER_THREADS = 256;

  public boolean entityProcesses = true;
  public boolean bytecodeTransformer = true;
  public boolean neighbourViews = true;
  public boolean castMemoryWrites = true;
  public boolean reserveServerCpu = false;
  public boolean reserveWholeServerCore = true;
  public boolean sharedStateGuard = true;
  public boolean directFieldGetters = true;
  public boolean splitRoutedMethods = true;
  public boolean batchPacketFlushes = true;
  public boolean skipUnchangedSnapshotData = true;
  public int carrierThreads = AUTO_CARRIER_THREADS;

  public void normalize() {
    carrierThreads = Math.clamp(carrierThreads, AUTO_CARRIER_THREADS, MAX_CARRIER_THREADS);
  }
}
