package ua.zefir.zefiroptimizations.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

import java.util.HashMap;
import java.util.Map;

public class AsyncTickManagerActor extends AbstractBehavior<ZefirsActorMessages.AsyncTickManagerMessage> {

    private final Map<LivingEntity, ActorRef<ZefirsActorMessages.EntityMessage>> entityActors = new HashMap<>();

    public static Behavior<ZefirsActorMessages.AsyncTickManagerMessage> create() {
        return Behaviors.setup(AsyncTickManagerActor::new);
    }

    private AsyncTickManagerActor(ActorContext<ZefirsActorMessages.AsyncTickManagerMessage> context) {
        super(context);
    }

    @Override
    public Receive<ZefirsActorMessages.AsyncTickManagerMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(ZefirsActorMessages.AsyncTick.class, this::handleAsyncTick)
                .onMessage(ZefirsActorMessages.EntityCreated.class, this::handleEntityCreated)
                .onMessage(ZefirsActorMessages.EntityRemoved.class, this::handleEntityRemoved)
                .onMessage(ZefirsActorMessages.TickSingleEntity.class, this::handleAsyncSingleTick)
                .build();
    }

    private Behavior<ZefirsActorMessages.AsyncTickManagerMessage> handleAsyncTick(ZefirsActorMessages.AsyncTick msg) {
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

    private Behavior<ZefirsActorMessages.AsyncTickManagerMessage> handleAsyncSingleTick(ZefirsActorMessages.TickSingleEntity msg) {
        ActorRef<ZefirsActorMessages.EntityMessage> entityActor = entityActors.get(msg.entity());

        if (entityActor != null) {
            entityActor.tell(new ZefirsActorMessages.AsyncTick());
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.AsyncTickManagerMessage> handleEntityCreated(ZefirsActorMessages.EntityCreated msg) {
        LivingEntity entity = msg.entity();

        String actorName = "entitySupervisor_" + entity.getUuid();
        ActorRef<ZefirsActorMessages.EntityMessage> entitySupervisor = getContext().spawn(EntityActorSupervisor.create(entity), actorName);
        getContext().watch(entitySupervisor);
        entityActors.put(entity, entitySupervisor);
        return this;
    }

    private Behavior<ZefirsActorMessages.AsyncTickManagerMessage> handleEntityRemoved(ZefirsActorMessages.EntityRemoved msg) {
        LivingEntity entity = msg.entity();
        ActorRef<ZefirsActorMessages.EntityMessage> entityActor = entityActors.remove(entity);
        if (entityActor != null) {
            getContext().stop(entityActor);
        }
        return this;
    }
}