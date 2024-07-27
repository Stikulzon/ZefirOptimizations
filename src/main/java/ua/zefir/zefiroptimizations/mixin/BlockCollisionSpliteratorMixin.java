package ua.zefir.zefiroptimizations.mixin;

import com.google.common.collect.AbstractIterator;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.CuboidBlockIterator;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.BlockView;
import net.minecraft.world.CollisionView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.BiFunction;

import static ua.zefir.zefiroptimizations.Commands.customComputeNextOptimization;

@Mixin(BlockCollisionSpliterator.class)
public abstract class BlockCollisionSpliteratorMixin<T> extends AbstractIterator<T> {
    @Final
    @Shadow
    private Box box;
    @Final
    @Shadow
    private ShapeContext context;
    @Final
    @Shadow
    private CuboidBlockIterator blockIterator;
    @Final
    @Shadow
    private BlockPos.Mutable pos;
    @Final
    @Shadow
    private VoxelShape boxShape;
    @Final
    @Shadow
    private CollisionView world;
    @Final
    @Shadow
    private boolean forEntity;
    @Final
    @Shadow
    private BiFunction<BlockPos.Mutable, VoxelShape, T> resultFunction;
    @Shadow
    protected abstract BlockView getChunk(int x, int z);

    @Inject(method = "computeNext", at = @At(value = "HEAD"), cancellable = true)
    private void findSupportingBlockPos(CallbackInfoReturnable<T> cir){
        if(customComputeNextOptimization) {
            cir.setReturnValue(this.customComputeNext());
        }
    }

    @Unique
    protected T customComputeNext() {
        // Pre-calculate bounding box values outside the loop
        double minX = this.box.minX;
        double minY = this.box.minY;
        double minZ = this.box.minZ;
        double maxX = this.box.maxX;
        double maxY = this.box.maxY;
        double maxZ = this.box.maxZ;

        while (this.blockIterator.step()) {
            int i = this.blockIterator.getX();
            int j = this.blockIterator.getY();
            int k = this.blockIterator.getZ();
            int l = this.blockIterator.getEdgeCoordinatesCount();

            // 2. Optimized Broad-Phase Culling: Direct comparison for bounding box intersection
            if (minX <= i + 1.0 && maxX >= i && minY <= j + 1.0 && maxY >= j && minZ <= k + 1.0 && maxZ >= k) {
                if (l != 3) {
                    // Reuse the 'pos' object instead of creating a new one each time
                    this.pos.set(i, j, k);
                    BlockView blockView = this.getChunk(i, k);

                    if (blockView != null) {
                        BlockState blockState = blockView.getBlockState(this.pos);

                        // 3. Combine conditions for better branch prediction
                        if (!this.forEntity && (l != 1 || blockState.exceedsCube()) &&
                                (l != 2 || blockState.isOf(Blocks.MOVING_PISTON)) &&
                                blockState.shouldSuffocate(blockView, this.pos)) {

                            VoxelShape voxelShape = blockState.getCollisionShape(this.world, this.pos, this.context);

                            if (voxelShape == VoxelShapes.fullCube()) {
                                // 4. Directly return the result (inline function call)
                                return this.resultFunction.apply(this.pos, voxelShape.offset(i, j, k));
                            } else {
                                VoxelShape voxelShape2 = voxelShape.offset(i, j, k);
                                // 5. Check isEmpty before VoxelShapes.matchesAnywhere
                                if (!voxelShape2.isEmpty() &&
                                        VoxelShapes.matchesAnywhere(voxelShape2, this.boxShape, BooleanBiFunction.AND)) {
                                    return this.resultFunction.apply(this.pos, voxelShape2);
                                }
                            }
                        }
                    }
                }
            }
        }
        return this.endOfData();
    }
}
