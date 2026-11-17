package ua.zefir.zefiroptimizations.process.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ua.zefir.zefiroptimizations.process.Processes;
import ua.zefir.zefiroptimizations.process.world.WorldSeq;

@Mixin(PoiManager.class)
public abstract class PoiManagerMixin {
  @WrapOperation(
      method = {"tick", "sectionsToVillage"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/ai/village/poi/PoiManager$DistanceTracker;runAllUpdates()V"))
  private void zo$updateDistancesOnServerThread(
      PoiManager.DistanceTracker tracker, Operation<Void> original) {
    if (Processes.inProcess()) {
      return;
    }
    if (!Processes.onMain()) {
      original.call(tracker);
      return;
    }
    WorldSeq.write(() -> original.call(tracker));
  }

  @WrapOperation(
      method = "sectionsToVillage",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/ai/village/poi/PoiManager$DistanceTracker;getLevel(J)I"))
  private int zo$readDistanceConsistently(
      PoiManager.DistanceTracker tracker, long section, Operation<Integer> original) {
    if (!Processes.inProcess()) {
      return original.call(tracker, section);
    }
    return WorldSeq.read(() -> original.call(tracker, section));
  }
}
