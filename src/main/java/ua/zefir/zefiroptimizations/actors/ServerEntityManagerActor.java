package ua.zefir.zefiroptimizations.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerEntityManager;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;

public class ServerEntityManagerActor extends AbstractBehavior<ServerEntityManagerMessages.ServerEntityManagerMessage> {
    private final ServerEntityManager<Entity> entityManager;

    public static Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> create(ServerEntityManager<Entity> entityManager) {
        return Behaviors.setup(context -> new ServerEntityManagerActor(context, entityManager));
    }

    public ServerEntityManagerActor(ActorContext<ServerEntityManagerMessages.ServerEntityManagerMessage> context, ServerEntityManager<Entity> entityManager) {
        super(context);
        this.entityManager = entityManager;
    }

    @Override
    public Receive<ServerEntityManagerMessages.ServerEntityManagerMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(ServerEntityManagerMessages.Tick.class, (msg) -> {this.entityManager.tick(); return this;})
                .onMessage(ServerEntityManagerMessages.LoadEntities.class, (msg) -> {this.entityManager.loadEntities(msg.entities()); return this;})
                .onMessage(ServerEntityManagerMessages.AddEntities.class, (msg) -> {this.entityManager.addEntities(msg.entities()); return this;})
                .onMessage(ServerEntityManagerMessages.UpdateTrackingStatusChunkLevelType.class, (msg) -> {this.entityManager.updateTrackingStatus(msg.chunkPos(), msg.levelType()); return this;})
                .onMessage(ServerEntityManagerMessages.UpdateTrackingStatus.class, (msg) -> {this.entityManager.updateTrackingStatus(msg.chunkPos(), msg.trackingStatus()); return this;})
                .onMessage(ServerEntityManagerMessages.Save.class, (msg) -> {this.entityManager.save(); return this;})
                .onMessage(ServerEntityManagerMessages.Flush.class, (msg) -> {this.entityManager.flush(); return this;})
                .onMessage(ServerEntityManagerMessages.Close.class, (msg) -> {this.entityManager.close(); return this;})
                .onMessage(ServerEntityManagerMessages.Dump.class, (msg) -> {this.entityManager.dump(msg.writer()); return this;})

                .onMessage(ServerEntityManagerMessages.RequestAddEntity.class, this::requestAddEntity)
                .onMessage(ServerEntityManagerMessages.RequestHas.class, this::requestHas)
                .onMessage(ServerEntityManagerMessages.RequestShouldTickBlockPos.class, this::requestShouldTickBlockPos)
                .onMessage(ServerEntityManagerMessages.RequestShouldTickChunkPos.class, this::requestShouldTickChunkPos)
                .onMessage(ServerEntityManagerMessages.RequestIsLoaded.class, this::requestIsLoaded)
                .onMessage(ServerEntityManagerMessages.RequestDebugString.class, this::requestDebugString)
                .onMessage(ServerEntityManagerMessages.RequestIndexSize.class, this::requestIndexSize)

                // Lookup operations
                .onMessage(ServerEntityManagerMessages.RequestEntityLookupByUuid.class, this::requestEntityLookupByUuid)
                .onMessage(ServerEntityManagerMessages.RequestEntityLookupById.class, this::requestEntityLookupById)
                .onMessage(ServerEntityManagerMessages.RequestEntityLookupIterable.class, this::requestEntityLookupIterable)
                .onMessage(ServerEntityManagerMessages.EntityLookupForEachIntersects.class, (msg) -> {this.entityManager.getLookup().forEachIntersects(msg.box(), msg.action()); return this;})
                .onMessage(ServerEntityManagerMessages.EntityLookupForEachIntersectsTypeFilter.class, (msg) -> {this.entityManager.getLookup().forEachIntersects(msg.filter(), msg.box(), msg.consumer()); return this;})
                .onMessage(ServerEntityManagerMessages.EntityLookupForEach.class, (msg) -> {this.entityManager.getLookup().forEach(msg.filter(), msg.consumer()); return this;})

                .build();
    }


    public Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestAddEntity(ServerEntityManagerMessages.RequestAddEntity msg) {
        boolean result = this.entityManager.addEntity(msg.entity());
        msg.replyTo().tell(result);
        return this;
    }

    public Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestHas(ServerEntityManagerMessages.RequestHas msg) {
        boolean result = this.entityManager.has(msg.uuid());
        msg.replyTo().tell(result);
        return this;
    }

    public Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestShouldTickBlockPos(ServerEntityManagerMessages.RequestShouldTickBlockPos msg) {
        boolean result = this.entityManager.shouldTick(msg.pos());
        msg.replyTo().tell(result);
        return this;
    }

    public Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestShouldTickChunkPos(ServerEntityManagerMessages.RequestShouldTickChunkPos msg) {
        boolean result = this.entityManager.shouldTick(msg.pos());
        msg.replyTo().tell(result);
        return this;
    }

    public Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestIsLoaded(ServerEntityManagerMessages.RequestIsLoaded msg) {
        boolean result = this.entityManager.isLoaded(msg.chunkPos());
        msg.replyTo().tell(result);
        return this;
    }

    public Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestDebugString(ServerEntityManagerMessages.RequestDebugString msg) {
        String debugString = this.entityManager.getDebugString();
        msg.replyTo().tell(new ServerEntityManagerMessages.ResponseDebugString(debugString));
        return this;
    }

    public Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestIndexSize(ServerEntityManagerMessages.RequestIndexSize msg) {
        int indexSize = this.entityManager.getIndexSize();
        msg.replyTo().tell(new ServerEntityManagerMessages.ResponseIndexSize(indexSize));
        return this;
    }

    // Lookup operations

    public Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestEntityLookupByUuid(ServerEntityManagerMessages.RequestEntityLookupByUuid msg) {
        Entity entity = this.entityManager.getLookup().get(msg.uuid());
        msg.replyTo().tell(new ServerEntityManagerMessages.ResponseEntityLookupEntity(entity));
        return this;
    }

    public Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestEntityLookupById(ServerEntityManagerMessages.RequestEntityLookupById msg) {
        Entity entity = this.entityManager.getLookup().get(msg.id());
        msg.replyTo().tell(new ServerEntityManagerMessages.ResponseEntityLookupEntity(entity));
        return this;
    }

    public Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestEntityLookupIterable(ServerEntityManagerMessages.RequestEntityLookupIterable msg) {
        Iterable<Entity> iterable = this.entityManager.getLookup().iterate();
        msg.replyTo().tell(new ServerEntityManagerMessages.ResponseEntityLookupIterable<>(iterable));
        return this;
    }
}