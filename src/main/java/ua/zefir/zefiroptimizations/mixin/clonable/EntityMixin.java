package ua.zefir.zefiroptimizations.mixin.clonable;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import ua.zefir.zefiroptimizations.data.EntityAccessor;

@Mixin(Entity.class)
public class EntityMixin implements Cloneable, EntityAccessor {
    @Override
    public Entity clone() {
        try {
            return (Entity) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
