package ua.zefir.zefiroptimizations.mixin;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AskPattern;
import net.minecraft.entity.Entity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;
import ua.zefir.zefiroptimizations.data.CheckedThreadLocalRandom;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

@Mixin(World.class)
public class WorldMixin {
    @Unique
    private ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor;

    @Inject(method = "getRandom", at = @At("HEAD"), cancellable = true)
    private void onGetOtherEntities(CallbackInfoReturnable<Random> cir) {
        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread()){
            cir.setReturnValue(Random.create());
        }
    }

    @Inject(method = "getOtherEntities", at = @At("HEAD"), cancellable = true)
    private void onGetOtherEntities(@Nullable Entity except, Box box, Predicate<? super Entity> predicate, CallbackInfoReturnable<List<Entity>> cir) {
        World self = (World) (Object) this;

        if(this.entityManagerActor == null) {
            CompletionStage<ZefirsActorMessages.ResponseEntityManagerActorRef> resultFuture =
                    AskPattern.ask(
                            ZefirOptimizations.getActorSystem(),
                            replyTo -> new ZefirsActorMessages.RequestEntityManagerActorRef(self.getRegistryKey(), replyTo),
                            Duration.ofSeconds(10),
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                this.entityManagerActor = resultFuture.toCompletableFuture().get().entityManagerActor();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
        }

            CompletionStage<List<Entity>> resultFuture =
                    AskPattern.ask(
                            this.entityManagerActor,
                            replyTo -> {
                                ServerEntityManagerMessages.RequestOtherEntities originalRequest = new ServerEntityManagerMessages.RequestOtherEntities(except, box, predicate, replyTo);;
                                return new ServerEntityManagerMessages.RequestOtherEntities(originalRequest);
                            },
                            Duration.ofSeconds(10),
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                List<Entity> result = resultFuture.toCompletableFuture().get();
                cir.setReturnValue(result);
                cir.cancel();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
    }

//    @Inject(method = "getOtherEntities", at = @At("TAIL"))
//    private void onGetOtherEntitiesCheck(@Nullable Entity except, Box box, Predicate<? super Entity> predicate, CallbackInfoReturnable<List<Entity>> cir) {
//        System.out.println("This should never happen");
//    }

    @Inject(method = "collectEntitiesByType(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;Ljava/util/List;I)V", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void onCollectEntitiesByType(TypeFilter<Entity, T> filter, Box box, Predicate<? super T> predicate, List<? super T> result, int limit, CallbackInfo ci) {
        World self = (World) (Object) this;

        if(this.entityManagerActor == null) {
            CompletionStage<ZefirsActorMessages.ResponseEntityManagerActorRef> resultFuture =
                    AskPattern.ask(
                            ZefirOptimizations.getActorSystem(),
                            replyTo -> new ZefirsActorMessages.RequestEntityManagerActorRef(self.getRegistryKey(), replyTo),
                            Duration.ofSeconds(10),
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                this.entityManagerActor = resultFuture.toCompletableFuture().get().entityManagerActor();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
        }


            CompletionStage<List<? extends T>> resultFuture =
                    AskPattern.ask(
                            this.entityManagerActor,
                            replyTo -> {
                                ServerEntityManagerMessages.RequestEntitiesByTypeWorld<T> originalRequest = new ServerEntityManagerMessages.RequestEntitiesByTypeWorld<>(filter, box, predicate, limit, self, replyTo);
                                return new ServerEntityManagerMessages.RequestEntitiesByTypeWorld<>(originalRequest);
                            },
                            Duration.ofSeconds(10),
                            ZefirOptimizations.getActorSystem().scheduler());
            try {
                result.addAll(resultFuture.toCompletableFuture().get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
            }
        ci.cancel();
    }

    @Shadow @Final private Thread thread;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/random/Random;create()Lnet/minecraft/util/math/random/Random;"))
    private Random redirectWorldRandomInit() {
        return new CheckedThreadLocalRandom(RandomSeed.getSeed(), () -> this.thread);
    }


}
