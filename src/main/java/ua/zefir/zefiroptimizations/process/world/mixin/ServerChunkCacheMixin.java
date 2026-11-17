package ua.zefir.zefiroptimizations.process.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import ua.zefir.zefiroptimizations.process.Processes;
import ua.zefir.zefiroptimizations.process.world.ChunkCache;

@Mixin(value = ServerChunkCache.class, priority = ChunkCache.WRAPPER_PRIORITY)
public abstract class ServerChunkCacheMixin {
  @Shadow
  @Nullable
  private ChunkHolder getVisibleChunkIfPresent(long pos) {
    throw new AssertionError();
  }

  @WrapMethod(
      method =
          "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;")
  private @Nullable ChunkAccess zo$getChunk(
      int x, int z, ChunkStatus status, boolean create, Operation<ChunkAccess> original) {
    ChunkCache cache = ChunkCache.current();
    if (cache != null) {
      long pos = ChunkPos.pack(x, z);
      if (cache.get(this, pos, status) instanceof ChunkAccess cached) {
        return cached;
      }
      ChunkHolder holder = getVisibleChunkIfPresent(pos);
      if (holder != null) {
        ChunkAccess chunk = holder.getChunkIfPresent(status);
        if (chunk != null) {
          cache.put(this, pos, status, chunk);
          return chunk;
        }
      }
      if (!create && (holder == null || holder.getTicketLevel() > ChunkLevel.byStatus(status))) {
        return null;
      }
      return (ChunkAccess) Processes.callMain(self -> original.call(x, z, status, create), null);
    }
    return original.call(x, z, status, create);
  }

  @WrapMethod(method = "getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;")
  private @Nullable LevelChunk zo$getWorldChunk(int x, int z, Operation<LevelChunk> original) {
    ChunkCache cache = ChunkCache.current();
    if (cache != null) {
      long pos = ChunkPos.pack(x, z);
      if (cache.get(this, pos, ChunkStatus.FULL) instanceof LevelChunk cached) {
        return cached;
      }
      ChunkHolder holder = getVisibleChunkIfPresent(pos);
      if (holder != null
          && holder.getChunkIfPresent(ChunkStatus.FULL) instanceof LevelChunk chunk) {
        cache.put(this, pos, ChunkStatus.FULL, chunk);
        return chunk;
      }
      return null;
    }
    return original.call(x, z);
  }
}
