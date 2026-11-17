package ua.zefir.zefiroptimizations.process.neighbour.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ua.zefir.zefiroptimizations.process.neighbour.NeighbourViews;
import ua.zefir.zefiroptimizations.process.neighbour.StableComparators;

@Mixin({NearestLivingEntitySensor.class, NearestItemSensor.class})
public abstract class SensorListSortMixin {
  @WrapOperation(
      method = "doTick*",
      at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"))
  private <T> void zo$sortByPrecomputedKeys(
      List<T> list, Comparator<? super T> comparator, Operation<Void> original) {
    if (NeighbourViews.isPresorted(list)) {
      return;
    }
    if (!StableComparators.sortIfKeyed(list, comparator)) {
      original.call(list, comparator);
    }
  }
}
