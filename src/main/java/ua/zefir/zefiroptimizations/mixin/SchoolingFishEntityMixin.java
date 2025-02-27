package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.passive.SchoolingFishEntity;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SchoolingFishEntity.class)
public class SchoolingFishEntityMixin {

    @Redirect(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/random/Random;nextInt(I)I",
                    ordinal = 0
            )
    )
    private int modifyIntI(Random instance, int i) {
        Random random = Random.create();
        return random.nextInt(200);
    }
}
