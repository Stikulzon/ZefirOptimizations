package ua.zefir.zefiroptimizations.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.*;
import net.minecraft.world.storage.ChunkDataAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.data.DummySectionedEntityCache;
import ua.zefir.zefiroptimizations.data.ServerEntityManagerRef;

import java.util.stream.LongStream;
import java.util.stream.Stream;

@Mixin(ServerEntityManager.class)
public abstract class ServerEntityManagerMixin<T extends EntityLike>{

    @Shadow @Final @Mutable
    SectionedEntityCache<T> cache;

    @Shadow @Final private Long2ObjectMap<EntityTrackingStatus> trackingStatuses;

    @Inject(method = "<init>", at = @At(
            value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;defaultReturnValue(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
    private void init(Class entityClass, EntityHandler handler, ChunkDataAccess dataAccess, CallbackInfo ci) {
        cache = new DummySectionedEntityCache<>(entityClass, this.trackingStatuses);
    }

    @Redirect(method = "entityLeftSection",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/SectionedEntityCache;removeSection(J)V"
            )
    )
    private void redirectEntityLeftSection(SectionedEntityCache<T> instance, long sectionPos) {
        ServerEntityManager<T> self = (ServerEntityManager<T>) (Object) this;
        ((DummySectionedEntityCache<T>) cache).removeSection(sectionPos, ((ServerEntityManagerRef) self).getEntityManagerActor());
    }

    @Redirect(method = "addEntity(Lnet/minecraft/world/entity/EntityLike;Z)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/SectionedEntityCache;getTrackingSection(J)Lnet/minecraft/world/entity/EntityTrackingSection;"
            )
    )
    private EntityTrackingSection<T> redirectAddEntity(SectionedEntityCache<T> instance, long sectionPos) {
        ServerEntityManager<T> self = (ServerEntityManager<T>) (Object) this;
        return ((DummySectionedEntityCache<T>) cache).getTrackingSection(sectionPos, ((ServerEntityManagerRef) self).getEntityManagerActor());
    }

    @Redirect(method = "updateTrackingStatus(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/entity/EntityTrackingStatus;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/SectionedEntityCache;getTrackingSections(J)Ljava/util/stream/Stream;"
            )
    )
    private Stream<EntityTrackingSection<T>> redirectGetTrackingSections(SectionedEntityCache instance, long chunkPos) {
        ServerEntityManager<T> self = (ServerEntityManager<T>) (Object) this;
        return ((DummySectionedEntityCache<T>) cache).getTrackingSections(chunkPos, ((ServerEntityManagerRef) self).getEntityManagerActor());
    }

    @Redirect(method = "trySave",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/SectionedEntityCache;getTrackingSections(J)Ljava/util/stream/Stream;"
            )
    )
    private Stream<EntityTrackingSection<T>> redirectGetTrackingSections2(SectionedEntityCache instance, long chunkPos) {
        ServerEntityManager<T> self = (ServerEntityManager<T>) (Object) this;
        return ((DummySectionedEntityCache<T>) cache).getTrackingSections(chunkPos, ((ServerEntityManagerRef) self).getEntityManagerActor());
    }

    @Redirect(method = "getLoadedChunks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/SectionedEntityCache;getChunkPositions()Lit/unimi/dsi/fastutil/longs/LongSet;"
            )
    )
    private LongSet redirectGetChunkPositions(SectionedEntityCache instance) {
        ServerEntityManager<T> self = (ServerEntityManager<T>) (Object) this;
        return ((DummySectionedEntityCache<T>) cache).getChunkPositions(((ServerEntityManagerRef) self).getEntityManagerActor());
    }

    @Redirect(method = "dump",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/SectionedEntityCache;getChunkPositions()Lit/unimi/dsi/fastutil/longs/LongSet;"
            )
    )
    private LongSet redirectGetChunkPositions2(SectionedEntityCache instance) {
        ServerEntityManager<T> self = (ServerEntityManager<T>) (Object) this;
        return ((DummySectionedEntityCache<T>) cache).getChunkPositions(((ServerEntityManagerRef) self).getEntityManagerActor());
    }

    @Redirect(method = "method_31813",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/SectionedEntityCache;getSections(J)Ljava/util/stream/LongStream;"
            )
    )
    private LongStream redirectGetSections(SectionedEntityCache instance, long chunkPos) {
        ServerEntityManager<T> self = (ServerEntityManager<T>) (Object) this;
        return ((DummySectionedEntityCache<T>) cache).getSections(chunkPos, ((ServerEntityManagerRef) self).getEntityManagerActor());
    }

    @Redirect(method = "method_31814",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/SectionedEntityCache;findTrackingSection(J)Lnet/minecraft/world/entity/EntityTrackingSection;"
            )
    )
    private EntityTrackingSection<T> redirectFindTrackingSection(SectionedEntityCache instance, long chunkPos) {
        ServerEntityManager<T> self = (ServerEntityManager<T>) (Object) this;
        return ((DummySectionedEntityCache<T>) cache).findTrackingSection(chunkPos, ((ServerEntityManagerRef) self).getEntityManagerActor());
    }

    @Redirect(method = "getDebugString",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/SectionedEntityCache;sectionCount()I"
            )
    )
    private int redirectSectionCount(SectionedEntityCache instance) {
        ServerEntityManager<T> self = (ServerEntityManager<T>) (Object) this;
        return ((DummySectionedEntityCache<T>) cache).sectionCount(((ServerEntityManagerRef) self).getEntityManagerActor());
    }

//    @Inject(method = "<init>", at = @At(
//            value = "TAIL"))
//    private void init(Class entityClass, EntityHandler handler, ChunkDataAccess dataAccess, CallbackInfo ci) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//            ServerEntityManager<T> self = (ServerEntityManager<T>) (Object) this;
//
//            ZefirOptimizations.getActorSystem()
//                    .tell(new ZefirsActorMessages.ServerEntityManagerCreated<>(self));
//
//            System.out.println("ServerEntityManager created on the thread: " + Thread.currentThread().getName());
//            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
//            for (StackTraceElement element : stackTrace) {
//                System.out.println(element);
//            }
//        }
//    }

    @Inject(method = "getLookup", at = @At("HEAD"))
    private void onGetLookup(CallbackInfoReturnable<EntityLookup<T>> cir) {
        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
            throw new RuntimeException("Unauthorized EntityLookup access. You need to request it from the ServerEntityManager actor.");
        }
    }

//    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
//    private void onTick(CallbackInfo ci) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//            entityManagerActor
//                    .tell(new ServerEntityManagerMessages.Tick());
//            ci.cancel();
//        }
//    }
//
//    @Inject(method = "loadEntities", at = @At("HEAD"), cancellable = true)
//    private void onLoadEntities(Stream<T> entities, CallbackInfo ci) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//            entityManagerActor
//                    .tell(new ServerEntityManagerMessages.LoadEntities((Stream<Entity>) entities));
//            ci.cancel();
//        }
//    }
//
//    @Inject(method = "addEntities", at = @At("HEAD"), cancellable = true)
//    private void onAddEntities(Stream<T> entities, CallbackInfo ci) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//            entityManagerActor
//                    .tell(new ServerEntityManagerMessages.AddEntities((Stream<Entity>) entities));
//            ci.cancel();
//        }
//    }
//
//    @Inject(method = "updateTrackingStatus(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/server/world/ChunkLevelType;)V", at = @At("HEAD"), cancellable = true)
//    private void onUpdateTrackingStatusChunkLevelType(ChunkPos chunkPos, ChunkLevelType levelType, CallbackInfo ci) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//            entityManagerActor
//                    .tell(new ServerEntityManagerMessages.UpdateTrackingStatusChunkLevelType(chunkPos, levelType));
//            ci.cancel();
//        }
//    }
//
//    @Inject(method = "updateTrackingStatus(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/entity/EntityTrackingStatus;)V", at = @At("HEAD"), cancellable = true)
//    private void onUpdateTrackingStatus(ChunkPos chunkPos, EntityTrackingStatus trackingStatus, CallbackInfo ci) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//            entityManagerActor
//                    .tell(new ServerEntityManagerMessages.UpdateTrackingStatus(chunkPos, trackingStatus));
//            ci.cancel();
//        }
//    }
//
//    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
//    private void onSave(CallbackInfo ci) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//            entityManagerActor
//                    .tell(new ServerEntityManagerMessages.Save());
//            ci.cancel();
//        }
//    }
//
//    @Inject(method = "flush", at = @At("HEAD"), cancellable = true)
//    private void onFlush(CallbackInfo ci) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//            entityManagerActor
//                    .tell(new ServerEntityManagerMessages.Flush());
//            ci.cancel();
//        }
//    }
//
//    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
//    private void onClose(CallbackInfo ci) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//            entityManagerActor
//                    .tell(new ServerEntityManagerMessages.Close());
//            ci.cancel();
//        }
//    }
//
//    @Inject(method = "dump", at = @At("HEAD"), cancellable = true)
//    private void onDump(Writer writer, CallbackInfo ci) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//            entityManagerActor
//                    .tell(new ServerEntityManagerMessages.Dump(writer));
//            ci.cancel();
//        }
//    }
//
//    @Inject(method = "addEntity(Lnet/minecraft/world/entity/EntityLike;)Z", at = @At("HEAD"), cancellable = true)
//    private void onAddEntity(T entity, CallbackInfoReturnable<Boolean> cir) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//
//            CompletionStage<Boolean> resultFuture =
//                    AskPattern.ask(
//                            entityManagerActor,
//                            replyTo -> new ServerEntityManagerMessages.RequestAddEntity((Entity) entity, replyTo),
//                            Duration.ofSeconds(3),
//                            ZefirOptimizations.getActorSystem().scheduler());
//            try {
//                boolean result = resultFuture.toCompletableFuture().get();
//                cir.setReturnValue(result);
//            } catch (InterruptedException | ExecutionException e) {
//                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
//            }
//        }
//    }
//
//    @Inject(method = "has", at = @At("HEAD"), cancellable = true)
//    private void onHas(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//
//            CompletionStage<Boolean> resultFuture =
//                    AskPattern.ask(
//                            entityManagerActor,
//                            replyTo -> new ServerEntityManagerMessages.RequestHas(uuid, replyTo),
//                            Duration.ofSeconds(3),
//                            ZefirOptimizations.getActorSystem().scheduler());
//            try {
//                boolean result = resultFuture.toCompletableFuture().get();
//                cir.setReturnValue(result);
//            } catch (InterruptedException | ExecutionException e) {
//                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
//            }
//        }
//    }
//
//    @Inject(method = "isLoaded", at = @At("HEAD"), cancellable = true)
//    private void onIsLoaded(long chunkPos, CallbackInfoReturnable<Boolean> cir) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//
//            CompletionStage<Boolean> resultFuture =
//                    AskPattern.ask(
//                            entityManagerActor,
//                            replyTo -> new ServerEntityManagerMessages.RequestIsLoaded(chunkPos, replyTo),
//                            Duration.ofSeconds(3),
//                            ZefirOptimizations.getActorSystem().scheduler());
//            try {
//                boolean result = resultFuture.toCompletableFuture().get();
//                cir.setReturnValue(result);
//            } catch (InterruptedException | ExecutionException e) {
//                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
//            }
//        }
//    }
//
//    @Inject(method = "shouldTick(Lnet/minecraft/util/math/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
//    private void onShouldTickBlockPos(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//
//            CompletionStage<Boolean> resultFuture =
//                    AskPattern.ask(
//                            entityManagerActor,
//                            replyTo -> new ServerEntityManagerMessages.RequestShouldTickBlockPos(pos, replyTo),
//                            Duration.ofSeconds(3),
//                            ZefirOptimizations.getActorSystem().scheduler());
//            try {
//                boolean result = resultFuture.toCompletableFuture().get();
//                cir.setReturnValue(result);
//            } catch (InterruptedException | ExecutionException e) {
//                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
//            }
//        }
//    }
//
//    @Inject(method = "shouldTick(Lnet/minecraft/util/math/ChunkPos;)Z", at = @At("HEAD"), cancellable = true)
//    private void onShouldTickChunkPos(ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//
//            CompletionStage<Boolean> resultFuture =
//                    AskPattern.ask(
//                            entityManagerActor,
//                            replyTo -> new ServerEntityManagerMessages.RequestShouldTickChunkPos(pos, replyTo),
//                            Duration.ofSeconds(3),
//                            ZefirOptimizations.getActorSystem().scheduler());
//            try {
//                boolean result = resultFuture.toCompletableFuture().get();
//                cir.setReturnValue(result);
//            } catch (InterruptedException | ExecutionException e) {
//                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
//            }
//        }
//    }
//
//    @Inject(method = "getDebugString", at = @At("HEAD"), cancellable = true)
//    private void onGetDebugString(CallbackInfoReturnable<String> cir) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//
//            CompletionStage<ServerEntityManagerMessages.ResponseDebugString> resultFuture =
//                    AskPattern.ask(
//                            entityManagerActor,
//                            ServerEntityManagerMessages.RequestDebugString::new,
//                            Duration.ofSeconds(3),
//                            ZefirOptimizations.getActorSystem().scheduler());
//            try {
//                String result = resultFuture.toCompletableFuture().get().debugString();
//                cir.setReturnValue(result);
//            } catch (InterruptedException | ExecutionException e) {
//                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
//            }
//        }
//    }
//
//    @Inject(method = "getIndexSize", at = @At("HEAD"), cancellable = true)
//    private void onGetIndexSize(CallbackInfoReturnable<Integer> cir) {
//        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
//
//            CompletionStage<ServerEntityManagerMessages.ResponseIndexSize> resultFuture =
//                    AskPattern.ask(
//                            entityManagerActor,
//                            ServerEntityManagerMessages.RequestIndexSize::new,
//                            Duration.ofSeconds(3),
//                            ZefirOptimizations.getActorSystem().scheduler());
//            try {
//                int result = resultFuture.toCompletableFuture().get().indexSize();
//                cir.setReturnValue(result);
//            } catch (InterruptedException | ExecutionException e) {
//                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
//            }
//        }
//    }
}
