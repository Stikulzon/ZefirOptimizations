package ua.zefir.zefiroptimizations.mixin;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AskPattern;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.random.RandomSequencesState;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.entity.EntityHandler;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.storage.ChunkDataAccess;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;
import ua.zefir.zefiroptimizations.data.DummyEntityLookup;
import ua.zefir.zefiroptimizations.data.DummyServerEntityManager;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

@Mixin(ServerWorld.class)
public class ServerWorldMixin  {
    @Unique
    private DummyEntityLookup<Entity> dummyEntityLookup;
    @Unique
    private ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor;

    @Redirect(method = "<init>", at = @At(
            value = "NEW",
            target = "(Ljava/lang/Class;Lnet/minecraft/world/entity/EntityHandler;Lnet/minecraft/world/storage/ChunkDataAccess;)Lnet/minecraft/server/world/ServerEntityManager;"))
    private ServerEntityManager serverEntityManagerInit(Class entityClass, EntityHandler handler, ChunkDataAccess dataAccess, MinecraftServer server, Executor workerExecutor, LevelStorage.Session session, ServerWorldProperties properties, RegistryKey worldKey, DimensionOptions dimensionOptions, WorldGenerationProgressListener worldGenerationProgressListener, boolean debugWorld, long seed, List spawners, boolean shouldTickTime, RandomSequencesState randomSequencesState) {
        ServerEntityManager<Entity> tempEntityManager = new ServerEntityManager<>(entityClass, handler, dataAccess);
        return new DummyServerEntityManager<>(entityClass, handler, dataAccess, worldKey, tempEntityManager);
    }

    @Inject(method = "<init>", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerEntityManager;<init>(Ljava/lang/Class;Lnet/minecraft/world/entity/EntityHandler;Lnet/minecraft/world/storage/ChunkDataAccess;)V",
            shift = At.Shift.AFTER
    ))
    private void serverEntityManagerInit(MinecraftServer server, Executor workerExecutor, LevelStorage.Session session, ServerWorldProperties properties, RegistryKey worldKey, DimensionOptions dimensionOptions, WorldGenerationProgressListener worldGenerationProgressListener, boolean debugWorld, long seed, List spawners, boolean shouldTickTime, RandomSequencesState randomSequencesState, CallbackInfo ci) {
        this.dummyEntityLookup = new DummyEntityLookup<Entity>(worldKey);
    }

    @Inject(method = "getEntityLookup", at = @At("HEAD"), cancellable = true)
    private void onGetEntityLookup(CallbackInfoReturnable<EntityLookup<Entity>> cir) {
        cir.setReturnValue(this.dummyEntityLookup);
    }

    @Redirect(method = "tickEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V"))
    private void redirectTickEntity(Entity instance) {
        ZefirOptimizations.getActorSystem().tell(new ZefirsActorMessages.TickSingleEntity(instance));
    }

    @Redirect(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tickRiding()V"))
    private void redirectTickRiding(Entity instance) {
        ZefirOptimizations.getActorSystem().tell(new ZefirsActorMessages.TickRidingSingleEntity(instance));
    }

    @Inject(method = "collectEntitiesByType(Lnet/minecraft/util/TypeFilter;Ljava/util/function/Predicate;Ljava/util/List;I)V", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void onCollectEntitiesByType(TypeFilter<Entity, T> filter, Predicate<? super T> predicate, List<? super T> result, int limit, CallbackInfo ci) {
        World self = (World) (Object) this;

        if(this.entityManagerActor == null) {
            CompletionStage<ZefirsActorMessages.ResponseEntityManagerActorRef> resultFuture =
                    AskPattern.ask(
                            ZefirOptimizations.getActorSystem(),
                            replyTo -> new ZefirsActorMessages.RequestEntityManagerActorRef(self.getRegistryKey(), replyTo),
                            ZefirOptimizations.timeout,
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                this.entityManagerActor = resultFuture.toCompletableFuture().get().entityManagerActor();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
        }

            CompletionStage<List<? extends T>> resultFuture =
                    AskPattern.ask(
                            this.entityManagerActor,
                            replyTo -> {
                                ServerEntityManagerMessages.RequestEntitiesByTypeServerWorld<T> originalRequest = new ServerEntityManagerMessages.RequestEntitiesByTypeServerWorld<>(filter, predicate, limit, replyTo);
                                return new ServerEntityManagerMessages.RequestEntitiesByTypeServerWorld<>(originalRequest);
                            },
                            ZefirOptimizations.timeout,
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                result.addAll(resultFuture.toCompletableFuture().get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
        ci.cancel();
    }
}
