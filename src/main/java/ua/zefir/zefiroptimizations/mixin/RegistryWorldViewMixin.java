package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import ua.zefir.zefiroptimizations.data.WeirdLockClass;

import java.util.List;
import java.util.Optional;

@Mixin(RegistryWorldView.class)
public interface RegistryWorldViewMixin extends EntityView, WorldView, ModifiableTestableWorld {


    @Override
    default <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
        return WorldView.super.getBlockEntity(pos, type);
    }

    @Overwrite
    default List<VoxelShape> getEntityCollisions(@Nullable Entity entity, Box box) {
        synchronized (WeirdLockClass.registryWorldViewLock) {
            return EntityView.super.getEntityCollisions(entity, box);
        }
    }

    @Override
    default boolean doesNotIntersectEntities(@Nullable Entity except, VoxelShape shape) {
        return EntityView.super.doesNotIntersectEntities(except, shape);
    }

    @Override
    default BlockPos getTopPosition(Heightmap.Type heightmap, BlockPos pos) {
        return WorldView.super.getTopPosition(heightmap, pos);
    }
}
