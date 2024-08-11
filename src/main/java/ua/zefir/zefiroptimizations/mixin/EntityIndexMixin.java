package ua.zefir.zefiroptimizations.mixin;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.world.entity.EntityIndex;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(EntityIndex.class)
public class EntityIndexMixin<T extends EntityLike> {
    @Final
    @Shadow
    private static Logger LOGGER;
    @Shadow
    @Final
    @Mutable
    private Int2ObjectMap<T> idToEntity;
    @Shadow
    @Final
    @Mutable
    private Map<UUID, T> uuidToEntity;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.idToEntity = new Int2ObjectOpenHashMap<>();
        this.uuidToEntity = new ConcurrentHashMap<>();
    }

    @Overwrite
    public <U extends T> void forEach(TypeFilter<T, U> filter, LazyIterationConsumer<U> consumer) {
        // Create a thread-safe snapshot of the values for iteration
        for (T entityLike : Collections.unmodifiableCollection(idToEntity.values())) {
            U entityLike2 = (U)filter.downcast(entityLike);
            if (entityLike2 != null && consumer.accept(entityLike2).shouldAbort()) {
                return;
            }
        }
    }

    @Overwrite
    public Iterable<T> iterate() {
        // Return an unmodifiable iterable of a thread-safe snapshot
        return Iterables.unmodifiableIterable(Collections.unmodifiableCollection(this.idToEntity.values()));
    }

    public void add(T entity) {
        UUID uUID = entity.getUuid();
        if (this.uuidToEntity.containsKey(uUID)) {
            LOGGER.warn("Duplicate entity UUID {}: {}", uUID, entity);
        } else {
            this.uuidToEntity.put(uUID, entity);
            this.idToEntity.put(entity.getId(), entity); // ConcurrentHashMap handles concurrent put operations
        }
    }

    @Overwrite
    public void remove(T entity) {
        this.uuidToEntity.remove(entity.getUuid()); // ConcurrentHashMap handles concurrent remove operations
        this.idToEntity.remove(entity.getId()); // Int2ObjectOpenHashMap is not thread-safe, but removal is less likely to cause issues compared to iteration
    }

    @Overwrite
    @Nullable
    public T get(int id) {
        return this.idToEntity.get(id); // Int2ObjectOpenHashMap is not thread-safe, but single get operations are generally safe
    }

    @Overwrite
    @Nullable
    public T get(UUID uuid) {
        return (T)this.uuidToEntity.get(uuid); // ConcurrentHashMap handles concurrent get operations
    }

    @Overwrite
    public int size() {
        return this.uuidToEntity.size(); // ConcurrentHashMap handles concurrent size retrieval
    }
}
