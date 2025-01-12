package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MobEntity.class)
public interface MobEntityAccessor {
    @Invoker("loot")
    void invokeLoot(ItemEntity itemEntity);
}
