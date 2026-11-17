package ua.zefir.zephyrium.process.brain.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zephyrium.process.brain.MemoryCasts;

@Mixin(BehaviorUtils.class)
public abstract class BehaviorUtilsMixin {
  @Inject(method = "lookAtEntity", at = @At("HEAD"), cancellable = true)
  private static void zo$castLook(LivingEntity entity, LivingEntity target, CallbackInfo ci) {
    if (MemoryCasts.castTo(
        entity,
        self -> {
          BehaviorUtils.lookAtEntity((LivingEntity) self, target);
          return null;
        })) {
      ci.cancel();
    }
  }

  @Inject(
      method =
          "setWalkAndLookTargetMemories(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/behavior/PositionTracker;FI)V",
      at = @At("HEAD"),
      cancellable = true)
  private static void zo$castWalkAndLook(
      LivingEntity entity,
      PositionTracker target,
      float speedModifier,
      int closeEnoughDistance,
      CallbackInfo ci) {
    if (MemoryCasts.castTo(
        entity,
        self -> {
          BehaviorUtils.setWalkAndLookTargetMemories(
              (LivingEntity) self, target, speedModifier, closeEnoughDistance);
          return null;
        })) {
      ci.cancel();
    }
  }
}
