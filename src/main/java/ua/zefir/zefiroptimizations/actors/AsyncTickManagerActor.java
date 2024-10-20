package ua.zefir.zefiroptimizations.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

import java.util.*;

import static ua.zefir.zefiroptimizations.ZefirOptimizations.LOGGER;

public class AsyncTickManagerActor extends AbstractActor {
    private final Map<LivingEntity, ActorRef> entityActors = new HashMap<>();
//    private final Set<UUID> pendingRemoval = new HashSet<>();

    public static Props props() {
        return Props.create(AsyncTickManagerActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(EntityActorMessages.AsyncTick.class, this::handleAsyncTick)
                .match(EntityActorMessages.EntityCreated.class, this::handleEntityCreated)
                .match(EntityActorMessages.EntityRemoved.class, this::handleEntityRemoved)
                .match(EntityActorMessages.TickSingleEntity.class, this::handleAsyncSingleTick)
                .build();
    }

    private void handleAsyncTick(EntityActorMessages.AsyncTick msg) {
        ServerWorld world = ZefirOptimizations.SERVER.getOverworld();

        // TODO: there are better way to handle it
        for (LivingEntity entity : world.getEntitiesByType(
                TypeFilter.instanceOf(LivingEntity.class),
                e -> e instanceof LivingEntity)) {

            ActorRef entityActor = entityActors.get(entity);

            if (entityActor != null) {
                entityActor.tell(msg, getSelf());
            }
        }
    }

    private void handleAsyncSingleTick(EntityActorMessages.TickSingleEntity msg) {
            ActorRef entityActor = entityActors.get(msg.entity());

            if (entityActor != null) {
                entityActor.tell(new EntityActorMessages.AsyncTick(), getSelf());
            }
    }

    private void handleEntityCreated(EntityActorMessages.EntityCreated msg) {
        LivingEntity entity = msg.entity();
//        if (pendingRemoval.contains(entity.getUuid())) {
//            pendingRemoval.remove(entity.getUuid());
//            return;
//        }

        String actorName = "entitySupervisor_" + entity.getUuid();

//        if (getContext().findChild(actorName).isPresent()) {
//            LOGGER.warn("Actor with name {} already exists. This could indicate a problem with entity lifecycle management.", actorName);
//            LOGGER.warn("Entity details: {}", entity);
//        }

        ActorRef entitySupervisor = getContext().findChild(actorName).orElseGet(() -> getContext().actorOf(EntityActorSupervisor.props(entity), actorName));
        entityActors.put(entity, entitySupervisor);
    }

    private void handleEntityRemoved(EntityActorMessages.EntityRemoved msg) {
        LivingEntity entity = msg.entity();
//        ZefirOptimizations.LOGGER.info("EntityRemoved: {}", entity.getUuid());
//        pendingRemoval.add(entity.getUuid());
        ActorRef entityActor = entityActors.get(entity);
        if (entityActor != null) {
            entityActor.tell(PoisonPill.getInstance(), getSelf());
        }
    }
}
