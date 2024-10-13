package ua.zefir.zefiroptimizations.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

import java.util.HashMap;
import java.util.Map;

public class AsyncTickManagerActor extends AbstractActor {
    private final Map<LivingEntity, ActorRef> entityActors = new HashMap<>();

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
                .match(EntityActorMessages.MainThreadCallback.class, msg -> {
                    // Execute the callback on the Minecraft server thread
                    ZefirOptimizations.SERVER.execute(() -> msg.callback().accept("Some result"));
                })
                .build();
    }

    private void handleAsyncTick(EntityActorMessages.AsyncTick msg) {
        ServerWorld world = ZefirOptimizations.SERVER.getOverworld();

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
        ActorRef entitySupervisor = getContext().actorOf(EntityActorSupervisor.props(entity), "entitySupervisor_" + entity.getUuid());
        entityActors.put(entity, entitySupervisor); // Store the supervisor reference
    }

    private void handleEntityRemoved(EntityActorMessages.EntityRemoved msg) {
        LivingEntity entity = msg.entity();
        ActorRef entityActor = entityActors.get(entity);
        if (entityActor != null) {
            entityActor.tell(PoisonPill.getInstance(), getSelf()); // Send poison pill for self-termination
        }
    }
}
