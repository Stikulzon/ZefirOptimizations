package ua.zefir.zefiroptimizations.data;

import akka.actor.typed.ActorRef;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.annotation.Debug;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import org.jetbrains.annotations.Nullable;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;

import java.util.stream.LongStream;
import java.util.stream.Stream;

public class CheckedSectionedEntityCache<T extends EntityLike> extends SectionedEntityCache<T> {
    private ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> entityManagerActor;
    
    public CheckedSectionedEntityCache(Class entityClass, Long2ObjectFunction chunkStatusDiscriminator)  {
        super(entityClass, chunkStatusDiscriminator);
    }

    public void forEachInBox(Box box, LazyIterationConsumer<EntityTrackingSection<T>> consumer, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        super.forEachInBox(box, consumer);
    }

    public LongStream getSections(long chunkPos, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        return super.getSections(chunkPos);
    }

    public Stream<EntityTrackingSection<T>> getTrackingSections(long chunkPos, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        return super.getTrackingSections(chunkPos);
    }

    public EntityTrackingSection<T> getTrackingSection(long sectionPos, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        return super.getTrackingSection(sectionPos);
    }

    @Nullable
    public EntityTrackingSection<T> findTrackingSection(long sectionPos, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        return super.findTrackingSection(sectionPos);
    }

    public LongSet getChunkPositions(ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        return super.getChunkPositions();
    }

    public void forEachIntersects(Box box, LazyIterationConsumer<T> consumer, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        super.forEachIntersects(box, consumer);
    }

    public <U extends T> void forEachIntersects(TypeFilter<T, U> filter, Box box, LazyIterationConsumer<U> consumer, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        super.forEachIntersects(filter, box, consumer);
    }

    public void removeSection(long sectionPos, ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        super.removeSection(sectionPos);
    }

    @Debug
    public int sectionCount(ActorRef<ServerEntityManagerMessages.ServerEntityManagerMessage> actorRef)  {
        if(entityManagerActor == null) {
            entityManagerActor = actorRef;
        } else if(actorRef != entityManagerActor) {
            throw new IllegalStateException("Actor refs do not match, required: " + entityManagerActor + ", get: " + actorRef);
        }
        return super.sectionCount();
    }
}
