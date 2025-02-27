package ua.zefir.zefiroptimizations.mixin;

import akka.actor.typed.ActorRef;
import net.minecraft.server.world.ServerEntityManager;
import org.spongepowered.asm.mixin.Mixin;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;
import ua.zefir.zefiroptimizations.data.ServerEntityManagerRef;

@Mixin(ServerEntityManager.class)
public class ServerEntityManagerInterfaceMixin implements ServerEntityManagerRef {
    public ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor = null;

    @Override
    public void setEntityManagerActor(ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor) {
        this.entityManagerActor = entityManagerActor;
    }

    @Override
    public ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> getEntityManagerActor() {
        return entityManagerActor;
    }
}
