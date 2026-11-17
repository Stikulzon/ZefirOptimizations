package ua.zefir.zephyrium.process.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import ua.zefir.zephyrium.process.Processes;

@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin {
  /*? if >=26.1 {*/
  @WrapMethod(method = "unpackChunk(Lnet/minecraft/world/level/ChunkPos;)V")
  /*?} else {*/
  /*@WrapMethod(method = "readColumn(Lnet/minecraft/world/level/ChunkPos;)V")
  *//*?}*/
  private void zo$loadOnServerThread(ChunkPos pos, Operation<Void> original) {
    if (!Processes.inProcess()) {
      original.call(pos);
      return;
    }
    Processes.callMain(
        self -> {
          original.call(pos);
          return null;
        },
        null);
  }
}
