package ua.zefir.zephyrium.process.push.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ua.zefir.zephyrium.process.ProcessEntity;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.push.Impulse;
import ua.zefir.zephyrium.process.push.VanillaPush;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
  @WrapOperation(
      method = "doPush",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;push(Lnet/minecraft/world/entity/Entity;)V"))
  private void zo$pushAway(Entity other, Entity pusher, Operation<Void> original) {
    LivingEntity self = (LivingEntity) (Object) this;
    if (!Processes.inProcess()
        || ((ProcessEntity) other).zo$owner() == Thread.currentThread()
        || !VanillaPush.is(other)) {
      original.call(other, pusher);
      return;
    }
    if (other instanceof LivingEntity living && living.isSleeping()) {
      return;
    }
    if (self.isPassengerOfSameVehicle(other) || self.noPhysics || other.noPhysics) {
      return;
    }
    double d = self.getX() - other.getX();
    double e = self.getZ() - other.getZ();
    double f = Mth.absMax(d, e);
    if (f < VanillaPush.MIN_DISTANCE) {
      return;
    }
    f = Math.sqrt(f);
    d /= f;
    e /= f;
    double g = 1.0 / f;
    if (g > 1.0) {
      g = 1.0;
    }
    d *= g;
    e *= g;
    d *= VanillaPush.STRENGTH;
    e *= VanillaPush.STRENGTH;
    if (!other.isVehicle() && other.isPushable() && !Impulse.add(other, -d, 0.0, -e)) {
      other.push(-d, 0.0, -e);
    }
    if (!self.isVehicle() && self.isPushable()) {
      self.push(d, 0.0, e);
    }
  }
}
