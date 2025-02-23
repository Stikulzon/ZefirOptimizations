package ua.zefir.zefiroptimizations.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import net.minecraft.entity.*;
import ua.zefir.zefiroptimizations.mixin.LivingEntityAccessor;

import java.util.concurrent.ThreadLocalRandom;

public class EntityActor extends AbstractBehavior<ZefirsActorMessages.EntityMessage> {
    protected final LivingEntity entity;
    protected final LivingEntityAccessor newEntityAccess;
    protected final ThreadLocalRandom random = ThreadLocalRandom.current();

    // Factory method (no more Props)
    public static Behavior<ZefirsActorMessages.EntityMessage> create(LivingEntity entity) {
        return Behaviors.setup(context -> new EntityActor(context, entity));
    }

    private EntityActor(ActorContext<ZefirsActorMessages.EntityMessage> context, LivingEntity entity) {
        super(context);
        this.entity = entity;
        this.newEntityAccess = (LivingEntityAccessor) entity;  // Assuming this is still valid
    }

    @Override
    public Receive<ZefirsActorMessages.EntityMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(ZefirsActorMessages.AsyncTick.class, this::handleAsyncTick)
                .onMessage(ZefirsActorMessages.BaseTickSingleEntity.class, this::handleBaseTick)
                .onMessage(ZefirsActorMessages.RequestIsRemoved.class, this::onRequestIsRemoved)
                .build();
    }

    private Behavior<ZefirsActorMessages.EntityMessage> handleAsyncTick(ZefirsActorMessages.AsyncTick msg) {
        if (!entity.isRemoved()) {
            entity.tick();
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.EntityMessage> handleBaseTick(ZefirsActorMessages.BaseTickSingleEntity msg) {
        if (!entity.isRemoved()) {
            entity.baseTick();
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.EntityMessage> onRequestIsRemoved(ZefirsActorMessages.RequestIsRemoved msg) {

        getContext().getSelf().tell(new ZefirsActorMessages.RequestIsRemoved(this.entity.isRemoved())); // This example is for understanding, but it is redundant.
        return this;
    }
}
