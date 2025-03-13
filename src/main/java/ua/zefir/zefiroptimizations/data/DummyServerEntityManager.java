package ua.zefir.zefiroptimizations.data;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AskPattern;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.util.annotation.Debug;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityHandler;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingStatus;
import net.minecraft.world.storage.ChunkDataAccess;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;

import java.io.Writer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

public class DummyServerEntityManager<T extends EntityLike> extends ServerEntityManager<T> {
    private final ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor;
    public DummyServerEntityManager(Class entityClass, EntityHandler handler, ChunkDataAccess dataAccess, RegistryKey<World> worldRegistryKey, ServerEntityManager entityManager) {
        super(entityClass, handler, dataAccess);

        CompletionStage<ZefirsActorMessages.ResponseEntityManagerActorRef> resultFuture =
                AskPattern.ask(
                        ZefirOptimizations.getActorSystem(),
                        replyTo -> new ZefirsActorMessages.ServerEntityManagerCreated<T>(entityManager, worldRegistryKey, replyTo),
                        ZefirOptimizations.timeout,
                        ZefirOptimizations.getActorSystem().scheduler());
        try {
            this.entityManagerActor = resultFuture.toCompletableFuture().get().entityManagerActor();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
        }
    }

    @Override
    public void tick() {
            this.entityManagerActor
                    .tell(new ServerEntityManagerMessages.Tick());
    }

    @Override
    public void loadEntities(Stream<T> entities) {
            this.entityManagerActor
                    .tell(new ServerEntityManagerMessages.LoadEntities((Stream<Entity>) entities));
    }

    @Override
    public void addEntities(Stream<T> entities) {
            this.entityManagerActor
                    .tell(new ServerEntityManagerMessages.AddEntities((Stream<Entity>) entities));
    }

    @Override
    public void updateTrackingStatus(ChunkPos chunkPos, ChunkLevelType levelType) {
            this.entityManagerActor
                    .tell(new ServerEntityManagerMessages.UpdateTrackingStatusChunkLevelType(chunkPos, levelType));
    }

    @Override
    public void updateTrackingStatus(ChunkPos chunkPos, EntityTrackingStatus trackingStatus) {
            this.entityManagerActor
                    .tell(new ServerEntityManagerMessages.UpdateTrackingStatus(chunkPos, trackingStatus));
    }

    public void save() {
            this.entityManagerActor
                    .tell(new ServerEntityManagerMessages.Save());
    }

    @Override
    public void flush() {
            this.entityManagerActor
                    .tell(new ServerEntityManagerMessages.Flush());
    }

    @Override
    public void close() {
            this.entityManagerActor
                    .tell(new ServerEntityManagerMessages.Close());
    }

    @Override
    public void dump(Writer writer) {
            this.entityManagerActor
                    .tell(new ServerEntityManagerMessages.Dump(writer));
    }

    @Override
    public boolean addEntity(T entity) {
            CompletionStage<Boolean> resultFuture =
                    AskPattern.ask(
                            this.entityManagerActor,
                            replyTo -> new ServerEntityManagerMessages.RequestAddEntity((Entity) entity, replyTo),
                            ZefirOptimizations.timeout,
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                boolean result = resultFuture.toCompletableFuture().get();
                return result;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
    }

    @Override
    public boolean has(UUID uuid) {
            CompletionStage<Boolean> resultFuture =
                    AskPattern.ask(
                            this.entityManagerActor,
                            replyTo -> new ServerEntityManagerMessages.RequestHas(uuid, replyTo),
                            ZefirOptimizations.timeout,
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                boolean result = resultFuture.toCompletableFuture().get();
                return result;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
    }

    @Override
    public boolean isLoaded(long chunkPos) {
            CompletionStage<Boolean> resultFuture =
                    AskPattern.ask(
                            this.entityManagerActor,
                            replyTo -> new ServerEntityManagerMessages.RequestIsLoaded(chunkPos, replyTo),
                            ZefirOptimizations.timeout,
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                boolean result = resultFuture.toCompletableFuture().get();
                return result;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
    }

    @Override
    public boolean shouldTick(BlockPos pos) {
            CompletionStage<Boolean> resultFuture =
                    AskPattern.ask(
                            this.entityManagerActor,
                            replyTo -> new ServerEntityManagerMessages.RequestShouldTickBlockPos(pos, replyTo),
                            ZefirOptimizations.timeout,
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                boolean result = resultFuture.toCompletableFuture().get();
                return result;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
    }

    @Override
    public boolean shouldTick(ChunkPos pos) {
            CompletionStage<Boolean> resultFuture =
                    AskPattern.ask(
                            this.entityManagerActor,
                            replyTo -> new ServerEntityManagerMessages.RequestShouldTickChunkPos(pos, replyTo),
                            ZefirOptimizations.timeout,
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                boolean result = resultFuture.toCompletableFuture().get();
                return result;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
    }

    @Debug
    @Override
    public String getDebugString() {
            CompletionStage<ServerEntityManagerMessages.ResponseDebugString> resultFuture =
                    AskPattern.ask(
                            this.entityManagerActor,
                            ServerEntityManagerMessages.RequestDebugString::new,
                            ZefirOptimizations.timeout,
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                String result = resultFuture.toCompletableFuture().get().debugString();
                return result;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
    }

    @Override
    public int getIndexSize() {
            CompletionStage<ServerEntityManagerMessages.ResponseIndexSize> resultFuture =
                    AskPattern.ask(
                            this.entityManagerActor,
                            ServerEntityManagerMessages.RequestIndexSize::new,
                            ZefirOptimizations.timeout,
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                int result = resultFuture.toCompletableFuture().get().indexSize();
                return result;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
    }

}
