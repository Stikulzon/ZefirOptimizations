package ua.zefir.zefiroptimizations.actors.messages;

import akka.actor.typed.ActorRef;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLike;

public final class ZefirsActorMessages {

    public sealed interface ActorSystemManagerMessage permits EntityCreated, EntityRemoved, RequestEntityManagerActorRef, ResponseEntityManagerActorRef, ServerEntityManagerCreated, Tick, TickSingleEntity {}
    public sealed interface EntityMessage permits Tick, BaseTickSingleEntity, ContinueTickMovement, ApplyDamage, LootItemEntity, RequestIsRemoved{}
    public sealed interface MainThreadMessage permits ApplyDamage, LootItemEntity{}

    public record ServerEntityManagerCreated<T extends EntityLike>(ServerEntityManager<T> entityManager, RegistryKey<World> worldRegistryKey, ActorRef<ResponseEntityManagerActorRef> replyTo) implements ActorSystemManagerMessage {}

    public record Tick() implements ActorSystemManagerMessage, EntityMessage {}

    public record TickSingleEntity(LivingEntity entity) implements ActorSystemManagerMessage {}
    public record BaseTickSingleEntity(LivingEntity entity) implements EntityMessage{}
    public record ContinueTickMovement() implements EntityMessage{}

    public record ApplyDamage(LivingEntity entity, DamageSource source, float amount) implements EntityMessage, MainThreadMessage{}
    public record LootItemEntity(LivingEntity entity, ItemEntity itemEntity) implements EntityMessage, MainThreadMessage{}

    // Entity Lifecycle
    public record EntityCreated(LivingEntity entity) implements ActorSystemManagerMessage {}
    public record EntityRemoved(LivingEntity entity) implements ActorSystemManagerMessage {}

    // Requests
    public record RequestIsRemoved(boolean isRemoved) implements EntityMessage{}

    public record RequestEntityManagerActorRef(RegistryKey<World> worldRegistryKey, ActorRef<ResponseEntityManagerActorRef> replyTo) implements ActorSystemManagerMessage{}

    public record ResponseEntityManagerActorRef(ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor) implements ActorSystemManagerMessage {}
}