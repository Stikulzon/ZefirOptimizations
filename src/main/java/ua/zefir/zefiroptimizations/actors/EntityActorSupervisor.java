package ua.zefir.zefiroptimizations.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import net.minecraft.entity.LivingEntity;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;

public class EntityActorSupervisor extends AbstractBehavior<ZefirsActorMessages.EntityMessage> {

    private final ActorRef<ZefirsActorMessages.EntityMessage> entityActor;

    public static Behavior<ZefirsActorMessages.EntityMessage> create(LivingEntity entity) {
        return Behaviors.setup(context ->
                new EntityActorSupervisor(context, entity)
        );
    }
    private EntityActorSupervisor(ActorContext<ZefirsActorMessages.EntityMessage> context, LivingEntity entity) {
        super(context);
        String actorName = "entityActor_" + entity.getUuid();
        entityActor = context.spawn(Behaviors.supervise(EntityActor.create(entity)).onFailure(SupervisorStrategy.restart()), actorName);
        context.watch(entityActor);

    }

    @Override
    public Receive<ZefirsActorMessages.EntityMessage> createReceive() {
        return newReceiveBuilder()
                .onAnyMessage(message -> {
                    entityActor.tell(message);
                    return this;
                })
                .build();
    }
}