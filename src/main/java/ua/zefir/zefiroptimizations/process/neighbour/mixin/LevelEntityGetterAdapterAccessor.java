package ua.zefir.zefiroptimizations.process.neighbour.mixin;

import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelEntityGetterAdapter.class)
public interface LevelEntityGetterAdapterAccessor {
  @Accessor
  EntitySectionStorage<?> getSectionStorage();
}
