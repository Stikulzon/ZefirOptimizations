package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

@Mixin(ServerWorld.class)
public class ServerWorldMixin  {

//    @Inject(method = "<init>", at = @At(
//            value = "INVOKE",
//            target = "Lnet/minecraft/server/world/ServerEntityManager;<init>(Ljava/lang/Class;Lnet/minecraft/world/entity/EntityHandler;Lnet/minecraft/world/storage/ChunkDataAccess;)V"))
//    private void serverEntityManagerInit(MinecraftServer server, Executor workerExecutor, LevelStorage.Session session, ServerWorldProperties properties, RegistryKey worldKey, DimensionOptions dimensionOptions, WorldGenerationProgressListener worldGenerationProgressListener, boolean debugWorld, long seed, List spawners, boolean shouldTickTime, RandomSequencesState randomSequencesState, CallbackInfo ci) {
//        ZefirOptimizations.getActorSystem()
//                .tell(new ZefirsActorMessages.ServerEntityManagerCreated<>(original));
//        return original;
//    }

    @Inject(method = "getEntityLookup", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfoReturnable<EntityLookup<Entity>> cir) {
        cir.setReturnValue(ZefirOptimizations.getDummyEntityLookup());
    }
}
