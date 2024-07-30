package ua.zefir.zefiroptimizations.threading;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

@Setter
@Getter
public class MovementPredictionRequest {
    private final Entity entity;
    private final Vec3d movement;
    private final WorldSnapshot snapshot;

    public MovementPredictionRequest(Entity entity, Vec3d movement, WorldSnapshot snapshot) {
        this.entity = entity;
        this.movement = movement;
        this.snapshot = snapshot;
    }
}
