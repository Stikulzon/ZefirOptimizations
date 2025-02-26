package ua.zefir.zefiroptimizations.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;

import java.util.HashMap;
import java.util.Map;

public class ActorSystemManager extends AbstractBehavior<ZefirsActorMessages.ActorSystemManagerMessage> {

    private final Map<LivingEntity, ActorRef<ZefirsActorMessages.EntityMessage>> entityActors = new HashMap<>();
    public static ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor;

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
                .onMessage(ZefirsActorMessages.ServerEntityManagerCreated.class, this::handleEntityManagerCreated)
                .build();
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
            }
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> handleAsyncSingleTick(ZefirsActorMessages.TickSingleEntity msg) {
        ActorRef<ZefirsActorMessages.EntityMessage> entityActor = entityActors.get(msg.entity());

        if (entityActor != null) {
            entityActor.tell(new ZefirsActorMessages.Tick());
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> handleEntityManagerCreated(ZefirsActorMessages.ServerEntityManagerCreated msg) {
        if(entityManagerActor == null) {
            ServerEntityManager<Entity> entityManager = msg.entityManager();
            entityManagerActor = getContext().spawn(Behaviors.supervise(ServerEntityManagerActor.create(entityManager)).onFailure(SupervisorStrategy.restart()), "entityManager");
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> handleEntityCreated(ZefirsActorMessages.EntityCreated msg) {
        LivingEntity entity = msg.entity();

        String actorName = "entitySupervisor_" + entity.getUuid();
        ActorRef<ZefirsActorMessages.EntityMessage> entitySupervisor = getContext().spawn(EntityActorSupervisor.create(entity), actorName);
        getContext().watch(entitySupervisor);
        entityActors.put(entity, entitySupervisor);
        return this;
    }

    private Behavior<ZefirsActorMessages.ActorSystemManagerMessage> handleEntityRemoved(ZefirsActorMessages.EntityRemoved msg) {
        LivingEntity entity = msg.entity();
        ActorRef<ZefirsActorMessages.EntityMessage> entityActor = entityActors.remove(entity);
        if (entityActor != null) {
            getContext().stop(entityActor);
        }
        return this;
    }
}