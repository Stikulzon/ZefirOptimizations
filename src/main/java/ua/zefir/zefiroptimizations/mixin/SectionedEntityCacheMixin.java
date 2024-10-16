package ua.zefir.zefiroptimizations.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.util.TypeFilter;
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

@Mixin(value = SectionedEntityCache.class, priority = 800)
public abstract class SectionedEntityCacheMixin<T extends EntityLike> {
    @Final
    @Shadow
    private Class<T> entityClass;
    @Final
    @Shadow
    private Long2ObjectFunction<EntityTrackingStatus> posToStatus;
    @Unique
    private final ConcurrentHashMap<Long, EntityTrackingSection<T>> trackingSections = new ConcurrentHashMap<>();
    @Unique
    private final ConcurrentLongSortedSet trackedPositions = new ConcurrentLongSortedSet();

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public void forEachInBox(Box box, LazyIterationConsumer<EntityTrackingSection<T>> consumer) {
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
            // Use a snapshot for consistent iteration
            LongSortedSet subSet = trackedPositions.subSet(q, r + 1L);
            for (Long s : subSet) {
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
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public LongStream getSections(long chunkPos) {
        int i = ChunkPos.getPackedX(chunkPos);
        int j = ChunkPos.getPackedZ(chunkPos);
        return this.getSections(i, j).stream().mapToLong(Long::longValue);
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    private LongSortedSet getSections(int chunkX, int chunkZ) {
        long l = ChunkSectionPos.asLong(chunkX, 0, chunkZ);
        long m = ChunkSectionPos.asLong(chunkX, -1, chunkZ);
        return this.trackedPositions.subSet(l, m + 1L);
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public Stream<EntityTrackingSection<T>> getTrackingSections(long chunkPos) {
        return this.getSections(chunkPos).mapToObj(this.trackingSections::get).filter(Objects::nonNull);
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public EntityTrackingSection<T> getTrackingSection(long sectionPos) {
        return this.trackingSections.computeIfAbsent(sectionPos, this::addSection);
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    @Nullable
    public EntityTrackingSection<T> findTrackingSection(long sectionPos) {
        return this.trackingSections.get(sectionPos);
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
    public void forEachIntersects(Box box, LazyIterationConsumer<T> consumer) {
        this.forEachInBox(box, section -> section.forEach(box, consumer));
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public <U extends T> void forEachIntersects(TypeFilter<T, U> filter, Box box, LazyIterationConsumer<U> consumer) {
        this.forEachInBox(box, section -> section.forEach(filter, box, consumer));
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @Overwrite
    public void removeSection(long sectionPos) {
        this.trackingSections.computeIfPresent(sectionPos, (key, value) -> {
            trackedPositions.remove(key);
            return null;
        });
    }

    /**
     * @author Zefir
     * @reason Thread-safe SectionedEntityCache operations implementation
     */
    @net.minecraft.util.annotation.Debug
    @Overwrite
    @Debug
    public int sectionCount() {
        return this.trackedPositions.size();
    }


    @Shadow
    private static long chunkPosFromSectionPos(long sectionPos) {
        throw new UnsupportedOperationException();
    }
}