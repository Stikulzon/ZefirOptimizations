package ua.zefir.zefiroptimizations.actors;

import akka.actor.ActorRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ZefirsActorMessages {
    public record AsyncTick() {}

    // Position Updates
    public record TickSingleEntity(LivingEntity entity) {}
    public record BaseTickSingleEntity(LivingEntity entity) {}
    public record TickNewAiAndContinue(ActorRef requestingActor, LivingEntity entity) {}
    public record ContinueTickMovement() {}
    public record InvokeMove(MovementType movementType, Vec3d movement) {}

    // Damage
    public record ApplyDamage(LivingEntity entity, DamageSource source, float amount) {}
    public record LootItemEntity(LivingEntity entity, ItemEntity itemEntity) {}
    public record FindCollisionsForMovement(@Nullable Entity entity, World world, List<VoxelShape> regularCollisions, Box movingEntityBoundingBox) {}

    // Entity Lifecycle
    public record EntityCreated(LivingEntity entity) {}
    public record EntityRemoved(LivingEntity entity) {}

    // Requests
    public record RequestIsRemoved(boolean isRemoved) {}
}