package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.SpawnHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SpawnHelper.class)
public class SpawnHelperMixin {

    @Redirect(method = "getRandomPosInChunkSection",
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/random/Random;nextInt(I)I",
                    ordinal = 0
            )
    )
    private static int modifyIntI(Random instance, int i) {
        Random random = Random.create();
        return random.nextInt(16);
    }

    @Redirect(method = "getRandomPosInChunkSection",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/random/Random;nextInt(I)I",
                    ordinal = 1
            )
    )
    private static int modifyIntJ(Random instance, int i) {
        Random random = Random.create();
        return random.nextInt(16);
    }

    @Redirect(method = "getRandomPosInChunkSection",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/MathHelper;nextBetween(Lnet/minecraft/util/math/random/Random;II)I"
            )
    )
    private static int modifyIntL(Random random, int min, int max) {
        Random newRandom = Random.create();
        return MathHelper.nextBetween(newRandom, min, max);
    }
}
