package ua.zefir.zefiroptimizations;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.zefir.zefiroptimizations.actors.AsyncTickManagerActor;
import ua.zefir.zefiroptimizations.actors.EntityActorMessages;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ZefirOptimizations implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("zefiroptimizations");
	public static MinecraftServer SERVER;
	@Getter
	private static ActorSystem actorSystem;
	@Getter
	private static ActorRef asyncTickManager;

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");

		actorSystem = ActorSystem.create("MinecraftActorSystem");
		asyncTickManager = actorSystem.actorOf(AsyncTickManagerActor.props(), "asyncTickManager");

		ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);

		Commands.registerCommands();
	}

	private void onServerStarting(MinecraftServer server) {
		SERVER = server;
	}
	private void onServerStarted(MinecraftServer server) {

		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleAtFixedRate(() -> {
			if (SERVER != null) {
				asyncTickManager.tell(new EntityActorMessages.AsyncTick(), ActorRef.noSender());
			}
		}, 0, 50, TimeUnit.MILLISECONDS); // 20 ticks per second
	}
}