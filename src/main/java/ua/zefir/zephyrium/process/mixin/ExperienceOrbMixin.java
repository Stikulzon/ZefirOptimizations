package ua.zefir.zephyrium.process.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import ua.zefir.zephyrium.process.Processes;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
  @WrapMethod(method = "merge(Lnet/minecraft/world/entity/ExperienceOrb;)V")
  private void zo$merge(ExperienceOrb other, Operation<Void> original) {
    ExperienceOrb self = (ExperienceOrb) (Object) this;
    Processes.atomically(
        other,
        () -> {
          if (!self.isRemoved() && !other.isRemoved()) {
            original.call(other);
          }
        });
  }
}
