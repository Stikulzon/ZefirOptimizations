package ua.zefir.zefiroptimizations.process.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zefiroptimizations.process.Processes;

@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EntityCallbacksMixin {
  @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
  private void zo$startTracking(Entity entity, CallbackInfo ci) {
    Processes.onStartTracking(entity);
  }

  @Inject(method = "onTickingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
  private void zo$startTicking(Entity entity, CallbackInfo ci) {
    Processes.onStartTicking(entity);
  }

  @Inject(method = "onTickingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
  private void zo$stopTicking(Entity entity, CallbackInfo ci) {
    Processes.onStopTicking(entity);
  }

  @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
  private void zo$stopTracking(Entity entity, CallbackInfo ci) {
    Processes.onStopTracking(entity);
  }
}
