package ua.zefir.zefiroptimizations.actors;


import akka.actor.AbstractActor;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

public class MainThreadActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(EntityActorMessages.TickNewAiAndContinue.class, msg -> {
                    ZefirOptimizations.SERVER.execute(() -> {
//                        ZefirOptimizations.LOGGER.info("TickNewAiAndContinue");
                        msg.entity().getWorld().getProfiler().push("newAi");
                        ((IAsyncLivingEntityAccess) msg.entity()).zefiroptimizations$tickNewAi();
//                        ZefirOptimizations.LOGGER.info("TickNewAiAndContinue");
                        msg.entity().getWorld().getProfiler().pop();
                        msg.requestingActor().tell(new EntityActorMessages.ContinueTickMovement(), getSelf());
                    });
                })
                .build();
    }
}
