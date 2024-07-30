package ua.zefir.zefiroptimizations.threading;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.Vec3d;

@Setter
@Getter
public class MovementPrediction {
    private final Vec3d position;
    private final Vec3d velocity;
    private final boolean needsCollisionRecheck;

    public MovementPrediction(Vec3d position, Vec3d velocity, boolean needsCollisionRecheck) {
        this.position = position;
        this.velocity = velocity;
        this.needsCollisionRecheck = needsCollisionRecheck;
    }

    public boolean getNeedsCollisionRecheck() {
        return needsCollisionRecheck;
    }
}
