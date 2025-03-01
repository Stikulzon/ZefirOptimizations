package ua.zefir.zefiroptimizations.mixin;

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

@Mixin(EntityTrackingSection.class)
public class EntityTrackingSectionMixin<T extends EntityLike> {

    @Shadow @Final private TypeFilterableList<T> collection;
    @Unique Class<T> entityClass;

//    @Unique
//    private Thread thread;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(Class<T> entityClass, EntityTrackingStatus status, CallbackInfo ci) {
        this.entityClass = entityClass;
//        if(Thread.currentThread() == ZefirOptimizations.SERVER.getThread()){
//        thread = Thread.currentThread();
//            System.out.println("EntityTrackingSection created on the thread: " + Thread.currentThread().getName());
//            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
//            for (StackTraceElement element : stackTrace) {
//                System.out.println(element);
//            }
//        }
    }

    @Inject(method = "forEach(Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)Lnet/minecraft/util/function/LazyIterationConsumer$NextIteration;", at = @At("HEAD"))
    private void onForEach(CallbackInfoReturnable<Boolean> cir) {
//        if(Thread.currentThread() != thread){
//            System.out.println("EntityTrackingSection access on the thread: " + Thread.currentThread().getName());
//            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
//            for (StackTraceElement element : stackTrace) {
//                System.out.println(element);
//            }
//        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public LazyIterationConsumer.NextIteration forEach(Box box, LazyIterationConsumer<T> consumer) {
        TypeFilterableList<T> copy = new TypeFilterableList<>(entityClass);
        copy.addAll(collection);
        for (T entityLike : copy) {
            if (entityLike.getBoundingBox().intersects(box) && consumer.accept(entityLike).shouldAbort()) {
                return LazyIterationConsumer.NextIteration.ABORT;
            }
        }

        return LazyIterationConsumer.NextIteration.CONTINUE;
    }

    @Inject(method = "forEach(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)Lnet/minecraft/util/function/LazyIterationConsumer$NextIteration;", at = @At("HEAD"))
    private void onForEach1(CallbackInfoReturnable<Boolean> cir) {
//        if(Thread.currentThread() != thread){
//            System.out.println("EntityTrackingSection access on the thread: " + Thread.currentThread().getName());
//            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
//            for (StackTraceElement element : stackTrace) {
//                System.out.println(element);
//            }
//        }
    }
}
