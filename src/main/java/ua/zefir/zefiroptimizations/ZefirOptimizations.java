package ua.zefir.zefiroptimizations;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.zefir.zefiroptimizations.actors.ActorSystemManager;
import ua.zefir.zefiroptimizations.actors.MainThreadActor;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;

import java.time.Duration;

public class ZefirOptimizations implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("zefiroptimizations");
	public static MinecraftServer SERVER;
	@Getter
	private static ActorSystem<ZefirsActorMessages.ActorSystemManagerMessage> actorSystem;
	@Getter
	private static ActorRef<ZefirsActorMessages.MainThreadMessage> mainThreadActor;
	public static Duration timeout = Duration.ofSeconds(10);

	@Override
	public void onInitialize() {
		actorSystem = ActorSystem.create(Behaviors.setup(context -> {
			mainThreadActor = context.spawn(MainThreadActor.create(), "mainThreadActor");
			return ActorSystemManager.create();
		}), "ZefirOptimizationsActorSystem");

		ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
	}

	public static void shutdown() {
		if (actorSystem != null) {
			actorSystem.terminate();
		}
	}

	private void onServerStarting(MinecraftServer server) {
		SERVER = server;
	}

	private void onServerStarted(MinecraftServer server) {
	}
}