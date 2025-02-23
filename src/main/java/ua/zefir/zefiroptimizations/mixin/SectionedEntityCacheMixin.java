package ua.zefir.zefiroptimizations.mixin;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import it.unimi.dsi.fastutil.longs.*;

import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.util.TypeFilter;
import net.minecraft.util.annotation.Debug;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.EntityTrackingStatus;
import net.minecraft.world.entity.SectionedEntityCache;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SectionedEntityCache.class, priority = 999)
public abstract class SectionedEntityCacheMixin<T extends EntityLike> {
    @Final
    @Shadow
    private Class<T> entityClass;
    @Final
    @Shadow
    private Long2ObjectFunction<EntityTrackingStatus> posToStatus;
    @Final
    @Shadow
    private Long2ObjectMap<EntityTrackingSection<T>> trackingSections = new Long2ObjectOpenHashMap<>();

    @Unique
    private final ConcurrentSkipListSet<Long> trackedPositions = new ConcurrentSkipListSet<>(); // Use ConcurrentSkipListSet
    @Unique
    private final ReentrantLock lock = new ReentrantLock();

//    @Inject(method = "<init>", at = @At("TAIL"))
//    private void onInit(Class entityClass, Long2ObjectFunction chunkStatusDiscriminator, CallbackInfo ci) {
//        trackedPositions = new ConcurrentRadixTree<Long>();
//    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public void forEachInBox(Box box, LazyIterationConsumer<EntityTrackingSection<T>> consumer) {
        int i = 2;
        int j = ChunkSectionPos.getSectionCoord(box.minX - (double) 2.0F);
        int k = ChunkSectionPos.getSectionCoord(box.minY - (double) 4.0F);
        int l = ChunkSectionPos.getSectionCoord(box.minZ - (double) 2.0F);
        int m = ChunkSectionPos.getSectionCoord(box.maxX + (double) 2.0F);
        int n = ChunkSectionPos.getSectionCoord(box.maxY + (double) 0.0F);
        int o = ChunkSectionPos.getSectionCoord(box.maxZ + (double) 2.0F);

        for (int p = j; p <= m; ++p) {
            long q = ChunkSectionPos.asLong(p, 0, 0);
            long r = ChunkSectionPos.asLong(p, -1, -1);
            // Use subSet directly on ConcurrentSkipListSet (it's thread-safe)
            LongIterator longIterator = (LongIterator) this.trackedPositions.subSet(q, r + 1L).iterator();

            while (longIterator.hasNext()) {
                long s = longIterator.nextLong();
                int t = ChunkSectionPos.unpackY(s);
                int u = ChunkSectionPos.unpackZ(s);
                if (t >= k && t <= n && u >= l && u <= o) {
                    EntityTrackingSection<T> entityTrackingSection = (EntityTrackingSection<T>) this.trackingSections.get(s);
                    if (entityTrackingSection != null && !entityTrackingSection.isEmpty() && entityTrackingSection.getStatus().shouldTrack() && consumer.accept(entityTrackingSection).shouldAbort()) {
                        return;
                    }
                }
            }
        }

    }


    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public LongStream getSections(long chunkPos) {
        int i = ChunkPos.getPackedX(chunkPos);
        int j = ChunkPos.getPackedZ(chunkPos);
        LongSortedSet longSortedSet = this.getSections(i, j);
        if (longSortedSet.isEmpty()) {
            return LongStream.empty();
        } else {
            PrimitiveIterator.OfLong ofLong = longSortedSet.iterator();
            return StreamSupport.longStream(Spliterators.spliteratorUnknownSize(ofLong, 1301), false);
        }
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    private LongSortedSet getSections(int chunkX, int chunkZ) {
        long l = ChunkSectionPos.asLong(chunkX, 0, chunkZ);
        long m = ChunkSectionPos.asLong(chunkX, -1, chunkZ);
        return (LongSortedSet) this.trackedPositions.subSet(l, m + 1L); //Directly use subset
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public Stream<Object> getTrackingSections(long chunkPos) {
        LongStream var10000 = this.getSections(chunkPos);
        Long2ObjectMap var10001 = this.trackingSections;
        Objects.requireNonNull(var10001);
        return var10000.mapToObj(var10001::get).filter(Objects::nonNull);
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public static long chunkPosFromSectionPos(long sectionPos) {
        return ChunkPos.toLong(ChunkSectionPos.unpackX(sectionPos), ChunkSectionPos.unpackZ(sectionPos));
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public EntityTrackingSection<T> getTrackingSection(long sectionPos) {
        // computeIfAbsent is thread-safe on Long2ObjectOpenHashMap
        return (EntityTrackingSection<T>) this.trackingSections.computeIfAbsent(sectionPos, this::addSection);
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    @Nullable
    public EntityTrackingSection<T> findTrackingSection(long sectionPos) {
        // get is thread-safe on Long2ObjectOpenHashMap
        return (EntityTrackingSection) this.trackingSections.get(sectionPos);
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    private EntityTrackingSection<T> addSection(long sectionPos) {
        long l = chunkPosFromSectionPos(sectionPos);
        EntityTrackingStatus entityTrackingStatus = (EntityTrackingStatus) this.posToStatus.get(l);
        this.trackedPositions.add(sectionPos); // ConcurrentSkipListSet.add is thread-safe
        return new EntityTrackingSection(this.entityClass, entityTrackingStatus);
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public LongSet getChunkPositions() {
        LongSet longSet = new LongOpenHashSet();
        // Iterate over a snapshot of the keys (thread-safe)
        this.trackingSections.keySet().forEach((sectionPos) -> longSet.add(chunkPosFromSectionPos(sectionPos)));
        return longSet;
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public void forEachIntersects(Box box, LazyIterationConsumer<T> consumer) {
        this.forEachInBox(box, (section) -> section.forEach(box, consumer));
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public <U extends T> void forEachIntersects(TypeFilter<T, U> filter, Box box, LazyIterationConsumer<U> consumer) {
        this.forEachInBox(box, (section) -> section.forEach(filter, box, consumer));
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public void removeSection(long sectionPos) {
        this.trackingSections.remove(sectionPos); // Thread-safe on Long2ObjectOpenHashMap
        this.trackedPositions.remove(sectionPos); // ConcurrentSkipListSet.remove is thread-safe
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    @Debug
    public int sectionCount() {
        return this.trackedPositions.size(); // ConcurrentSkipListSet.size is thread-safe
    }
}