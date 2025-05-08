package ua.zefir.zefiroptimizations.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;
import ua.zefir.zefiroptimizations.data.ServerPlayerEntityMixinInterface;
import ua.zefir.zefiroptimizations.mixin.LivingEntityAccessor;

import java.util.concurrent.ThreadLocalRandom;

public class EntityActor extends AbstractBehavior<ZefirsActorMessages.EntityMessage> {
    protected final Entity entity;
    protected final ThreadLocalRandom random = ThreadLocalRandom.current();

    public static Behavior<ZefirsActorMessages.EntityMessage> create(Entity entity) {
        return Behaviors.setup(context -> new EntityActor(context, entity));
    }

    private EntityActor(ActorContext<ZefirsActorMessages.EntityMessage> context, Entity entity) {
        super(context);
        this.entity = entity;
    }

    @Override
    public Receive<ZefirsActorMessages.EntityMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(ZefirsActorMessages.Tick.class, this::handleAsyncTick)
                .onMessage(ZefirsActorMessages.TickPlayerActor.class, this::handleTickPlayerActor)
                .onMessage(ZefirsActorMessages.RequestIsRemoved.class, this::onRequestIsRemoved)
                .build();
    }

    private Behavior<ZefirsActorMessages.EntityMessage> handleAsyncTick(ZefirsActorMessages.Tick msg) {
        if (!entity.isRemoved()) {
            entity.tick();
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.EntityMessage> handleTickPlayerActor(ZefirsActorMessages.TickPlayerActor msg) {
        if (!entity.isRemoved() && entity instanceof PlayerEntity player) {
            ((ServerPlayerEntityMixinInterface) player).zefirOptimizations$callSuperTick();
        }
        return this;
    }

    private Behavior<ZefirsActorMessages.EntityMessage> onRequestIsRemoved(ZefirsActorMessages.RequestIsRemoved msg) {

        getContext().getSelf().tell(new ZefirsActorMessages.RequestIsRemoved(this.entity.isRemoved())); // This example is for understanding, but it is redundant.
        return this;
    }
}
