package ua.zefir.zefiroptimizations.process.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zefiroptimizations.process.Processes;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
  @Inject(method = "doTick", at = @At("TAIL"))
  private void zo$publishSnapshot(CallbackInfo ci) {
    Processes.republish((Entity) (Object) this);
  }
}
