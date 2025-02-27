package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.world.entity.SimpleEntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

@Mixin(SimpleEntityLookup.class)
public class SimpleEntityLookupMixin {
    @Inject(method = "iterate", at = @At("HEAD"))
    private void onCanStart(CallbackInfoReturnable<Boolean> cir) {
        if(Thread.currentThread() == ZefirOptimizations.SERVER.getThread()){
            System.out.println("Got FollowParentGoal canStart check on the thread: " + Thread.currentThread().getName());
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                System.out.println(element);
            }
        }
    }
}
