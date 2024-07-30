package ua.zefir.zefiroptimizations.threading;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.block.BlockState;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

public class WorldSnapshot {
    private final Long2ObjectMap<BlockState> blockStates; // ChunkPos.toLong() -> BlockState
    private final World world; // Store a reference to the world

    public WorldSnapshot(Entity entity, Vec3d movement, int radius) {
        this.world = entity.getWorld(); // Store the world reference
        // Calculate the bounding box of the area to capture
        Box expandedBox = entity.getBoundingBox().stretch(movement).expand(radius);

        this.blockStates = new Long2ObjectOpenHashMap<>();

        // Correct iteration over chunks:
        int minChunkX = ChunkSectionPos.getSectionCoord(MathHelper.floor(expandedBox.minX));
        int minChunkZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(expandedBox.minZ));
        int maxChunkX = ChunkSectionPos.getSectionCoord(MathHelper.floor(expandedBox.maxX));
        int maxChunkZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(expandedBox.maxZ));

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

                // Iterate over blocks within the chunk and expanded box
                BlockPos.iterate(chunkPos.getStartX(), world.getBottomY(), chunkPos.getStartZ(),
                                chunkPos.getEndX(), world.getTopY(), chunkPos.getEndZ())
                        .forEach(blockPos -> {
                            if (expandedBox.contains(Vec3d.ofCenter(blockPos))) {
                                BlockState state = entity.getWorld().getBlockState(blockPos);
                                this.blockStates.put(chunkPos.toLong(), state);
                            }
                        });
            }
        }
    }

    public BlockState getBlockState(BlockPos pos) {
        long chunkPos = new ChunkPos(pos).toLong();
        return this.blockStates.getOrDefault(chunkPos, Blocks.AIR.getDefaultState());
    }

    public VoxelShape getBlockCollisionShape(BlockPos pos) {
        long chunkPos = new ChunkPos(pos).toLong();
        if (this.blockStates.containsKey(chunkPos)) {
            BlockState state = this.blockStates.get(chunkPos);
            return state.getCollisionShape(this.world, pos, ShapeContext.absent());
        } else {
            // Block not in snapshot, fall back to world (for blocks outside the radius)
            return this.world.getBlockState(pos).getCollisionShape(this.world, pos, ShapeContext.absent());
        }
    }
}
