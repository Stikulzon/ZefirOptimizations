package ua.zefir.zephyrium.process.neighbour.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ua.zefir.zephyrium.process.neighbour.NeighbourViews;

@Mixin(NearestLivingEntitySensor.class)
public abstract class NearestLivingEntitySensorMixin {
  @WrapOperation(
      method = "doTick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
  private <E> List<E> zo$readNeighbourView(
      ServerLevel level,
      Class<E> type,
      AABB box,
      Predicate<? super E> filter,
      Operation<List<E>> original,
      ServerLevel world,
      LivingEntity viewer) {
    if (type != LivingEntity.class) {
      return original.call(level, type, box, filter);
    }
    @SuppressWarnings("unchecked")
    List<E> view = (List<E>) NeighbourViews.nearestLiving(level, viewer, box);
    return view != null ? view : original.call(level, type, box, filter);
  }
}
