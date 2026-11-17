package ua.zefir.zefiroptimizations.process.neighbour.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Comparator;
import java.util.function.ToDoubleFunction;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.entity.ai.sensing.PlayerSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ua.zefir.zefiroptimizations.process.Processes;
import ua.zefir.zefiroptimizations.process.neighbour.StableComparators;

@Mixin({NearestLivingEntitySensor.class, NearestItemSensor.class, PlayerSensor.class})
public abstract class SensorSortMixin {
  @WrapOperation(
      method = "doTick*",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ljava/util/Comparator;comparingDouble(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;"))
  private static <T> Comparator<T> zo$sortByStableDistance(
      ToDoubleFunction<? super T> key, Operation<Comparator<T>> original) {
    if (!Processes.inProcess()) {
      return original.call(key);
    }
    return StableComparators.comparingDouble(key);
  }
}
