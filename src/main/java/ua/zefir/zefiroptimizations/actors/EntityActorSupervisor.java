package ua.zefir.zefiroptimizations.actors;


import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.japi.pf.DeciderBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import scala.concurrent.duration.Duration;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

import java.util.concurrent.TimeUnit;

import static akka.actor.SupervisorStrategy.escalate;
import static akka.actor.SupervisorStrategy.restart;
import static akka.actor.SupervisorStrategy.resume;
import static ua.zefir.zefiroptimizations.ZefirOptimizations.LOGGER;

public class EntityActorSupervisor extends AbstractActor {

    private final ActorRef entityActor;

    public static Props props(LivingEntity entity) {
            return Props.create(EntityActorSupervisor.class, entity);
    }

    public EntityActorSupervisor(LivingEntity entity) {
        String actorName = "entityActor_" + entity.getUuid();

        if (getContext().findChild(actorName).isPresent()) {
            LOGGER.warn("Actor with name {} already exists. This could indicate a problem with entity lifecycle management.", actorName);
            LOGGER.warn("Entity details: {}", entity);
        }

        entityActor = getContext().findChild(actorName).orElseGet(() -> {
            if (entity instanceof MobEntity) {
                return getContext().actorOf(MobEntityActor.props(entity), actorName);
            } else if (entity instanceof ArmorStandEntity) {
                return getContext().actorOf(ArmorStandEntityActor.props(entity), actorName);
            } else {
                return getContext().actorOf(EntityActor.props(entity), actorName);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchAny(message -> entityActor.forward(message, getContext()))
                .build();
    }

    private static final SupervisorStrategy strategy =
            new OneForOneStrategy(10, Duration.create(1, TimeUnit.MINUTES), DeciderBuilder
                    .match(NullPointerException.class, e -> restart())
                    .match(IllegalArgumentException.class, e -> resume())
                    .match(RuntimeException.class, e -> restart())
                    .matchAny(o -> escalate())
                    .build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }
}