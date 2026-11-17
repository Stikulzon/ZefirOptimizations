package ua.zefir.zefiroptimizations.process.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import ua.zefir.zefiroptimizations.process.Processes;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
  @WrapMethod(method = "tryToMerge(Lnet/minecraft/world/entity/item/ItemEntity;)V")
  private void zo$tryMerge(ItemEntity other, Operation<Void> original) {
    ItemEntity self = (ItemEntity) (Object) this;
    Processes.atomically(
        other,
        () -> {
          if (!self.isRemoved() && !other.isRemoved()) {
            original.call(other);
          }
        });
  }
}
