package ua.zefir.zefiroptimizations.actors.messages;

import akka.actor.typed.ActorRef;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingStatus;

import java.io.Writer;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ServerEntityManagerMessages {
    public sealed interface ServerEntityManagerMessage permits AddEntities, Close, Dump, EntityLookupForEach, EntityLookupForEachIntersects, EntityLookupForEachIntersectsTypeFilter, Flush, LoadEntities, RequestAddEntity, RequestDebugString, RequestEntityLookupById, RequestEntityLookupByUuid, RequestEntityLookupIterable, RequestHas, RequestIndexSize, RequestIsLoaded, RequestShouldTickBlockPos, RequestShouldTickChunkPos, Save, Tick, UpdateTrackingStatus, UpdateTrackingStatusChunkLevelType {}
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

    public record RequestEntityLookupByUuid(UUID uuid, ActorRef<ResponseEntityLookupEntity> replyTo) implements ServerEntityManagerMessage {}
    public record RequestEntityLookupById(int id, ActorRef<ResponseEntityLookupEntity> replyTo) implements ServerEntityManagerMessage {}
    public record RequestEntityLookupIterable<T extends EntityLike>(ActorRef<ResponseEntityLookupIterable<T>> replyTo) implements ServerEntityManagerMessage {}

    public record EntityLookupForEachIntersects(Box box, Consumer action) implements ServerEntityManagerMessage {}
    public record EntityLookupForEachIntersectsTypeFilter(TypeFilter filter, Box box, LazyIterationConsumer consumer) implements ServerEntityManagerMessage {}
    public record EntityLookupForEach(TypeFilter filter, LazyIterationConsumer consumer) implements ServerEntityManagerMessage {}

    public record ResponseEntityLookupEntity(Entity entity) implements ServerEntityManagerMessageResponse {}
    public record ResponseEntityLookupIterable<T extends EntityLike>(Iterable<T> iterable) implements ServerEntityManagerMessageResponse {}
}
