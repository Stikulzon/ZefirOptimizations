package ua.zefir.zefiroptimizations.actors;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;

public final class ZefirsActorMessages {

    public sealed interface AsyncTickManagerMessage permits AsyncTick, EntityCreated, EntityRemoved, TickSingleEntity{}
    public sealed interface EntityMessage permits AsyncTick, BaseTickSingleEntity, ContinueTickMovement, ApplyDamage, LootItemEntity, RequestIsRemoved{}
    public sealed interface MainThreadMessage permits ApplyDamage, LootItemEntity{}
    public record AsyncTick() implements AsyncTickManagerMessage, EntityMessage{}

    // Position Updates
    public record TickSingleEntity(LivingEntity entity) implements AsyncTickManagerMessage{}
    public record BaseTickSingleEntity(LivingEntity entity) implements EntityMessage{}
    public record ContinueTickMovement() implements EntityMessage{}

    // Damage
    public record ApplyDamage(LivingEntity entity, DamageSource source, float amount) implements EntityMessage, MainThreadMessage{}
    public record LootItemEntity(LivingEntity entity, ItemEntity itemEntity) implements EntityMessage, MainThreadMessage{}
    // Entity Lifecycle
    public record EntityCreated(LivingEntity entity) implements AsyncTickManagerMessage{}
    public record EntityRemoved(LivingEntity entity) implements AsyncTickManagerMessage{}

    // Requests
    public record RequestIsRemoved(boolean isRemoved) implements EntityMessage{}
    public record IntegerResponse(int value) {} // For example purposes
}