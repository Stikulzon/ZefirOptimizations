package ua.zefir.zefiroptimizations.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.annotation.Debug;
import net.minecraft.util.collection.TypeFilterableList;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.EntityTrackingStatus;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

@Mixin(EntityTrackingSection.class)
public abstract class EntityTrackingSectionMixin<T extends EntityLike> {
//    @Shadow
//    @Final
//    @Mutable
//    private net.minecraft.util.collection.TypeFilterableList<T> collection;
    @Unique
    private CopyOnWriteArrayList<T> collection;
    @Unique
    private AtomicReference<EntityTrackingStatus> status;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(Class entityClass, EntityTrackingStatus initialStatus, CallbackInfo ci) {
        this.status = new AtomicReference<>(initialStatus);
        this.collection = new CopyOnWriteArrayList<>();
    }

    @Overwrite
    public void add(T entity) {
        this.collection.add(entity);  // CopyOnWriteArrayList handles thread-safe addition
    }

    @Overwrite
    public boolean remove(T entity) {
        return this.collection.remove(entity);  // CopyOnWriteArrayList handles thread-safe removal
    }

    @Overwrite
    public LazyIterationConsumer.NextIteration forEach(Box box, LazyIterationConsumer<T> consumer) {
        for (T entityLike : this.collection) {
            if (entityLike.getBoundingBox().intersects(box) && consumer.accept(entityLike).shouldAbort()) {
                return LazyIterationConsumer.NextIteration.ABORT;
            }
        }
        return LazyIterationConsumer.NextIteration.CONTINUE;
    }

    @Overwrite
    public <U extends T> LazyIterationConsumer.NextIteration forEach(TypeFilter<T, U> type, Box box, LazyIterationConsumer<? super U> consumer) {
        for (T entityLike : this.collection) {
            U entityLike2 = type.downcast(entityLike);
            if (entityLike2 != null && entityLike.getBoundingBox().intersects(box) && consumer.accept(entityLike2).shouldAbort()) {
                return LazyIterationConsumer.NextIteration.ABORT;
            }
        }
        return LazyIterationConsumer.NextIteration.CONTINUE;
    }

    @Overwrite
    public boolean isEmpty() {
        return this.collection.isEmpty();  // CopyOnWriteArrayList handles thread-safe check
    }

    @Overwrite
    public Stream<T> stream() {
        return this.collection.stream();  // CopyOnWriteArrayList handles thread-safe stream
    }

    @Overwrite
    public EntityTrackingStatus getStatus() {
        return this.status.get();  // AtomicReference provides thread-safe access
    }

    @Overwrite
    public EntityTrackingStatus swapStatus(EntityTrackingStatus newStatus) {
        return this.status.getAndSet(newStatus);  // AtomicReference provides atomic swap operation
    }

    @Overwrite
    @Debug
    public int size() {
        return this.collection.size();  // CopyOnWriteArrayList handles thread-safe size check
    }


//    @Shadow
//    public abstract LazyIterationConsumer.NextIteration forEach(Box box, LazyIterationConsumer<T> consumer);

//    @Shadow
//    public abstract <U extends T> LazyIterationConsumer.NextIteration forEach(
//            TypeFilter<T, U> type, Box box, LazyIterationConsumer<? super U> consumer
//    );

//    @Unique
//    private final ReentrantReadWriteLock collectionLock = new ReentrantReadWriteLock();
//
//    @Inject(method = "add", at = @At("HEAD"))
//    private void zefiroptimizations$add(T entity, CallbackInfo ci) {
//        collectionLock.writeLock().lock();
//    }
//
//    @Inject(method = "add", at = @At("TAIL"))
//    private void zefiroptimizations$addUnlock(T entity, CallbackInfo ci) {
//        collectionLock.writeLock().unlock();
//    }
//
//    @Inject(method = "remove", at = @At("HEAD"))
//    private void zefiroptimizations$removeHead(T entity, CallbackInfoReturnable<Boolean> cir) {
//        collectionLock.writeLock().lock();
//    }
//
//    @ModifyReturnValue(method = "remove", at = @At("RETURN"))
//    private boolean zefiroptimizations$removeReturn(boolean original) {
//        try{
//            return original;
//        } finally {
//            collectionLock.writeLock().unlock();
//        }
//    }
//
//    @Inject(method = "forEach(Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)Lnet/minecraft/util/function/LazyIterationConsumer$NextIteration;", at = @At("HEAD"))
//    private void zefiroptimizations$forEachLock(Box box, LazyIterationConsumer<T> consumer, CallbackInfoReturnable<LazyIterationConsumer.NextIteration> cir) {
//        collectionLock.readLock().lock();
//    }
//
//    @ModifyReturnValue(method = "forEach(Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)Lnet/minecraft/util/function/LazyIterationConsumer$NextIteration;", at = @At("RETURN"))
//    private LazyIterationConsumer.NextIteration zefiroptimizations$forEachUnlock(LazyIterationConsumer.NextIteration original) {
//        try{
//            return original;
//        } finally {
//            collectionLock.readLock().unlock();
//        }
//    }
//
//    @Inject(method = "forEach(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)Lnet/minecraft/util/function/LazyIterationConsumer$NextIteration;", at = @At("HEAD"))
//    private <U extends T> void zefiroptimizations$forEachTypeLock(TypeFilter<T, U> type, Box box, LazyIterationConsumer<? super U> consumer, CallbackInfoReturnable<LazyIterationConsumer.NextIteration> cir) {
//        collectionLock.readLock().lock();
//    }
//
//    @ModifyReturnValue(method = "forEach(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)Lnet/minecraft/util/function/LazyIterationConsumer$NextIteration;", at = @At("RETURN"))
//    private <U extends T> LazyIterationConsumer.NextIteration zefiroptimizations$forEachTypeUnlock(LazyIterationConsumer.NextIteration original) {
//        try{
//            return original;
//        } finally {
//            collectionLock.readLock().unlock();
//        }
//    }
}
