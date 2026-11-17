package ua.zefir.zephyrium.process.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zephyrium.process.TickCatchUp;
import ua.zefir.zephyrium.process.world.MainQueue;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
  @Unique private boolean zo$betweenTicks;

  @Shadow private long nextTickTimeNanos;

  @Inject(method = "tickServer", at = @At("HEAD"))
  private void zo$dropLostTicks(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
    nextTickTimeNanos = TickCatchUp.nextTickTime(Util.getNanos(), nextTickTimeNanos);
  }

  @WrapMethod(method = "waitUntilNextTick")
  private void zo$runProcessTasksBetweenTicks(Operation<Void> original) {
    zo$betweenTicks = true;
    MainQueue.drain();
    try {
      original.call();
    } finally {
      zo$betweenTicks = false;
    }
  }

  @WrapMethod(method = "pollTask")
  private boolean zo$runProcessTask(Operation<Boolean> original) {
    if (zo$betweenTicks && MainQueue.runOne()) {
      return true;
    }
    return original.call();
  }
}
