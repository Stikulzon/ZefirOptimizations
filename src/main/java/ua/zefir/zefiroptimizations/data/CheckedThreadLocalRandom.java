package ua.zefir.zefiroptimizations.data;

import net.minecraft.util.math.random.LocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

public class CheckedThreadLocalRandom extends LocalRandom {

    private static final ThreadLocal<LocalRandom> FALLBACK = ThreadLocal.withInitial(() -> new LocalRandom(new Random().nextLong()));


    private final Supplier<Thread> owner;

    public CheckedThreadLocalRandom(long seed, Supplier<Thread> owner) {
        super(seed);
        this.owner = Objects.requireNonNull(owner);
    }

    private boolean isSafe() {
        Thread owner = this.owner != null ? this.owner.get() : null;
        boolean notOwner = owner != null && Thread.currentThread() != owner;
        if (notOwner) {
            handleNotOwner();
            return false;
        } else {
            return true;
        }
    }

    private void handleNotOwner() {

    }

    @Override
    public void setSeed(long seed) {
        if (isSafe()) {
            super.setSeed(seed);
        } else {
            FALLBACK.get().setSeed(seed);
        }
    }

    @Override
    public int next(int bits) {
        if (isSafe()) {
            return super.next(bits);
        } else {
            return FALLBACK.get().next(bits);
        }
    }
}
