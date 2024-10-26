package ua.zefir.zefiroptimizations.actors;


import akka.actor.AbstractActor;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

public class MainThreadActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ZefirsActorMessages.TickNewAiAndContinue.class, msg -> {
                    ZefirOptimizations.SERVER.execute(() -> {
                        msg.entity().getWorld().getProfiler().push("newAi");
                        ((IAsyncLivingEntityAccess) msg.entity()).zefiroptimizations$tickNewAi();
                        msg.entity().getWorld().getProfiler().pop();
                        msg.requestingActor().tell(new ZefirsActorMessages.ContinueTickMovement(), getSelf());
                    });
                })
                .match(ZefirsActorMessages.ApplyDamage.class, msg -> {
                    ZefirOptimizations.SERVER.execute(() -> {
                        msg.entity().damage(msg.source(), msg.amount());
                    });
                })
                .match(ZefirsActorMessages.LootItemEntity.class, msg -> {
                    ZefirOptimizations.SERVER.execute(() -> {
                        msg.iAsyncLivingEntityAccess().zefiroptimizations$loot(msg.itemEntity());
                    });
                })
                .build();
    }
}
