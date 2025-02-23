package ua.zefir.zefiroptimizations.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.mixin.MobEntityAccessor;

public class MainThreadActor extends AbstractBehavior<ZefirsActorMessages.MainThreadMessage> {

    public static Behavior<ZefirsActorMessages.MainThreadMessage> create() {
        return Behaviors.setup(MainThreadActor::new);
    }

    private MainThreadActor(ActorContext<ZefirsActorMessages.MainThreadMessage> context) {
        super(context);
    }

    @Override
    public Receive<ZefirsActorMessages.MainThreadMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(ZefirsActorMessages.ApplyDamage.class, this::onApplyDamage)
                .onMessage(ZefirsActorMessages.LootItemEntity.class, this::onLootItemEntity)
                .build();
    }

    private Behavior<ZefirsActorMessages.MainThreadMessage> onApplyDamage(ZefirsActorMessages.ApplyDamage msg) {
        ZefirOptimizations.SERVER.execute(() -> {
            msg.entity().damage(msg.source(), msg.amount());
        });
        return this;
    }

    private Behavior<ZefirsActorMessages.MainThreadMessage> onLootItemEntity(ZefirsActorMessages.LootItemEntity msg) {
        ZefirOptimizations.SERVER.execute(() -> {
            ((MobEntityAccessor) msg.entity()).invokeLoot(msg.itemEntity());
        });
        return this;
    }
}
