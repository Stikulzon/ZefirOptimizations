package ua.zefir.zefiroptimizations.data;

import akka.actor.typed.ActorRef;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityIndex;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.SectionedEntityCache;
import net.minecraft.world.entity.SimpleEntityLookup;
import org.jetbrains.annotations.Nullable;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;

import java.util.UUID;
import java.util.function.Consumer;

public class CheckedSimpleEntityLookup<T extends EntityLike> extends SimpleEntityLookup<T> {
    private ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor;
    public CheckedSimpleEntityLookup(EntityIndex<T> index, SectionedEntityCache<T> cache) {
        super(index, cache);
    }

    @Nullable
    public T get(int id, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        return super.get(id);
    }

    @Nullable
    public T get(UUID uuid, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        return super.get(uuid);
    }

    public Iterable<T> iterate(ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        return super.iterate();
    }

    public <U extends T> void forEach(TypeFilter<T, U> filter, LazyIterationConsumer<U> consumer, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        super.forEach(filter, consumer);
    }

    public void forEachIntersects(Box box, Consumer<T> action, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        super.forEachIntersects(box, action);
    }

    public <U extends T> void forEachIntersects(TypeFilter<T, U> filter, Box box, LazyIterationConsumer<U> consumer, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        super.forEachIntersects(filter, box, consumer);
    }
}
