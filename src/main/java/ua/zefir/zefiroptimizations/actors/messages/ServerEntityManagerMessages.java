package ua.zefir.zefiroptimizations.actors.messages;

import akka.actor.typed.ActorRef;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.EntityTrackingStatus;
import org.jetbrains.annotations.Nullable;

import java.io.Writer;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ServerEntityManagerMessages {
    public sealed interface ServerEntityManagerMessage permits AddEntities, RequestCacheTrackingSection, Close, Dump, EntityLeftSection, EntityLookupForEach, EntityLookupForEachIntersects, EntityLookupForEachIntersectsTypeFilter, EntityUuidsRemove, Flush, HandlerDestroy, HandlerUpdateLoadStatus, LoadEntities, RequestAddEntity, RequestDebugString, RequestEntitiesByTypeServerWorld, RequestEntitiesByTypeWorld, RequestEntityLookupById, RequestEntityLookupByUuid, RequestEntityLookupIterable, RequestHas, RequestIndexSize, RequestIsLoaded, RequestOtherEntities, RequestShouldTickBlockPos, RequestShouldTickChunkPos, Save, StartTicking, StartTracking, StopTicking, StopTracking, Tick, UpdateTrackingStatus, UpdateTrackingStatusChunkLevelType {}
    public sealed interface ServerEntityManagerMessageResponse permits ResponseDebugString, ResponseEntityLookupEntity, ResponseEntityLookupIterable, ResponseIndexSize {}

    public record Tick() implements ServerEntityManagerMessage {}
    public record LoadEntities(Stream<Entity> entities) implements ServerEntityManagerMessage {}
    public record AddEntities(Stream<Entity> entities) implements ServerEntityManagerMessage {}
    public record UpdateTrackingStatusChunkLevelType(ChunkPos chunkPos, ChunkLevelType levelType) implements ServerEntityManagerMessage {}
    public record UpdateTrackingStatus(ChunkPos chunkPos, EntityTrackingStatus trackingStatus) implements ServerEntityManagerMessage {}
    public record Save() implements ServerEntityManagerMessage {}
    public record Flush() implements ServerEntityManagerMessage {}
    public record Close() implements ServerEntityManagerMessage {}
    public record Dump(Writer writer) implements ServerEntityManagerMessage {}

    // Requests
    public record RequestAddEntity(Entity entity, ActorRef<Boolean> replyTo) implements ServerEntityManagerMessage {}
    public record RequestHas(UUID uuid, ActorRef<Boolean> replyTo) implements ServerEntityManagerMessage {}
    public record RequestIsLoaded(long chunkPos, ActorRef<Boolean> replyTo) implements ServerEntityManagerMessage {}
    public record RequestShouldTickBlockPos(BlockPos pos, ActorRef<Boolean> replyTo) implements ServerEntityManagerMessage {}
    public record RequestShouldTickChunkPos(ChunkPos pos, ActorRef<Boolean> replyTo) implements ServerEntityManagerMessage {}
    public record RequestDebugString(ActorRef<ResponseDebugString> replyTo) implements ServerEntityManagerMessage {}
    public record RequestIndexSize(ActorRef<ResponseIndexSize> replyTo) implements ServerEntityManagerMessage {}

//    public record ResponseAddEntity(boolean result) implements ServerEntityManagerMessageResponse {}
//    public record ResponseHas(boolean result) implements ServerEntityManagerMessageResponse {}
//    public record ResponseIsLoaded(boolean result) implements ServerEntityManagerMessageResponse {}
//    public record ResponseShouldTickBlockPos(boolean result) implements ServerEntityManagerMessageResponse {}
//    public record ResponseShouldTickChunkPos(boolean result) implements ServerEntityManagerMessageResponse {}
//    public record ResponseBoolean(boolean result) implements ServerEntityManagerMessageResponse {}

    public record ResponseDebugString(String debugString) implements ServerEntityManagerMessageResponse {}
    public record ResponseIndexSize(int indexSize) implements ServerEntityManagerMessageResponse {}

    // Lookups
    public record RequestEntityLookupByUuid(UUID uuid, ActorRef<ResponseEntityLookupEntity> replyTo) implements ServerEntityManagerMessage {}
    public record RequestEntityLookupById(int id, ActorRef<ResponseEntityLookupEntity> replyTo) implements ServerEntityManagerMessage {}
    public record RequestEntityLookupIterable<T extends EntityLike>(ActorRef<ResponseEntityLookupIterable<T>> replyTo) implements ServerEntityManagerMessage {}
    public record RequestOtherEntities(@Nullable Entity except, Box box, Predicate<? super Entity> predicate,
                                       ActorRef<List<Entity>> replyTo) implements ServerEntityManagerMessage {
        public RequestOtherEntities(RequestOtherEntities original) {
            this(
                    original.except,
                    new Box(original.box.minX, original.box.minY, original.box.minZ,
                            original.box.maxX, original.box.maxY, original.box.maxZ),
                    copyPredicate(original.predicate),
                    original.replyTo
            );
        }
        private static <T> Predicate<? super T> copyPredicate(Predicate<? super T> original) {
            if (original instanceof Cloneable) {
                try {
                    return (Predicate<? super T>) original.getClass().getMethod("clone").invoke(original);
                } catch (Exception e) {
                    System.err.println("Warning: Predicate is Cloneable but clone() method failed. Using shallow copy.");
                }
            }
            return original;
        }
    }
    public record RequestEntitiesByTypeWorld<T extends Entity>(TypeFilter<Entity, T> filter, Box box,
                                                               Predicate<? super T> predicate, int limit, World world, ActorRef<List<? extends T>> replyTo)
            implements ServerEntityManagerMessage {

        // Deep copy constructor
        public RequestEntitiesByTypeWorld(RequestEntitiesByTypeWorld<T> original) {
            this(
                    original.filter, // Shallow copy is OK (immutable)
                    new Box(original.box.minX, original.box.minY, original.box.minZ,
                            original.box.maxX, original.box.maxY, original.box.maxZ), // Deep copy of Box
                    copyPredicate(original.predicate), // Attempt deep copy, fallback to shallow
                    original.limit,
                    original.world,
                    original.replyTo
            );
        }
        private static <T> Predicate<? super T> copyPredicate(Predicate<? super T> original) {
            if (original instanceof Cloneable) { //Cloneable check
                try {
                    return (Predicate<? super T>) original.getClass().getMethod("clone").invoke(original);
                } catch (Exception e) {
                    System.err.println("Warning: Predicate is Cloneable but clone() method failed. Using shallow copy.");
                }
            }
            return original;
        }
    }

    public record RequestEntitiesByTypeServerWorld<T extends Entity>(TypeFilter<Entity, T> filter,
                                                                     Predicate<? super T> predicate, int limit,
                                                                     ActorRef<List<? extends T>> replyTo)
            implements ServerEntityManagerMessage {

        public RequestEntitiesByTypeServerWorld(RequestEntitiesByTypeServerWorld<T> original) {
            this(
                    original.filter,
                    copyPredicate(original.predicate),
                    original.limit,
                    original.replyTo
            );
        }
        private static <T> Predicate<? super T> copyPredicate(Predicate<? super T> original) {
            if (original instanceof Cloneable) {
                try {
                    return (Predicate<? super T>) original.getClass().getMethod("clone").invoke(original);
                } catch (Exception e) {
                    System.err.println("Warning: Predicate is Cloneable but clone() method failed. Using shallow copy.");
                }
            }
            return original;
        }
    }

    public record ResponseEntityLookupEntity(Entity entity) implements ServerEntityManagerMessageResponse {}
//    public record ResponseOtherEntities(List<Entity> list) implements ServerEntityManagerMessageResponse {}
    public record ResponseEntityLookupIterable<T extends EntityLike>(Iterable<T> iterable) implements ServerEntityManagerMessageResponse {}

    public record EntityLookupForEachIntersects(Box box, Consumer action) implements ServerEntityManagerMessage {}
    public record EntityLookupForEachIntersectsTypeFilter(TypeFilter filter, Box box, LazyIterationConsumer consumer) implements ServerEntityManagerMessage {}
    public record EntityLookupForEach(TypeFilter filter, LazyIterationConsumer consumer) implements ServerEntityManagerMessage {}

    // Listener
    public record EntityLeftSection<T extends EntityLike>(long sectionPos, EntityTrackingSection<T> section) implements ServerEntityManagerMessage {}
    public record StartTicking<T extends EntityLike>(T entity) implements ServerEntityManagerMessage {}
    public record StopTicking<T extends EntityLike>(T entity) implements ServerEntityManagerMessage {}
    public record StartTracking<T extends EntityLike>(T entity) implements ServerEntityManagerMessage {}
    public record StopTracking<T extends EntityLike>(T entity) implements ServerEntityManagerMessage {}
    public record HandlerDestroy<T>(T entity) implements ServerEntityManagerMessage {}
    public record HandlerUpdateLoadStatus<T>(T entity) implements ServerEntityManagerMessage {}
    public record EntityUuidsRemove(UUID uuid) implements ServerEntityManagerMessage {}

    public record RequestCacheTrackingSection<T extends EntityLike>(long sectionPos, ActorRef<EntityTrackingSection<T>> replyTo) implements ServerEntityManagerMessage {}
//    public record ResponseCacheTrackingSection<T extends EntityLike>(EntityTrackingSection<T> entityTrackingSection) implements ServerEntityManagerMessageResponse {}

}
