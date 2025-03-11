package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.world.entity.SimpleEntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleEntityLookup.class)
public class EntityLookupMixin {
//    @Unique
//    public int concurrentModificationCheck = 0;
//
//    @Inject(method = "forEach", at = @At("HEAD"))
//    private void onForEach(CallbackInfo ci) {
//        concurrentModificationCheck++;
//    }
//
//    @Inject(method = "forEach", at = @At("TAIL"))
//    private void onForEach2(CallbackInfo ci) {
//        concurrentModificationCheck--;
//        if (concurrentModificationCheck != 0) {
//            throw new RuntimeException("Concurrent modification check failed, ConcurrentModificationCheck value:" + concurrentModificationCheck);
//        }
//    }
//
//    @Inject(method = "forEachIntersects(Lnet/minecraft/util/math/Box;Ljava/util/function/Consumer;)V", at = @At("HEAD"))
//    private void onForEachIntersects(CallbackInfo ci) {
//        concurrentModificationCheck++;
//    }
//
//    @Inject(method = "forEachIntersects(Lnet/minecraft/util/math/Box;Ljava/util/function/Consumer;)V", at = @At("TAIL"))
//    private void onForEachIntersects2(CallbackInfo ci) {
//        concurrentModificationCheck--;
//        if (concurrentModificationCheck != 0) {
//            throw new RuntimeException("Concurrent modification check failed, ConcurrentModificationCheck value:" + concurrentModificationCheck);
//        }
//    }
//
//    @Inject(method = "forEachIntersects(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)V", at = @At("HEAD"))
//    private void onForEachIntersects3(CallbackInfo ci) {
//        concurrentModificationCheck++;
//    }
//
//    @Inject(method = "forEachIntersects(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)V", at = @At("TAIL"))
//    private void onForEachIntersects4(CallbackInfo ci) {
//        concurrentModificationCheck--;
//        if (concurrentModificationCheck != 0) {
//            throw new RuntimeException("Concurrent modification check failed, ConcurrentModificationCheck value:" + concurrentModificationCheck);
//        }
//    }
}
