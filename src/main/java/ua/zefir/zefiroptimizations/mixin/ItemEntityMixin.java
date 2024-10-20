package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.Ownable;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity implements Ownable {
    public ItemEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Redirect(method = "<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/random/Random;nextDouble()D",
                    ordinal = 0)
    )
    private static double modifyVelocityX(Random instance) {
        Random random = Random.create();
        return random.nextDouble();
    }

    @Redirect(method = "<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/random/Random;nextDouble()D",
                    ordinal = 1)
    )
    private static double modifyVelocityY(Random instance) {
        Random random = Random.create();
        return random.nextDouble();
    }
}
