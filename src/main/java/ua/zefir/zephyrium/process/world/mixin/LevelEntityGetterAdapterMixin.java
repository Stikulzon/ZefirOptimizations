package ua.zefir.zephyrium.process.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.world.ScratchLists;
import ua.zefir.zephyrium.process.world.WorldSeq;

@Mixin(LevelEntityGetterAdapter.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class LevelEntityGetterAdapterMixin {
  @WrapMethod(method = "get(I)Lnet/minecraft/world/level/entity/EntityAccess;")
  private EntityAccess zo$get(int id, Operation<EntityAccess> original) {
    return Processes.inProcess() ? WorldSeq.read(() -> original.call(id)) : original.call(id);
  }

  @WrapMethod(method = "get(Ljava/util/UUID;)Lnet/minecraft/world/level/entity/EntityAccess;")
  private EntityAccess zo$get(UUID uuid, Operation<EntityAccess> original) {
    return Processes.inProcess() ? WorldSeq.read(() -> original.call(uuid)) : original.call(uuid);
  }

  @WrapMethod(method = "getAll")
  private Iterable zo$iterate(Operation<Iterable> original) {
    if (!Processes.inProcess()) {
      return original.call();
    }
    return WorldSeq.read(
        () -> {
          List<Object> copy = new ArrayList<>();
          for (Object entity : original.call()) {
            copy.add(entity);
          }
          return copy;
        });
  }

  @WrapMethod(
      method =
          "get(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/util/AbortableIterationConsumer;)V")
  private void zo$forEach(
      EntityTypeTest filter, AbortableIterationConsumer consumer, Operation<Void> original) {
    if (!Processes.inProcess()) {
      original.call(filter, consumer);
      return;
    }
    ArrayList<Object> found = ScratchLists.take();
    try {
      WorldSeq.read(
          () -> {
            found.clear();
            original.call(filter, zo$collector(found));
            return found;
          });
      zo$deliver(found, consumer);
    } finally {
      ScratchLists.release(found);
    }
  }

  @WrapMethod(method = "get(Lnet/minecraft/world/phys/AABB;Ljava/util/function/Consumer;)V")
  private void zo$forEachIntersects(AABB box, Consumer action, Operation<Void> original) {
    if (!Processes.inProcess()) {
      original.call(box, action);
      return;
    }
    ArrayList<Object> found = ScratchLists.take();
    try {
      WorldSeq.read(
          () -> {
            found.clear();
            original.call(box, (Consumer<Object>) found::add);
            return found;
          });
      for (int i = 0, n = found.size(); i < n; i++) {
        action.accept(found.get(i));
      }
    } finally {
      ScratchLists.release(found);
    }
  }

  @WrapMethod(
      method =
          "get(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V")
  private void zo$forEachIntersects(
      EntityTypeTest filter,
      AABB box,
      AbortableIterationConsumer consumer,
      Operation<Void> original) {
    if (!Processes.inProcess()) {
      original.call(filter, box, consumer);
      return;
    }
    ArrayList<Object> found = ScratchLists.take();
    try {
      WorldSeq.read(
          () -> {
            found.clear();
            original.call(filter, box, zo$collector(found));
            return found;
          });
      zo$deliver(found, consumer);
    } finally {
      ScratchLists.release(found);
    }
  }

  @Unique
  private static AbortableIterationConsumer<Object> zo$collector(List<Object> found) {
    return entity -> {
      found.add(entity);
      return AbortableIterationConsumer.Continuation.CONTINUE;
    };
  }

  @Unique
  private static void zo$deliver(List<Object> found, AbortableIterationConsumer consumer) {
    for (Object entity : found) {
      if (consumer.accept(entity).shouldAbort()) {
        return;
      }
    }
  }
}
