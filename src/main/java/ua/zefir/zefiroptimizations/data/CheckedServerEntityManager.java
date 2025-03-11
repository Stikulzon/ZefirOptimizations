package ua.zefir.zefiroptimizations.data;

import akka.actor.typed.ActorRef;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.EntityHandler;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.world.storage.ChunkDataAccess;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;

public class CheckedServerEntityManager<T extends EntityLike> extends ServerEntityManager<T> {
    private ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor;

    public CheckedServerEntityManager(Class<T> entityClass, EntityHandler<T> handler, ChunkDataAccess<T> dataAccess) {
        super(entityClass, handler, dataAccess);
    }

    public EntityLookup<T> getLookup(ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef) {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        return super.getLookup();
    }
}
