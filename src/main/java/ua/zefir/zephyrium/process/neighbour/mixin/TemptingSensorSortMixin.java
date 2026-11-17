package ua.zefir.zephyrium.process.neighbour.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Comparator;
import java.util.function.ToDoubleFunction;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.neighbour.StableComparators;

@Mixin(TemptingSensor.class)
public abstract class TemptingSensorSortMixin {
  @WrapOperation(
      method = "doTick*",
      require = 0,
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
