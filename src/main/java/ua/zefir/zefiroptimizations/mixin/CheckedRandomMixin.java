package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.util.math.random.GaussianGenerator;
import net.minecraft.world.entity.EntityTrackingStatus;
import org.spongepowered.asm.mixin.*;
import net.minecraft.util.math.random.CheckedRandom;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(CheckedRandom.class)
public class CheckedRandomMixin {

    @Final
    @Shadow
    private AtomicLong seed;
    @Unique
    private ThreadLocal<AtomicLong> threadLocalSeed;
    @Unique
    private ThreadLocal<GaussianGenerator> threadLocalGaussianGenerator;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/random/CheckedRandom;setSeed(J)V"))
    private void onInit(long seed, CallbackInfo ci) {
        threadLocalSeed = ThreadLocal.withInitial(AtomicLong::new);
        threadLocalGaussianGenerator = ThreadLocal.withInitial(() -> new GaussianGenerator((CheckedRandom) (Object) this));
    }

    @Overwrite
    public void setSeed(long seed) {
        this.threadLocalSeed.get().set((seed ^ 25214903917L) & 281474976710655L);
        this.threadLocalGaussianGenerator.get().reset();
    }

    @Overwrite
    public int next(int bits) {
        AtomicLong seed = this.threadLocalSeed.get();
        long l = seed.get();
        long m = l * 25214903917L + 11L & 281474976710655L;
        seed.set(m);
        return (int)(m >> 48 - bits);
    }

    @Overwrite
    public double nextGaussian() {
        return this.threadLocalGaussianGenerator.get().next();
    }


}
