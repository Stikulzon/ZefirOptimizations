package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void onCanStart(CallbackInfo ci) {
        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread()){
            System.out.println("Got tickMovement canStart check on the thread: " + Thread.currentThread().getName());
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                System.out.println(element);
            }
        }
    }
}
