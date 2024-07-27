package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.CuboidBlockIterator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.CollisionView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static ua.zefir.zefiroptimizations.Commands.*;

@Mixin(CollisionView.class)
public interface CollisionViewMixin {
    @Inject(method = "findSupportingBlockPos", at = @At(value = "HEAD"), cancellable = true)
    private void findSupportingBlockPos(Entity entity, Box box, CallbackInfoReturnable<Optional<BlockPos>> cir){
        if(collisionViewOptimization) {
            cir.setReturnValue(this.customFindSupportingBlockPos(entity, box));
        }
    }

    @Unique
    default Optional<BlockPos> customFindSupportingBlockPos(Entity entity, Box box) {
        CollisionView self = (CollisionView) this;

        // Use primitive double instead of wrapper class for performance
        double closestDistanceSq = Double.MAX_VALUE;
        BlockPos closestBlockPos = null;

        // Precompute entity position for efficiency
        Vec3d entityPos = entity.getPos();

        // Iterate directly over BlockPos instead of using Spliterator for potential performance gain
        // Assuming BlockCollisionSpliterator provides a way to access the iteration bounds
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.ceil(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.ceil(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.ceil(box.maxZ);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos currentBlockPos = new BlockPos(x, y, z);

                    // Directly check if block exists at the position for early rejection
                    if (self.getBlockState(currentBlockPos).isAir()) {
                        continue;
                    }

                    // Calculate squared distance directly for optimization
                    double currentDistanceSq = currentBlockPos.getSquaredDistance(entityPos);

                    // Update closest block only if distance is smaller or equal and z-coordinate is lower
                    // This prioritizes lower blocks when distances are equal
                    if (currentDistanceSq < closestDistanceSq ||
                            (currentDistanceSq == closestDistanceSq && currentBlockPos.getZ() < closestBlockPos.getZ())) {
                        closestBlockPos = currentBlockPos;
                        closestDistanceSq = currentDistanceSq;
                    }
                }
            }
        }

        return Optional.ofNullable(closestBlockPos);
    }
}
