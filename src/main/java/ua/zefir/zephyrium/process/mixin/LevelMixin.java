package ua.zefir.zephyrium.process.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import ua.zefir.zephyrium.process.EntityProcess;
import ua.zefir.zephyrium.process.Processes;

@Mixin(Level.class)
public abstract class LevelMixin {
  @WrapMethod(
      method =
          "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
  private boolean zo$setBlockState(
      BlockPos pos, BlockState state, int flags, int maxUpdateDepth, Operation<Boolean> original) {
    if (Processes.inProcess()) {
      return (Boolean)
          Processes.callMain(self -> original.call(pos, state, flags, maxUpdateDepth), null);
    }
    return original.call(pos, state, flags, maxUpdateDepth);
  }

  @WrapMethod(
      method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z")
  private boolean zo$breakBlock(
      BlockPos pos,
      boolean drop,
      @Nullable Entity breakingEntity,
      int maxUpdateDepth,
      Operation<Boolean> original) {
    if (Processes.inProcess()) {
      return (Boolean)
          Processes.callMain(
              self -> original.call(pos, drop, breakingEntity, maxUpdateDepth), null);
    }
    return original.call(pos, drop, breakingEntity, maxUpdateDepth);
  }

  @WrapMethod(method = "getBlockEntity")
  private @Nullable BlockEntity zo$getBlockEntity(BlockPos pos, Operation<BlockEntity> original) {
    if (Processes.inProcess()) {
      return (BlockEntity) Processes.callMain(self -> original.call(pos), null);
    }
    return original.call(pos);
  }

  /*? if <1.21.2 {*/
  /*@WrapMethod(method = "getProfiler")
  private net.minecraft.util.profiling.ProfilerFiller zo$getProfiler(
      Operation<net.minecraft.util.profiling.ProfilerFiller> original) {
    return Processes.inProcess()
        ? net.minecraft.util.profiling.InactiveProfiler.INSTANCE
        : original.call();
  }
  *//*?}*/

  @WrapMethod(method = "getRandom")
  private RandomSource zo$getRandom(Operation<RandomSource> original) {
    EntityProcess process = EntityProcess.current();
    return process != null ? process.random() : original.call();
  }
}
