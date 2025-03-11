package ua.zefir.zefiroptimizations.mixin;

import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.entity.SectionedEntityCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionedEntityCache.class)
public class SectionedEntityCacheMixin {
//    @Unique
//    public int concurrentModificationCheck = 0;
//
//    @Inject(method = "forEachInBox", at = @At("HEAD"))
//    private void onForEachInBox(CallbackInfo ci) {
//        if (concurrentModificationCheck != 0) {
//            throw new RuntimeException("Concurrent modification check failed, ConcurrentModificationCheck value:" + concurrentModificationCheck);
//        }
//    }
//
//    @Inject(method = "forEachInBox", at = @At("RETURN"))
//    private void onForEachInBox2(CallbackInfo ci) {
//        concurrentModificationCheck--;
//        if (concurrentModificationCheck != 0) {
//            throw new RuntimeException("Concurrent modification check failed, ConcurrentModificationCheck value:" + concurrentModificationCheck);
//        }
//    }
//
//    @Inject(method = "getSections(II)Lit/unimi/dsi/fastutil/longs/LongSortedSet;", at = @At("HEAD"))
//    private void onGetSections(int chunkX, int chunkZ, CallbackInfoReturnable<LongSortedSet> cir) {
//        concurrentModificationCheck++;
//    }
//
//    @Inject(method = "getSections(II)Lit/unimi/dsi/fastutil/longs/LongSortedSet;", at = @At("RETURN"))
//    private void onGetSections2(int chunkX, int chunkZ, CallbackInfoReturnable<LongSortedSet> cir) {
//        concurrentModificationCheck--;
//        if (concurrentModificationCheck != 0) {
//            throw new RuntimeException("Concurrent modification check failed, ConcurrentModificationCheck value:" + concurrentModificationCheck);
//        }
//    }
}
