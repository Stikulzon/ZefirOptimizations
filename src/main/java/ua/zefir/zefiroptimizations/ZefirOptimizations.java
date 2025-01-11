package ua.zefir.zefiroptimizations;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.zefir.zefiroptimizations.actors.AsyncTickManagerActor;
import ua.zefir.zefiroptimizations.actors.MainThreadActor;

public class ZefirOptimizations implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("zefiroptimizations");
	public static MinecraftServer SERVER;
	@Getter
	private static ActorSystem actorSystem;
	@Getter
	private static ActorRef asyncTickManager;
	@Getter
	private static ActorRef mainThreadActor;
	public static boolean firstTimeIterating = true;

	@Override
	public void onInitialize() {
		actorSystem = ActorSystem.create("ZefirOptimizationsActorSystem");
		asyncTickManager = actorSystem.actorOf(AsyncTickManagerActor.props(), "asyncTickManager");
		mainThreadActor = actorSystem.actorOf(Props.create(MainThreadActor.class), "mainThreadActor");

		ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
	}

	private void onServerStarting(MinecraftServer server) {
		SERVER = server;
	}

	private void onServerStarted(MinecraftServer server) {
	}
}