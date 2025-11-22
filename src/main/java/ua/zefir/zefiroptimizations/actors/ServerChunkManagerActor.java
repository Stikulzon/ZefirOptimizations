package ua.zefir.zefiroptimizations.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import ua.zefir.zefiroptimizations.actors.messages.ServerChunkManagerMessages;

public class ServerChunkManagerActor  extends AbstractBehavior<ServerChunkManagerMessages.ServerChunkManagerMessage> {
    public ServerChunkManagerActor(ActorContext<ServerChunkManagerMessages.ServerChunkManagerMessage> context) {
        super(context);
    }

    public static Behavior<ServerChunkManagerMessages.ServerChunkManagerMessage> create() {
        return Behaviors.setup(ServerChunkManagerActor::new);
    }


    @Override
    public Receive<ServerChunkManagerMessages.ServerChunkManagerMessage> createReceive() {
        return newReceiveBuilder()
                .build();
    }
}
