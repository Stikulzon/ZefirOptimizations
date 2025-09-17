package ua.zefir.zefiroptimizations.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.google.common.collect.Lists;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.util.function.LazyIterationConsumer;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;
import ua.zefir.zefiroptimizations.data.DummyPredicate;
import ua.zefir.zefiroptimizations.mixin.ServerEntityManagerAccessor;

import java.util.List;
import java.util.function.Predicate;

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
                .onMessage(ServerEntityManagerMessages.EntityLookupForEachIntersects.class, (msg) -> {this.entityManager.getLookup().forEachIntersects(msg.box(), msg.action()); return this;})
                .onMessage(ServerEntityManagerMessages.EntityLookupForEachIntersectsTypeFilter.class, (msg) -> {this.entityManager.getLookup().forEachIntersects(msg.filter(), msg.box(), msg.consumer()); return this;})
                .onMessage(ServerEntityManagerMessages.EntityLookupForEach.class, (msg) -> {this.entityManager.getLookup().forEach(msg.filter(), msg.consumer()); return this;})
                .onMessage(ServerEntityManagerMessages.RequestEntityLookupByUuid.class, this::requestEntityLookupByUuid)
                .onMessage(ServerEntityManagerMessages.RequestEntityLookupById.class, this::requestEntityLookupById)
                .onMessage(ServerEntityManagerMessages.RequestEntityLookupIterable.class, this::requestEntityLookupIterable)
                .onMessage(ServerEntityManagerMessages.RequestOtherEntities.class, this::requestOtherEntities)
                .onMessage(ServerEntityManagerMessages.RequestEntitiesByTypeWorld.class, this::requestEntitiesByTypeWorld)
                .onMessage(ServerEntityManagerMessages.RequestEntitiesByTypeServerWorld.class, this::requestEntitiesByTypeServerWorld)

                // Listener
                .onMessage(ServerEntityManagerMessages.EntityLeftSection.class, this::entityLeftSection)
                .onMessage(ServerEntityManagerMessages.StartTicking.class, this::startTicking)
                .onMessage(ServerEntityManagerMessages.StopTicking.class, this::stopTicking)
                .onMessage(ServerEntityManagerMessages.StartTracking.class, this::startTracking)
                .onMessage(ServerEntityManagerMessages.StopTracking.class, this::stopTracking)
                .onMessage(ServerEntityManagerMessages.RequestCacheTrackingSection.class, this::cacheGetTrackingSection)
                .onMessage(ServerEntityManagerMessages.RequestCacheTrackingSection.class, this::cacheGetTrackingSection)
                .onMessage(ServerEntityManagerMessages.EntityUuidsRemove.class, this::entityUuidsRemove)
                .onMessage(ServerEntityManagerMessages.HandlerDestroy.class, this::handlerDestroy)
                .onMessage(ServerEntityManagerMessages.HandlerUpdateLoadStatus.class, this::handlerUpdateLoadStatus)

                .build();
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> handlerUpdateLoadStatus(ServerEntityManagerMessages.HandlerUpdateLoadStatus msg) {
        entityManager.handler.updateLoadStatus((Entity) msg.entity());
        return this;
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> handlerDestroy(ServerEntityManagerMessages.HandlerDestroy msg) {
        entityManager.handler.destroy((Entity) msg.entity());
        return this;
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> entityUuidsRemove(ServerEntityManagerMessages.EntityUuidsRemove msg) {
        ((ServerEntityManagerAccessor)this.entityManager).getEntityUuids().remove(msg.uuid());
        return this;
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> cacheGetTrackingSection(ServerEntityManagerMessages.RequestCacheTrackingSection msg) {
        var result = ((ServerEntityManagerAccessor)this.entityManager).getCache().getTrackingSection(msg.sectionPos());
        msg.replyTo().tell(result);
        return this;
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> stopTracking(ServerEntityManagerMessages.StopTracking msg) {
        ((ServerEntityManagerAccessor)this.entityManager).invokeStopTracking(msg.entity());
        return this;
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> startTracking(ServerEntityManagerMessages.StartTracking msg) {
        ((ServerEntityManagerAccessor)this.entityManager).invokeStartTracking(msg.entity());
        return this;
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> stopTicking(ServerEntityManagerMessages.StopTicking msg) {
        ((ServerEntityManagerAccessor)this.entityManager).invokeStopTicking(msg.entity());
        return this;
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> startTicking(ServerEntityManagerMessages.StartTicking msg) {
        ((ServerEntityManagerAccessor)this.entityManager).invokeStartTicking(msg.entity());
        return this;
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> entityLeftSection(ServerEntityManagerMessages.EntityLeftSection msg) {
        ((ServerEntityManagerAccessor)this.entityManager).invokeEntityLeftSection(msg.sectionPos(), msg.section());
        return this;
    }

    // I need to find a better approach than just copying original code
    private <T extends Entity> Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestEntitiesByTypeServerWorld(ServerEntityManagerMessages.RequestEntitiesByTypeServerWorld msg) {
        List<? super T> result = Lists.newArrayList();

        this.entityManager.getLookup().forEach(msg.filter(), entity -> {
            if (msg.predicate().test(entity)) {
                result.add((T) entity);
                if (result.size() >= msg.limit()) {
                    return LazyIterationConsumer.NextIteration.ABORT;
                }
            }

            return LazyIterationConsumer.NextIteration.CONTINUE;
        });
        msg.replyTo().tell(result);
        return this;
    }

    private <T extends Entity> Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestEntitiesByTypeWorld(ServerEntityManagerMessages.RequestEntitiesByTypeWorld msg) {
        List<? super T> result = Lists.newArrayList();
        this.entityManager.getLookup().forEachIntersects(msg.filter(), msg.box(), entity -> {
            if (msg.predicate().test(entity)) {
                result.add((T) entity);
                if (result.size() >= msg.limit()) {
                    return LazyIterationConsumer.NextIteration.ABORT;
                }
            }

            if (entity instanceof EnderDragonEntity enderDragonEntity) {
                for (EnderDragonPart enderDragonPart : enderDragonEntity.getBodyParts()) {
                    T entity2 = (T) msg.filter().downcast(enderDragonPart);
                    if (entity2 != null && msg.predicate().test(entity2)) {
                        result.add(entity2);
                        if (result.size() >= msg.limit()) {
                            return LazyIterationConsumer.NextIteration.ABORT;
                        }
                    }
                }
            }

            return LazyIterationConsumer.NextIteration.CONTINUE;
        });
        msg.replyTo().tell(result);
        return this;
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestOtherEntities(ServerEntityManagerMessages.RequestOtherEntities msg) {
        // 1. Log entry
        getContext().getLog().info("requestOtherEntities: Received request for box {}, except {}, predicate {}", msg.box(), msg.except(), msg.predicate().getClass().getName());
//        getContext().getLog().info(msg.predicate().getClass().getSimpleName());
//        Predicate<Entity> predicate = msg.except() == null ? EntityPredicates.CAN_COLLIDE : EntityPredicates.EXCEPT_SPECTATOR.and(msg.except()::collidesWith);

        List<Entity> list = Lists.<Entity>newArrayList();
//        getContext().getLog().info("msg.predicate() instanceof DummyPredicate {}", msg.predicate() instanceof DummyPredicate);
        try {
            this.entityManager.getLookup().forEachIntersects(msg.box(), entity -> {
                // 2. Log each entity being considered
                // Be careful: this can be VERY verbose if many entities are in the box
//                getContext().getLog().info("requestOtherEntities: Checking entity {}", entity);

                boolean shouldAdd = false;
                if (entity != msg.except()) {
                    // 3. Log before predicate test
//                    getContext().getLog().info("requestOtherEntities: Testing predicate for entity {}", entity);
//                    if(msg.predicate() instanceof DummyPredicate){
//                        throw new RuntimeException("DummyPredicate " + entity);
//                    }
                    boolean predicateResult = msg.predicate().test(entity);
                    // 4. Log after predicate test
//                    getContext().getLog().info("requestOtherEntities: Predicate for entity {} returned {}", entity, predicateResult);
                    if (predicateResult) {
                        list.add(entity);
                        // 5. Log when entity added
//                        getContext().getLog().info("requestOtherEntities: Added entity {} to list", entity);
                        shouldAdd = true; // Keep track if added
                    }
                }

                if (entity instanceof EnderDragonEntity enderDragonEntity) {
                    // 6. Log EnderDragon found
//                    getContext().getLog().info("requestOtherEntities: Found EnderDragonEntity: {}", enderDragonEntity);
                    for (EnderDragonPart enderDragonPart : enderDragonEntity.getBodyParts()) {
                        // 7. Log each part
                        getContext().getLog().info("requestOtherEntities: Checking EnderDragonPart {}", enderDragonPart);

                        // CRITICAL BUG HERE: You are checking `entity != msg.except()`
                        // instead of `enderDragonPart != msg.except()` for the parts.
                        // If the main dragon entity is the one to be excepted, NO parts will be added.
                        // This might not be the deadlock, but it's a significant functional bug.
                        // Corrected logic:
                        if (enderDragonPart != msg.except()) { // Check the part, not the main dragon
                            // 8. Log before predicate test for part
                            // getContext().getLog().debug("requestOtherEntities: Testing predicate for part {}", enderDragonPart);
                            boolean partPredicateResult = msg.predicate().test(enderDragonPart);
                            // 9. Log after predicate test for part
                            getContext().getLog().info("requestOtherEntities: Predicate for part {} returned {}", enderDragonPart, partPredicateResult);
                            if (partPredicateResult) {
                                list.add(enderDragonPart);
                                // 10. Log when part added
                                getContext().getLog().info("requestOtherEntities: Added EnderDragonPart {} to list", enderDragonPart);
                            }
                        } else if (shouldAdd) { // If the main dragon was added, and this part IS the 'except', log it.
                            getContext().getLog().warn("requestOtherEntities: EnderDragonPart {} was skipped because it's the 'except' entity, but main dragon was added.", enderDragonPart);
                        }
                    }
                }
            });

            // 11. Log before replying
//            getContext().getLog().info("requestOtherEntities: Replying with list size: {}", list.size());
            msg.replyTo().tell(list);
            return this;

        } catch (Throwable t) { // Catch EVERYTHING
            // 12. CRITICAL: Log any exception
            getContext().getLog().error("requestOtherEntities: CRITICAL ERROR during processing. Replying with empty list.", t);
            // Ensure the asker gets a reply, even if it's an error or empty.
            // Otherwise, the asker (main thread) will block forever on .get()
            msg.replyTo().tell(Lists.newArrayList()); // Or a specific error message type
            // Depending on the severity, you might want to stop the actor or return Behaviors.stopped()
            // For now, just returning 'this' will allow it to process next message, but it might be in a bad state.
            // If the error is unrecoverable, consider:
            // return Behaviors.stopped(() -> getContext().getLog().error("Actor stopped due to unrecoverable error in requestOtherEntities"));
            return this;
        }
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
//        System.out.println("Starting request isLoaded");
        boolean result = this.entityManager.isLoaded(msg.chunkPos());
        msg.replyTo().tell(result);
//        System.out.println("Finished request isLoaded");
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