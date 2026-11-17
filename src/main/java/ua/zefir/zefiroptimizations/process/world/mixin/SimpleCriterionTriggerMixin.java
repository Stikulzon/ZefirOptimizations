package ua.zefir.zefiroptimizations.process.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Predicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import ua.zefir.zefiroptimizations.process.Processes;

@Mixin(SimpleCriterionTrigger.class)
public abstract class SimpleCriterionTriggerMixin {
  @WrapMethod(method = "trigger")
  @SuppressWarnings({"rawtypes"})
  private void zo$trigger(ServerPlayer player, Predicate predicate, Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(player, predicate));
      return;
    }
    original.call(player, predicate);
  }
}
