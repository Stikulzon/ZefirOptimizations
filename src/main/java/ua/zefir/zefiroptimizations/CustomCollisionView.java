package ua.zefir.zefiroptimizations;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.CollisionView;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomCollisionView {
    Optional<BlockPos> customFindSupportingBlockPos(Entity entity, Box box) {
        CollisionView self = (CollisionView) this;

        BlockPos closestBlockPos = null;
        double closestDistance = Double.MAX_VALUE;

        // Precompute all block positions in the given box and store them in a list
        List<BlockPos> blockPositions = new ArrayList<>();
        BlockCollisionSpliterator<BlockPos> blockCollisionSpliterator = new BlockCollisionSpliterator<>(self, entity, box, false, (pos, voxelShape) -> pos);
        blockCollisionSpliterator.forEachRemaining(blockPositions::add);

        Vec3d entityPos = entity.getPos(); // Cache entity position

        for (BlockPos currentBlockPos : blockPositions) {
            double currentDistance = currentBlockPos.getSquaredDistance(entityPos);

            if (currentDistance < closestDistance || (currentDistance == closestDistance && (closestBlockPos == null || closestBlockPos.compareTo(currentBlockPos) < 0))) {
                closestBlockPos = currentBlockPos;
                closestDistance = currentDistance;
            }
        }

        return Optional.ofNullable(closestBlockPos);
    }
}
