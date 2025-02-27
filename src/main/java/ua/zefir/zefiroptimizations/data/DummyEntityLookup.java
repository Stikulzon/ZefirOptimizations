package ua.zefir.zefiroptimizations.data;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AskPattern;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityLookup;
import org.jetbrains.annotations.Nullable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class DummyEntityLookup<T extends EntityLike> implements EntityLookup<T> {

    private final ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor;

    public DummyEntityLookup(RegistryKey<World> worldRegistryKey) {
        CompletionStage<ZefirsActorMessages.ResponseEntityManagerActorRef> resultFuture =
                AskPattern.ask(
                        ZefirOptimizations.getActorSystem(),
                        replyTo -> new ZefirsActorMessages.RequestEntityManagerActorRef(worldRegistryKey, replyTo),
                        Duration.ofSeconds(3),
                        ZefirOptimizations.getActorSystem().scheduler());
        try {
            this.entityManagerActor = resultFuture.toCompletableFuture().get().entityManagerActor();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
        }
    }

    @Override
    public T get(int id) {
        CompletionStage<ServerEntityManagerMessages.ResponseEntityLookupEntity> resultFuture =
                AskPattern.ask(
                        entityManagerActor,
                        replyTo -> new ServerEntityManagerMessages.RequestEntityLookupById(id, replyTo),
                        Duration.ofSeconds(3),
                        ZefirOptimizations.getActorSystem().scheduler());
        try {
            return (T) resultFuture.toCompletableFuture().get().entity();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
        }
    }

    @Override
    public @Nullable T get(UUID uuid) {
        CompletionStage<ServerEntityManagerMessages.ResponseEntityLookupEntity> resultFuture =
                AskPattern.ask(
                        entityManagerActor,
                        replyTo -> new ServerEntityManagerMessages.RequestEntityLookupByUuid(uuid, replyTo),
                        Duration.ofSeconds(3),
                        ZefirOptimizations.getActorSystem().scheduler());
        try {
            return (T) resultFuture.toCompletableFuture().get().entity();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
        }
    }

    @Override
    public Iterable<T> iterate() {
        CompletionStage<ServerEntityManagerMessages.ResponseEntityLookupIterable<T>> resultFuture =
                AskPattern.ask(
                        entityManagerActor,
                        ServerEntityManagerMessages.RequestEntityLookupIterable::new,
                        Duration.ofSeconds(3),
                        ZefirOptimizations.getActorSystem().scheduler());
        try {
            return resultFuture.toCompletableFuture().get().iterable();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
        }
    }

    @Override
    public void forEachIntersects(Box box, Consumer action) {
        throw new RuntimeException("Unauthorized EntityLookup access. You need to request it from the ServerEntityManager actor.");
//        entityManagerActor
//                .tell(new ServerEntityManagerMessages.EntityLookupForEachIntersects(box, action));
    }

    @Override
    public void forEachIntersects(TypeFilter filter, Box box, LazyIterationConsumer consumer) {
        throw new RuntimeException("Unauthorized EntityLookup access. You need to request it from the ServerEntityManager actor.");
//        entityManagerActor
//                .tell(new ServerEntityManagerMessages.EntityLookupForEachIntersectsTypeFilter(filter, box, consumer));
    }

    @Override
    public void forEach(TypeFilter filter, LazyIterationConsumer consumer) {
        throw new RuntimeException("Unauthorized EntityLookup access. You need to request it from the ServerEntityManager actor.");
//        entityManagerActor
//                .tell(new ServerEntityManagerMessages.EntityLookupForEach(filter, consumer));
    }
}
