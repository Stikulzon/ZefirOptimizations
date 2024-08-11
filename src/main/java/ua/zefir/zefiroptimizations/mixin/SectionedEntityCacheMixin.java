package ua.zefir.zefiroptimizations.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.EntityTrackingStatus;
import net.minecraft.world.entity.SectionedEntityCache;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.locks.ReentrantReadWriteLock;

@Mixin(SectionedEntityCache.class)
public abstract class SectionedEntityCacheMixin<T extends EntityLike> {
    @Shadow @Final private Long2ObjectMap<EntityTrackingSection<T>> trackingSections;
    @Unique
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(Class<T> entityClass, Long2ObjectFunction<EntityTrackingStatus> chunkStatusDiscriminator, CallbackInfo ci) {
        // You can initialize the lock here if needed
    }

    @Inject(method = "forEachInBox", at = @At("HEAD"))
    private void onForEachInBox(Box box, LazyIterationConsumer<EntityTrackingSection<T>> consumer, CallbackInfo ci) {
        lock.readLock().lock();
    }

    @Inject(method = "forEachInBox", at = @At("TAIL"))
    private void onForEachInBoxTail(Box box, LazyIterationConsumer<EntityTrackingSection<T>> consumer, CallbackInfo ci) {
        lock.readLock().unlock();
    }

    @Inject(method = "getSections(J)Ljava/util/stream/LongStream;", at = @At("HEAD"))
    private void onGetSections(long chunkPos, CallbackInfoReturnable<LongStream> cir) {
        lock.readLock().lock();
    }

    @ModifyReturnValue(method = "getSections(J)Ljava/util/stream/LongStream;", at = @At(value = "RETURN", ordinal = 0))
    private LongStream onGetSectionsReturn1(LongStream original) {
        try{
            return original;
        } finally {
            lock.readLock().unlock();
        }
    }

    @ModifyReturnValue(method = "getSections(J)Ljava/util/stream/LongStream;", at = @At(value = "RETURN", ordinal = 1))
    private LongStream onGetSectionsReturn2(LongStream original) {
        try{
            return original;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Inject(method = "getTrackingSections", at = @At("HEAD"))
    private void onGetTrackingSections(long chunkPos, CallbackInfoReturnable<Stream<EntityTrackingSection<T>>> cir) {
        lock.readLock().lock();
    }

    @ModifyReturnValue(method = "getTrackingSections", at = @At("RETURN"))
    private Stream<EntityTrackingSection<T>> onGetTrackingSectionsReturn(Stream<EntityTrackingSection<T>> original) {
        try{
            return original;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Inject(method = "getTrackingSection", at = @At("HEAD"))
    private void onGetTrackingSection(long sectionPos, CallbackInfoReturnable<EntityTrackingSection<T>> cir) {
        lock.writeLock().lock();
    }

    @ModifyReturnValue(method = "getTrackingSection", at = @At("RETURN"))
    private EntityTrackingSection<T> onGetTrackingSectionReturn(EntityTrackingSection<T> original) {
        try{
            return original;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Inject(method = "findTrackingSection", at = @At("HEAD"))
    private void onFindTrackingSection(long sectionPos, CallbackInfoReturnable<EntityTrackingSection<T>> cir) {
        lock.readLock().lock();
    }

    @ModifyReturnValue(method = "findTrackingSection", at = @At("RETURN"))
    private @Nullable EntityTrackingSection<T> onFindTrackingSectionReturn(@Nullable EntityTrackingSection<T> original) {
        try{
            return original;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Inject(method = "getChunkPositions", at = @At("HEAD"))
    private void onGetChunkPositions(CallbackInfoReturnable<LongSet> cir) {
        lock.readLock().lock();
    }

    @Inject(method = "getChunkPositions", at = @At("RETURN"))
    private void onGetChunkPositionsReturn(CallbackInfoReturnable<LongSet> cir) {
        lock.readLock().unlock();
    }

    @Inject(method = "forEachIntersects(Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)V", at = @At("HEAD"))
    private void onForEachIntersects(Box box, LazyIterationConsumer<T> consumer, CallbackInfo ci) {
        lock.readLock().lock();
    }

    @Inject(method = "forEachIntersects(Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)V", at = @At("TAIL"))
    private void onForEachIntersectsTail(Box box, LazyIterationConsumer<T> consumer, CallbackInfo ci) {
        lock.readLock().unlock();
    }

    @Inject(method = "forEachIntersects(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)V", at = @At("HEAD"))
    private <U extends T> void onForEachIntersectsTyped(TypeFilter<T, U> filter, Box box, LazyIterationConsumer<U> consumer, CallbackInfo ci) {
        lock.readLock().lock();
    }

    @Inject(method = "forEachIntersects(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)V", at = @At("TAIL"))
    private <U extends T> void onForEachIntersectsTypedTail(TypeFilter<T, U> filter, Box box, LazyIterationConsumer<U> consumer, CallbackInfo ci) {
        lock.readLock().unlock();
    }

    @Inject(method = "removeSection", at = @At("HEAD"))
    private void onRemoveSection(long sectionPos, CallbackInfo ci) {
        lock.writeLock().lock();
    }

    @Inject(method = "removeSection", at = @At("TAIL"))
    private void onRemoveSectionTail(long sectionPos, CallbackInfo ci) {
        lock.writeLock().unlock();
    }

    @Inject(method = "sectionCount", at = @At("HEAD"))
    private void onSectionCount(CallbackInfoReturnable<Integer> cir) {
        lock.readLock().lock();
    }

    @ModifyReturnValue(method = "sectionCount", at = @At("RETURN"))
    private int onSectionCountReturn(int original) {
        try{
            return original;
        } finally {
            lock.readLock().unlock();
        }
    }
}
