package ua.zefir.zefiroptimizations.process.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import ua.zefir.zefiroptimizations.process.EntityProcess;
import ua.zefir.zefiroptimizations.process.Processes;

@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
  @Shadow @Final private Entity entity;

  @WrapMethod(method = "sendChanges")
  private void zo$tick(Operation<Void> original) {
    EntityProcess process = Processes.processOf(entity);
    if (process != null && Processes.onMain()) {
      process.deferTrackerTick(() -> original.call());
      return;
    }
    original.call();
  }
}
