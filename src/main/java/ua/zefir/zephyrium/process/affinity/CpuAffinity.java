package ua.zefir.zephyrium.process.affinity;

import java.util.BitSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import ua.zefir.zephyrium.config.ConfigService;
import ua.zefir.zephyrium.config.ZephyrConfig;
import ua.zefir.zephyrium.process.Processes;

public final class CpuAffinity {
  public static final int CARRIER_CHECK_INTERVAL = 64;
  static final Logger LOGGER = Processes.LOGGER;
  private static final long RESTORE_SECONDS = 10L;
  private static final long RESTORE_NANOS = TimeUnit.SECONDS.toNanos(RESTORE_SECONDS);

  private static volatile MinecraftServer server;
  private static volatile BitSet carrierCpus;
  private static volatile boolean reserved;
  private static volatile long releasedAt;
  private static volatile boolean carrierFailureLogged;
  private static BitSet processCpus;
  private static int requests;
  private static Optional<NativeAffinity> nativeAffinity;

  public static void start(MinecraftServer started) {
    server = started;
    configChanged();
  }

  public static void stop() {
    requests++;
    release();
    server = null;
  }

  public static void configChanged() {
    MinecraftServer current = server;
    if (current == null) {
      return;
    }
    if (!current.isSameThread()) {
      current.execute(CpuAffinity::configChanged);
      return;
    }
    int request = ++requests;
    ZephyrConfig config = ConfigService.get();
    if (!config.reserveServerCpu) {
      release();
      return;
    }
    NativeAffinity affinity = nativeAffinity().orElse(null);
    if (affinity == null) {
      return;
    }
    int cpu = affinity.currentCpu();
    boolean wholeCore = config.reserveWholeServerCore;
    CompletableFuture.supplyAsync(
            () -> wholeCore ? CoreTopology.siblingsOf(cpu) : single(cpu), Util.ioPool())
        .whenCompleteAsync(
            (cpus, error) -> {
              if (request != requests) {
                return;
              }
              if (error != null) {
                LOGGER.error("Could not find the CPUs of the server thread's core", error);
                return;
              }
              reserve(affinity, cpu, cpus);
            },
            current);
  }

  private static BitSet single(int cpu) {
    BitSet cpus = new BitSet();
    cpus.set(cpu);
    return cpus;
  }

  private static void reserve(NativeAffinity affinity, int cpu, BitSet cpus) {
    BitSet allowed = affinity.processCpus();
    BitSet forServer = (BitSet) cpus.clone();
    forServer.and(allowed);
    BitSet forCarriers = (BitSet) allowed.clone();
    forCarriers.andNot(forServer);
    if (forServer.isEmpty() || forCarriers.isEmpty()) {
      LOGGER.warn(
          "Cannot reserve CPU {} for the server thread: this process may only use CPUs {}",
          cpu,
          allowed);
      return;
    }
    if (!affinity.pinCurrentThread(forServer)) {
      LOGGER.error("Could not pin the server thread to CPUs {}", forServer);
      return;
    }
    processCpus = allowed;
    carrierCpus = forCarriers;
    reserved = true;
    LOGGER.info(
        "Reserved CPUs {} for the server thread; entity processes run on CPUs {}",
        forServer,
        forCarriers);
  }

  private static void release() {
    if (!reserved) {
      return;
    }
    reserved = false;
    releasedAt = System.nanoTime();
    carrierCpus = processCpus;
    NativeAffinity affinity = nativeAffinity().orElse(null);
    if (affinity != null && !affinity.pinCurrentThread(processCpus)) {
      LOGGER.error("Could not let the server thread run on CPUs {} again", processCpus);
    }
    LOGGER.info("Released the CPUs reserved for the server thread");
  }

  public static void pinCarrier() {
    BitSet cpus = carrierCpus;
    if (cpus == null) {
      return;
    }
    if (!reserved && System.nanoTime() - releasedAt > RESTORE_NANOS) {
      carrierCpus = null;
      return;
    }
    NativeAffinity affinity = nativeAffinity.isEmpty() ? null : nativeAffinity.orElse(null);
    if (affinity == null) {
      return;
    }
    try {
      if (!affinity.pinCurrentThread(cpus) && !carrierFailureLogged) {
        carrierFailureLogged = true;
        LOGGER.error("Could not pin an entity process carrier to CPUs {}", cpus);
      }
    } catch (RuntimeException | LinkageError e) {
      if (!carrierFailureLogged) {
        carrierFailureLogged = true;
        LOGGER.error("Could not pin an entity process carrier to CPUs {}", cpus, e);
      }
    }
  }

  private static Optional<NativeAffinity> nativeAffinity() {
    if (nativeAffinity.isPresent()) {
      return nativeAffinity;
    }
    try {
      nativeAffinity = NativeAffinity.load();
      if (nativeAffinity.isEmpty()) {
        LOGGER.warn("reserveServerCpu is not supported on this system");
      }
    } catch (RuntimeException | LinkageError e) {
      LOGGER.error("Could not load the native CPU affinity functions", e);
      nativeAffinity = Optional.empty();
    }
    return nativeAffinity;
  }
}
