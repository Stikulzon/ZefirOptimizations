package ua.zefir.zefiroptimizations.data;

import akka.actor.typed.ActorRef;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;

public interface ServerEntityManagerRef {
    void setEntityManagerActor(ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor);
    ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> getEntityManagerActor();
}
