package ua.zefir.zefiroptimizations;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.ericsson.otp.erlang.*;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.zefir.zefiroptimizations.threading.MovementPredictionActor;
import ua.zefir.zefiroptimizations.threading.WorldSnapshotActor;

import static ua.zefir.zefiroptimizations.Commands.registerCommands;

public class ZefirOptimizations implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("zefiroptimizations");
	@Getter
    private static OtpConnection connection;
	public static ActorSystem ACTOR_SYSTEM;
	public static ActorRef WORLD_SNAPSHOT_ACTOR;
	public static ActorRef MOVEMENT_PREDICTION_ACTOR;

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");
		registerCommands();

		ACTOR_SYSTEM = ActorSystem.create("MinecraftCollisionSystem");

		// Create the actors (you can do this here or in the mixin)
		WORLD_SNAPSHOT_ACTOR = ACTOR_SYSTEM.actorOf(Props.create(WorldSnapshotActor.class));
		MOVEMENT_PREDICTION_ACTOR = ACTOR_SYSTEM.actorOf(Props.create(MovementPredictionActor.class));

	}

}