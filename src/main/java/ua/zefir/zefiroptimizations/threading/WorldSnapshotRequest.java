package ua.zefir.zefiroptimizations.threading;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

@Getter
@Setter
public class WorldSnapshotRequest {
    private final Entity entity;
    private final Vec3d movement;

    public WorldSnapshotRequest(Entity entity, Vec3d movement) {
        this.entity = entity;
        this.movement = movement;
    }
}
