package ua.zefir.zephyrium;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.zefir.zephyrium.command.ZephyrCommand;
import ua.zefir.zephyrium.config.ConfigService;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.affinity.CpuAffinity;
import ua.zefir.zephyrium.process.profile.ProfileCommand;
import ua.zefir.zephyrium.process.profile.Profiler;

public final class ZephyrModInit implements ModInitializer {
  public static final String MOD_ID = "zephyrium";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  @Override
  public void onInitialize() {
    ServerLifecycleEvents.SERVER_STARTING.register(Processes::start);
    ServerLifecycleEvents.SERVER_STARTED.register(CpuAffinity::start);
    ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> {
          CpuAffinity.stop();
          Processes.stop();
        });
    ConfigService.onReload(CpuAffinity::configChanged);

    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registries, environment) ->
            ZephyrCommand.register(dispatcher, registries, ProfileCommand.class));
    ServerTickEvents.START_SERVER_TICK.register(
        server -> {
          Processes.onServerTickStart();
          Profiler.onServerTickStart();
        });
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          Processes.onServerTickEnd();
          Profiler.onServerTickEnd();
          ProfileCommand.onServerTick(server);
        });
  }
}
