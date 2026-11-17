package ua.zefir.zephyrium.process.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import ua.zefir.zephyrium.process.EntityProcess;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.world.WorldSeq;

@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
public abstract class PersistentEntitySectionManagerCallbackMixin {
  @Shadow @Final private EntityAccess entity;

  @Unique private boolean zo$detached;

  @WrapMethod(method = "onMove")
  private void zo$updateEntityPosition(Operation<Void> original) {
    Runnable move =
        () -> {
          if (!zo$detached) {
            original.call();
          }
        };
    EntityProcess process = EntityProcess.current();
    if (process != null) {
      process.deferPositionUpdate(this, move);
    } else if (Processes.onMain()) {
      WorldSeq.write(move);
    } else {
      Processes.castMain(() -> WorldSeq.write(move));
    }
  }

  @WrapMethod(method = "onRemove")
  private void zo$remove(Entity.RemovalReason reason, Operation<Void> original) {
    Runnable remove =
        () -> {
          if (zo$detached) {
            return;
          }
          zo$detached = true;
          original.call(reason);
        };
    EntityProcess process = EntityProcess.current();
    if (process != null) {
      process.deferRemoval(remove);
      entity.setLevelCallback(EntityInLevelCallback.NULL);
    } else if (Processes.onMain()) {
      WorldSeq.write(remove);
    } else {
      Processes.castMain(() -> WorldSeq.write(remove));
    }
  }
}
