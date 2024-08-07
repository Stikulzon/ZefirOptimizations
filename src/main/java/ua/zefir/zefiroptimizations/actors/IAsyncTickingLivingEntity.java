package ua.zefir.zefiroptimizations.actors;

import net.minecraft.util.math.Vec3d;

public interface IAsyncTickingLivingEntity {
    boolean zefiroptimizations$isAsyncTicking();
    void zefiroptimizations$setAsyncTicking(boolean asyncTicking);
    void tickAsync();
    Vec3d getSynchronizedPosition();
}
