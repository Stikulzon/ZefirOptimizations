package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BubbleColumnBlock.class)
public class BubbleColumnBlockMixin {
    @Redirect(method = "onEntityCollision",
            at = @At(value = "INVOKE",
                    target = "net/minecraft/util/math/random/Random.nextDouble ()D",
                    ordinal = 0
            )
    )
    private double modifyDouble(Random instance) {
        Random random = Random.create();
        return random.nextDouble();
    }

    @Redirect(method = "onEntityCollision",
            at = @At(value = "INVOKE",
                    target = "net/minecraft/util/math/random/Random.nextDouble ()D",
                    ordinal = 1
            )
    )
    private double modifyDouble2(Random instance) {
        Random random = Random.create();
        return random.nextDouble();
    }
    @Redirect(method = "onEntityCollision",
            at = @At(value = "INVOKE",
                    target = "net/minecraft/util/math/random/Random.nextDouble ()D",
                    ordinal = 2
            )
    )
    private double modifyDouble3(Random instance) {
        Random random = Random.create();
        return random.nextDouble();
    }

    @Redirect(method = "onEntityCollision",
            at = @At(value = "INVOKE",
                    target = "net/minecraft/util/math/random/Random.nextDouble ()D",
                    ordinal = 3
            )
    )
    private double modifyDouble4(Random instance) {
        Random random = Random.create();
        return random.nextDouble();
    }
}
