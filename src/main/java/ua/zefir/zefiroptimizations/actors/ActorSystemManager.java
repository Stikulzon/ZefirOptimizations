package ua.zefir.zefiroptimizations.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.world.World;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;
import ua.zefir.zefiroptimizations.data.ServerEntityManagerRef;

import java.util.HashMap;
import java.util.Map;

public class ActorSystemManager extends AbstractBehavior<ZefirsActorMessages.ActorSystemManagerMessage> {

    private final Map<Entity, ActorRef<ZefirsActorMessages.EntityMessage>> entityActors = new HashMap<>();
    private final Map<RegistryKey<World>, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage>> entityManagerActors = new HashMap<>();

    public static Behavior<ZefirsActorMessages.ActorSystemManagerMessage> create() {
        return Behaviors.setup(ActorSystemManager::new);
    }

    private ActorSystemManager(ActorContext<ZefirsActorMessages.ActorSystemManagerMessage> context) {
        super(context);
    }

    @Override
    public Receive<ZefirsActorMessages.ActorSystemManagerMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(ZefirsActorMessages.Tick.class, this::handleAsyncTick)
                .onMessage(ZefirsActorMessages.EntityCreated.class, this::handleEntityCreated)
                .onMessage(ZefirsActorMessages.EntityRemoved.class, this::handleEntityRemoved)
                .onMessage(ZefirsActorMessages.TickSingleEntity.class, this::handleAsyncSingleTick)
                .onMessage(ZefirsActorMessages.TickPlayer.class, this::handleTickPlayer)
                .onMessage(ZefirsActorMessages.ServerEntityManagerCreated.class, this::handleEntityManagerCreated)
                .onMessage(ZefirsActorMessages.RequestEntityManagerActorRef.class, this::requestEntityManagerActorRef)
                .onSignal(Terminated.class, this::onTerminated)
                .build();
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> requestEntityManagerActorRef(ZefirsActorMessages.RequestEntityManagerActorRef msg) {
        var result = entityManagerActors.get(msg.worldRegistryKey());
        msg.replyTo().tell(new ZefirsActorMessages.ResponseEntityManagerActorRef(result));
        return this;
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> handleAsyncTick(ZefirsActorMessages.Tick msg) {
        ServerWorld world = ZefirOptimizations.SERVER.getOverworld();

        // TODO: there are better way to handle this
        for (LivingEntity entity : world.getEntitiesByType(
                TypeFilter.instanceOf(LivingEntity.class),
                e -> e instanceof LivingEntity)) {

            ActorRef<ZefirsActorMessages.EntityMessage> entityActor = entityActors.get(entity);

            if (entityActor != null) {
                entityActor.tell(msg);
            } else {
                System.out.println("entityActor are null for entity " + entity);
            }
        }
//        System.out.println("Tick!");
        return this;
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> handleAsyncSingleTick(ZefirsActorMessages.TickSingleEntity msg) {
        ActorRef<ZefirsActorMessages.EntityMessage> entityActor = entityActors.get(msg.entity());
//        Optional<ActorRef<Void>> entityActor = this.getContext().getChild("entityActor_" + msg.entity().getUuid());

        if (entityActor != null) {
            entityActor.tell(new ZefirsActorMessages.Tick());
        } else {
            System.out.println("entityActor are null for entity " + msg.entity());
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> handleTickPlayer(ZefirsActorMessages.TickPlayer msg) {
        ActorRef<ZefirsActorMessages.EntityMessage> entityActor = entityActors.get(msg.entity());
        if (entityActor != null) {
            entityActor.tell(new ZefirsActorMessages.TickPlayerActor());
        } else {
            System.out.println("entityActor are null for entity " + msg.entity());
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> handleEntityManagerCreated(ZefirsActorMessages.ServerEntityManagerCreated msg) {

        String actorName = "entityManager_" + msg.worldRegistryKey().getValue().toString();
        System.out.println("Created entityManager actor with name " + actorName);
        ServerEntityManager<Entity> entityManager = msg.entityManager();
        ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor = getContext().spawn(Behaviors.supervise(ServerEntityManagerActor.create(entityManager)).onFailure(SupervisorStrategy.restart()), actorName);
        entityManagerActors.put(msg.worldRegistryKey(), entityManagerActor);
        ((ServerEntityManagerRef) entityManager).setEntityManagerActor(entityManagerActor);

        msg.replyTo().tell(new ZefirsActorMessages.ResponseEntityManagerActorRef(entityManagerActor));
        return this;
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> handleEntityCreated(ZefirsActorMessages.EntityCreated msg) {
        Entity entity = msg.entity();

        String actorName = "entityActor_" + entity.getUuid();
        if (getContext().getChild(actorName).isEmpty()) {
            ActorRef<ZefirsActorMessages.EntityMessage> entityActor = getContext().spawn(Behaviors.supervise(EntityActor.create(entity)).onFailure(SupervisorStrategy.restart()), actorName);
            getContext().watch(entityActor);
            entityActors.put(entity, entityActor);
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> handleEntityRemoved(ZefirsActorMessages.EntityRemoved msg) {
        Entity entity = msg.entity();
        ActorRef<ZefirsActorMessages.EntityMessage> entityActor = entityActors.remove(entity);
        if (entityActor != null) {
            getContext().stop(entityActor);
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> onTerminated(Terminated signal) {
        getContext().getLog().info("Child actor {} has terminated.", signal.getRef().path().name());
        return this;
    }

}