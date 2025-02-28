package ua.zefir.zefiroptimizations.mixin.clonable;

import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import ua.zefir.zefiroptimizations.data.BoxAccessor;

@Mixin(Box.class)
public class BoxMixin implements Cloneable, BoxAccessor {
    @Override
    public Box clone() {
        try {
            return (Box) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
