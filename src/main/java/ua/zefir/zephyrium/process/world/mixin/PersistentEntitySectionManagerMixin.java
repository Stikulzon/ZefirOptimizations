package ua.zefir.zephyrium.process.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.world.MainQueue;
import ua.zefir.zephyrium.process.world.WorldSeq;

@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin {
  @WrapMethod(method = "tick")
  private void zo$tick(Operation<Void> original) {
    zo$writeVoid(() -> original.call());
  }

  @WrapMethod(method = "addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z")
  private boolean zo$addEntity(EntityAccess entity, boolean existing, Operation<Boolean> original) {
    return zo$write(() -> original.call(entity, existing));
  }

  @WrapMethod(
      method =
          "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V")
  private void zo$updateTrackingStatus(
      ChunkPos chunkPos, Visibility status, Operation<Void> original) {
    zo$writeVoid(() -> original.call(chunkPos, status));
  }

  @WrapMethod(method = "autoSave")
  private void zo$save(Operation<Void> original) {
    zo$writeVoid(() -> original.call());
  }

  @WrapMethod(method = "saveAll")
  private void zo$flush(Operation<Void> original) {
    if (Processes.onMain()) {
      MainQueue.drain();
    }
    zo$writeVoid(() -> original.call());
  }

  @WrapMethod(method = "close")
  private void zo$close(Operation<Void> original) {
    zo$writeVoid(() -> original.call());
  }

  @WrapMethod(method = "isLoaded")
  private boolean zo$has(UUID uuid, Operation<Boolean> original) {
    return zo$read(() -> original.call(uuid));
  }

  @WrapMethod(method = "canPositionTick(Lnet/minecraft/core/BlockPos;)Z")
  private boolean zo$shouldTick(BlockPos pos, Operation<Boolean> original) {
    return zo$read(() -> original.call(pos));
  }

  @WrapMethod(method = "canPositionTick(Lnet/minecraft/world/level/ChunkPos;)Z")
  private boolean zo$shouldTick(ChunkPos pos, Operation<Boolean> original) {
    return zo$read(() -> original.call(pos));
  }

  @WrapMethod(method = "areEntitiesLoaded")
  private boolean zo$isLoaded(long chunkPos, Operation<Boolean> original) {
    return zo$read(() -> original.call(chunkPos));
  }

  @Unique
  private static void zo$writeVoid(Runnable mutation) {
    zo$write(
        () -> {
          mutation.run();
          return null;
        });
  }

  @SuppressWarnings("unchecked")
  @Unique
  private static <T> T zo$write(Supplier<T> mutation) {
    if (Processes.inProcess()) {
      return (T) Processes.callMain(self -> zo$writeOnMain(mutation), null);
    }
    if (!Processes.onMain()) {
      Processes.reportForeignWrite("entity manager");
      return mutation.get();
    }
    return zo$writeOnMain(mutation);
  }

  @Unique
  private static <T> T zo$writeOnMain(Supplier<T> mutation) {
    WorldSeq.beginWrite();
    try {
      return mutation.get();
    } finally {
      WorldSeq.endWrite();
    }
  }

  @Unique
  private static <T> T zo$read(Supplier<T> read) {
    return Processes.inProcess() ? WorldSeq.read(read) : read.get();
  }
}
