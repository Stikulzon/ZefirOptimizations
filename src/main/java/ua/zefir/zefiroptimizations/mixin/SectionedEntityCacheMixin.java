package ua.zefir.zefiroptimizations.mixin;

import it.unimi.dsi.fastutil.longs.*;

import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
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
import ua.zefir.zefiroptimizations.data.ConcurrentLongSortedSet;

@Mixin(value = SectionedEntityCache.class)
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
    @Final
    @Shadow
    private LongSortedSet trackedPositions = new LongAVLTreeSet();

    @Unique
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public void forEachInBox(Box box, LazyIterationConsumer<EntityTrackingSection<T>> consumer) {
        lock.lock();
        try {
            int i = 2;
            int j = ChunkSectionPos.getSectionCoord(box.minX - 2.0);
            int k = ChunkSectionPos.getSectionCoord(box.minY - 4.0);
            int l = ChunkSectionPos.getSectionCoord(box.minZ - 2.0);
            int m = ChunkSectionPos.getSectionCoord(box.maxX + 2.0);
            int n = ChunkSectionPos.getSectionCoord(box.maxY + 0.0);
            int o = ChunkSectionPos.getSectionCoord(box.maxZ + 2.0);

            for (int p = j; p <= m; p++) {
                long q = ChunkSectionPos.asLong(p, 0, 0);
                long r = ChunkSectionPos.asLong(p, -1, -1);
                LongIterator longIterator = this.trackedPositions.subSet(q, r + 1L).iterator();

                while (longIterator.hasNext()) {
                    long s = longIterator.nextLong();
                    int t = ChunkSectionPos.unpackY(s);
                    int u = ChunkSectionPos.unpackZ(s);
                    if (t >= k && t <= n && u >= l && u <= o) {
                        EntityTrackingSection<T> entityTrackingSection = this.trackingSections.get(s);
                        if (entityTrackingSection != null
                                && !entityTrackingSection.isEmpty()
                                && entityTrackingSection.getStatus().shouldTrack()
                                && consumer.accept(entityTrackingSection).shouldAbort()) {
                            return;
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
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

        lock.lock();
        try {
            return this.trackedPositions.subSet(l, m + 1L);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public Stream<EntityTrackingSection<T>> getTrackingSections(long chunkPos) {
        lock.lock();
        try {
            return this.getSections(chunkPos).mapToObj(this.trackingSections::get).filter(Objects::nonNull);
        } finally {
            lock.unlock();
        }
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
        lock.lock();
        try {
            return this.trackingSections.computeIfAbsent(sectionPos, this::addSection);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    @Nullable
    public EntityTrackingSection<T> findTrackingSection(long sectionPos) {
        lock.lock();
        try {
            return this.trackingSections.get(sectionPos);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    private EntityTrackingSection<T> addSection(long sectionPos) {
        long l = chunkPosFromSectionPos(sectionPos);
        EntityTrackingStatus entityTrackingStatus = this.posToStatus.get(l);
        this.trackedPositions.add(sectionPos);
        return new EntityTrackingSection<>(this.entityClass, entityTrackingStatus);
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public LongSet getChunkPositions() {
        LongSet longSet = new LongOpenHashSet();
        lock.lock();
        try {
            this.trackingSections.keySet().forEach(sectionPos -> longSet.add(chunkPosFromSectionPos(sectionPos)));
        } finally {
            lock.unlock();
        }
        return longSet;
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public void forEachIntersects(Box box, LazyIterationConsumer<T> consumer) {
        lock.lock();
        try {
            this.forEachInBox(box, section -> section.forEach(box, consumer));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public <U extends T> void forEachIntersects(TypeFilter<T, U> filter, Box box, LazyIterationConsumer<U> consumer) {
        lock.lock();
        try {
            this.forEachInBox(box, section -> section.forEach(filter, box, consumer));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public void removeSection(long sectionPos) {
        lock.lock();
        try {
            this.trackingSections.remove(sectionPos);
            this.trackedPositions.remove(sectionPos);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    @Debug
    public int sectionCount() {
        return this.trackedPositions.size();
    }
}