package ua.zefir.zefiroptimizations.actors.messages;

import akka.actor.typed.ActorRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLike;

public interface ZefirsActorMessages  {
    interface ZefirsActorMessage {}

    interface ActorSystemManagerMessage {}
    interface EntityMessage {}
    interface MainThreadMessage {}

    record ServerEntityManagerCreated<T extends EntityLike>(ServerEntityManager<T> entityManager, RegistryKey<World> worldRegistryKey, ActorRef<ResponseEntityManagerActorRef> replyTo) implements ActorSystemManagerMessage {}
    record ServerWorldCreated(ServerWorld world, RegistryKey<World> worldRegistryKey, ActorRef<ResponseWorldActorRef> replyTo) implements ActorSystemManagerMessage {}


    record Tick() implements ActorSystemManagerMessage, EntityMessage {}
    record TickRiding() implements ActorSystemManagerMessage, EntityMessage {}
    record TickPlayerActor() implements EntityMessage {}

    record TickSingleEntity(Entity entity) implements ActorSystemManagerMessage {}
    record TickRidingSingleEntity(Entity entity) implements ActorSystemManagerMessage {}
    record TickPlayer(Entity entity) implements ActorSystemManagerMessage {}
    record ContinueTickMovement() implements EntityMessage{}

    record ApplyDamage(LivingEntity entity, DamageSource source, float amount) implements EntityMessage, MainThreadMessage{}
    record LootItemEntity(LivingEntity entity, ItemEntity itemEntity) implements EntityMessage, MainThreadMessage{}

    // Entity Lifecycle
    record EntityCreated(Entity entity) implements ActorSystemManagerMessage {}
    record EntityRemoved(Entity entity) implements ActorSystemManagerMessage {}

    // Requests
    record RequestIsRemoved(boolean isRemoved) implements EntityMessage{}

    record RequestEntityManagerActorRef(RegistryKey<World> worldRegistryKey, ActorRef<ResponseEntityManagerActorRef> replyTo) implements ActorSystemManagerMessage{}
    record ResponseEntityManagerActorRef(ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor) implements ActorSystemManagerMessage {}

    record RequestWorldActorRef(RegistryKey<World> worldRegistryKey, ActorRef<ResponseWorldActorRef> replyTo) implements ActorSystemManagerMessage{}
    record ResponseWorldActorRef(ActorRef<WorldMessages.WorldMessage> worldActor) implements ActorSystemManagerMessage {}
}