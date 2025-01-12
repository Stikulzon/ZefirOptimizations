package ua.zefir.zefiroptimizations.mixin;

import akka.util.Timeout;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.concurrent.duration.Duration;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Getter
@Mixin(Entity.class)
public abstract class EntityMixin {

//    @Unique
//    Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void onSetRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread() && !(self instanceof PlayerEntity)) {
            ZefirOptimizations.SERVER.execute(() -> {
                self.setRemoved(reason);
            });
            ci.cancel();
        }
    }

//    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
//    private void onMove(MovementType movementType, Vec3d movement, CallbackInfo ci) {
//        Entity self = (Entity) (Object) this;
//
//        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread() && !(self instanceof PlayerEntity)) {
//            ZefirOptimizations.SERVER.execute(() -> {
//                self.move(movementType, movement);
//            });
//            ci.cancel();
//        }
//    }

//    @Inject(method = "findCollisionsForMovement", at = @At("HEAD"), cancellable = true)
//    private static void onFindCollisionsForMovement(@Nullable Entity entity, World world, List<VoxelShape> regularCollisions, Box movingEntityBoundingBox, CallbackInfoReturnable<List<VoxelShape>> cir) {
//        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
////            System.out.println("Adjusting movement for collisions");
//            ZefirOptimizations.SERVER.execute(() -> {
//                cir.setReturnValue(EntityAccessor.invokeFindCollisionsForMovement(entity, world, regularCollisions, movingEntityBoundingBox));
//            });
//        }
//    }

//    @Inject(method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
//    private void onAdjustMovementForCollisions(Vec3d movement, CallbackInfoReturnable<Vec3d> cir) {
//            System.out.println("onAdjustMovementForCollisions");
//    }

//    @Inject(method = "adjustMovementForCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Lnet/minecraft/world/World;Ljava/util/List;)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
//    private static void onAdjustMovementForCollisions(@Nullable Entity entity, Vec3d movement, Box entityBoundingBox, World world, List<VoxelShape> collisions, CallbackInfoReturnable<Vec3d> cir) {
//        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
////            System.out.println("Adjusting movement!111");
//            ZefirOptimizations.SERVER.execute(() -> {
//                cir.setReturnValue(Entity.adjustMovementForCollisions(entity, movement, entityBoundingBox, world, collisions));
//            });
//        }
//        System.out.println("Adjusting movement on the main thread");
//    }
//
    @Inject(method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void onAdjustMovementForCollisions1(Vec3d movement, CallbackInfoReturnable<Vec3d> cir) {
        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
            ZefirOptimizations.SERVER.execute(() -> {
                cir.setReturnValue( ( (EntityAccessor) this).invokeAdjustMovementForCollisions(movement) );
            });
        }
    }
}
