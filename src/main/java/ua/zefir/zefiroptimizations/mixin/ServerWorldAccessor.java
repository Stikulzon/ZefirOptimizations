package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerWorld.class)
public interface ServerWorldAccessor {
    @Invoker("getEntityLookup")
    EntityLookup<Entity> invokeGetEntityLookup();

    @Invoker("tickTime")
    void invokeTickTime();

    @Invoker("getLightningPos")
    BlockPos invokeGetLightningPos(BlockPos pos);
}
