package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.EntityTrackingStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

@Mixin(EntityTrackingSection.class)
public class EntityTrackingSectionMixin {

    @Unique
    private Thread thread;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(Class entityClass, EntityTrackingStatus status, CallbackInfo ci) {
//        if(Thread.currentThread() == ZefirOptimizations.SERVER.getThread()){
        thread = Thread.currentThread();
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
