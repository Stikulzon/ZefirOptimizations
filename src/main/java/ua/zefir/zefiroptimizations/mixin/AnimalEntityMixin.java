package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

import java.util.concurrent.CompletableFuture;

@Mixin(AnimalEntity.class)
public class AnimalEntityMixin {
    @Inject(method = "breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;)V", at = @At("HEAD"), cancellable = true)
    private void onBreed(ServerWorld world, AnimalEntity other, CallbackInfo ci) {
        AnimalEntity self = (AnimalEntity) (Object) this;

        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
            CompletableFuture<Void> future = new CompletableFuture<>();

            ZefirOptimizations.SERVER.execute(() -> {
                self.breed(world, other);
                future.complete(null);
            });

            try {
                future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            ci.cancel();
        }
    }
}
