package ua.zefir.zefiroptimizations.actors;

import akka.actor.ActorRef;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.Vec3d;

public final class ZefirsActorMessages {
    public record AsyncTick() {}

    // Position Updates
    public record TickSingleEntity(LivingEntity entity) {}
    public record TickNewAiAndContinue(ActorRef requestingActor, LivingEntity entity) {}
    public record ContinueTickMovement() {}
    public record InvokeMove(MovementType movementType, Vec3d movement) {}

    // Damage
    public record ApplyDamage(LivingEntity entity, DamageSource source, float amount) {}
    public record LootItemEntity(IAsyncLivingEntityAccess iAsyncLivingEntityAccess, ItemEntity itemEntity) {}

    // Entity Lifecycle
    public record EntityCreated(LivingEntity entity) {}
    public record EntityRemoved(LivingEntity entity) {}

    // Syc
    public record ApplyPositionAndRotationDiff(LivingEntity entity, EntityActor.EntitySnapshot snapshot) {}
}