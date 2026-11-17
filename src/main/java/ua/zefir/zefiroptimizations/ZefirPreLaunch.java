package ua.zefir.zefiroptimizations;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import ua.zefir.zefiroptimizations.config.ConfigService;
import ua.zefir.zefiroptimizations.config.ZefirConfig;
import ua.zefir.zefiroptimizations.process.ownership.Ownership;
import ua.zefir.zefiroptimizations.process.sharedstate.SharedState;
import ua.zefir.zefiroptimizations.transform.Instrumenter;
import ua.zefir.zefiroptimizations.transform.TransformerInstaller;

public final class ZefirPreLaunch implements PreLaunchEntrypoint {
  private static final String PARALLELISM_PROPERTY = "jdk.virtualThreadScheduler.parallelism";
  private static final int MIN_CARRIERS = 1;
  private static final int CPUS_PER_CARRIER = 2;
  private static final int SERVER_THREAD_SHARE = 1;

  @Override
  public void onPreLaunch() {
    ConfigService.load();
    limitCarriers();
    ClassLoader loader = ZefirPreLaunch.class.getClassLoader();
    List<Instrumenter> instrumenters = new ArrayList<>();
    Ownership.instrumenter(loader)
        .ifPresent(
            ownership -> {
              SharedState.instrumenter(loader).ifPresent(instrumenters::add);
              instrumenters.add(ownership);
            });
    TransformerInstaller.install(instrumenters);
  }

  private static void limitCarriers() {
    if (System.getProperty(PARALLELISM_PROPERTY) != null) {
      return;
    }
    int configured = ConfigService.get().carrierThreads;
    if (configured != ZefirConfig.AUTO_CARRIER_THREADS) {
      System.setProperty(PARALLELISM_PROPERTY, Integer.toString(configured));
      return;
    }
    int cpus = Runtime.getRuntime().availableProcessors();
    int carriers =
        Math.max(MIN_CARRIERS, Math.min(cpus, cpus / CPUS_PER_CARRIER + SERVER_THREAD_SHARE));
    System.setProperty(PARALLELISM_PROPERTY, Integer.toString(carriers));
  }
}
