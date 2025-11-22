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
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.ChunkSectionPos;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;
import ua.zefir.zefiroptimizations.mixin.ServerEntityManagerAccessor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class ServerEntityManagerActor extends AbstractBehavior<ServerEntityManagerMessages.ServerEntityManagerMessage> {
    private final ServerEntityManager<Entity> entityManager;

    private final long timeout = 1000;
    private final TimeUnit timeUnit = TimeUnit.MILLISECONDS;

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

                .onMessage(ServerEntityManagerMessages.ListenerInternalRemove.class, this::handleListenerInternalRemove)
                .onMessage(ServerEntityManagerMessages.ListenerInternalAdd.class, this::handleListenerInternalAdd)

                .build();
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> handleListenerInternalRemove(ServerEntityManagerMessages.ListenerInternalRemove msg) {
        Entity entity = (Entity) msg.entity();
        long sectionPos = msg.sectionPos();

        // Access the cache directly via accessor
        var cache = ((ServerEntityManagerAccessor)this.entityManager).getCache();
        var section = cache.findTrackingSection(sectionPos);

        if (section != null) {
            // 1. Remove the entity
            if (!section.remove(entity)) {
                getContext().getLog().warn("Entity {} wasn't found in section {} (destroying due to {})", entity, ChunkSectionPos.from(sectionPos), msg.reason());
            }

            // 2. Cleanup section if empty (Logic from entityLeftSection)
            if (section.isEmpty()) {
                cache.removeSection(sectionPos);
            }
        }
        return this;
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> handleListenerInternalAdd(ServerEntityManagerMessages.ListenerInternalAdd msg) {
        Entity entity = (Entity) msg.entity();

        // We must recalculate the position here because the redirect only gives us the entity
        long newSectionPos = ChunkSectionPos.toLong(entity.getBlockPos());

        var cache = ((ServerEntityManagerAccessor)this.entityManager).getCache();

        // getTrackingSection creates it if it doesn't exist
        var section = cache.getTrackingSection(newSectionPos);

        section.add(entity);
        return this;
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
    private <T extends Entity> Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestEntitiesByTypeServerWorld(
            ServerEntityManagerMessages.RequestEntitiesByTypeServerWorld msg) {

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Callable<List<? super T>> entityLookupTask = () -> {
            final List<? super T> result = Collections.synchronizedList(Lists.newArrayList());

            this.entityManager.getLookup().forEach(msg.filter(), entity -> {
                if (Thread.currentThread().isInterrupted()) {
                    return LazyIterationConsumer.NextIteration.ABORT;
                }

                if (msg.predicate().test(entity)) {
                    result.add((T) entity);
                    if (result.size() >= msg.limit()) {
                        return LazyIterationConsumer.NextIteration.ABORT;
                    }
                }
                return LazyIterationConsumer.NextIteration.CONTINUE;
            });
            return result;
        };

        Future<List<? super T>> future = executor.submit(entityLookupTask);

        try {
            List<? super T> result = future.get(timeout, timeUnit);
            msg.replyTo().tell(result);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Entity lookup operation timed out after " + timeout + " " + timeUnit.toString(), e);
        } catch (InterruptedException | ExecutionException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error during entity lookup", e);
        } finally {
            executor.shutdown();
        }

        return this;
    }

    private <T extends Entity> Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestEntitiesByTypeWorld(
            ServerEntityManagerMessages.RequestEntitiesByTypeWorld msg) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Callable<List<? super T>> entityLookupTask = () -> {
            final List<? super T> result = Collections.synchronizedList(Lists.newArrayList());

            this.entityManager.getLookup().forEachIntersects(msg.filter(), msg.box(), entity -> {
                if (Thread.currentThread().isInterrupted()) {
                    return LazyIterationConsumer.NextIteration.ABORT;
                }

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
            return result;
        };

        Future<List<? super T>> future = executor.submit(entityLookupTask);

        try {
            List<? super T> result = future.get(timeout, timeUnit);
            msg.replyTo().tell(result);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Entity lookup operation timed out after " + 1000 + " " + TimeUnit.MILLISECONDS.toString(), e);
        } catch (InterruptedException | ExecutionException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error during entity lookup", e);
        } finally {
            executor.shutdown();
        }

        return this;
    }

    private Behavior<ServerEntityManagerMessages.ServerEntityManagerMessage> requestOtherEntities(
            ServerEntityManagerMessages.RequestOtherEntities msg) {

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Callable<List<Entity>> entityLookupTask = () -> {
            final List<Entity> list = Collections.synchronizedList(Lists.newArrayList());

            this.entityManager.getLookup().forEachIntersects(msg.box(), entity -> {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                boolean shouldAdd = false;
                if (entity != msg.except()) {
                    if (msg.predicate().test(entity)) {
                        list.add(entity);
                        shouldAdd = true;
                    }
                }

                if (entity instanceof EnderDragonEntity enderDragonEntity) {
                    for (EnderDragonPart enderDragonPart : enderDragonEntity.getBodyParts()) {
                        getContext().getLog().info("requestOtherEntities: Checking EnderDragonPart {}", enderDragonPart);

                        if (enderDragonPart != msg.except()) {
                            boolean partPredicateResult = msg.predicate().test(enderDragonPart);
                            getContext().getLog().info("requestOtherEntities: Predicate for part {} returned {}", enderDragonPart, partPredicateResult);
                            if (partPredicateResult) {
                                list.add(enderDragonPart);
                                getContext().getLog().info("requestOtherEntities: Added EnderDragonPart {} to list", enderDragonPart);
                            }
                        } else if (shouldAdd) {
                            getContext().getLog().warn("requestOtherEntities: EnderDragonPart {} was skipped because it's the 'except' entity, but main dragon was added.", enderDragonPart);
                        }
                    }
                }
            });
            return list;
        };

        Future<List<Entity>> future = executor.submit(entityLookupTask);

        try {
            List<Entity> result = future.get(timeout, timeUnit);
            msg.replyTo().tell(result);
        } catch (TimeoutException e) {
            future.cancel(true);
            getContext().getLog().error("requestOtherEntities: CRITICAL ERROR - Operation timed out.", e);
            msg.replyTo().tell(Lists.newArrayList());
            throw new RuntimeException("Entity lookup operation timed out after " + timeout + " " + timeUnit.toString(), e);
        } catch (InterruptedException | ExecutionException e) {
            future.cancel(true);
            getContext().getLog().error("requestOtherEntities: CRITICAL ERROR during processing. Replying with empty list.", e);
            msg.replyTo().tell(Lists.newArrayList());
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error during entity lookup", e);
        } finally {
            executor.shutdown();
        }

        return this;
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