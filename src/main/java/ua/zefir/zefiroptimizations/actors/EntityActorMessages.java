package ua.zefir.zefiroptimizations.actors;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.Vec3d;

public final class EntityActorMessages {
    public record AsyncTick() {}

    // Position Updates
    public record UpdatePosition(LivingEntity entity, Vec3d newPosition) {}
    public record SyncPosition(LivingEntity entity, Vec3d newPosition) {}
    public record GetPosition() {}
    public record PositionResponse(Vec3d position) {}

    // Damage
    public record ApplyDamage(float amount, DamageSource source) {}

    // Entity Lifecycle
    public record EntityCreated(LivingEntity entity) {}
    public record EntityRemoved(LivingEntity entity) {}
}