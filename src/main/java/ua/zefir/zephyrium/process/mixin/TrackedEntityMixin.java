package ua.zefir.zephyrium.process.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin {
  @ModifyExpressionValue(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target = "Lcom/google/common/collect/Sets;newIdentityHashSet()Ljava/util/Set;"))
  private Set<Object> zo$concurrentListeners(Set<Object> original) {
    return Collections.newSetFromMap(new ConcurrentHashMap<>());
  }
}
