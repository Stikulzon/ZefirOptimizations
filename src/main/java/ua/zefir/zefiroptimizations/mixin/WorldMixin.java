package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

@Mixin(World.class)
public class WorldMixin {
    @Inject(method = "getOtherEntities", at = @At("HEAD"), cancellable = true)
    private void onGetOtherEntities(Entity except, Box box, Predicate<? super Entity> predicate, CallbackInfoReturnable<List<Entity>> cir) {
        World self = (World) (Object) this;
        if (Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
            CompletableFuture<Void> future = new CompletableFuture<>();

            ZefirOptimizations.SERVER.execute(() -> {
                cir.setReturnValue(self.getOtherEntities(except, box, predicate));
                future.complete(null);
            });

            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
//            cir.cancel();
        }
    }
}
