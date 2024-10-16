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

    /**
     * @author Zefir
     * @reason Thread-safe EntityIndex operations implementation
     */
    @Overwrite
    public <U extends T> void forEach(TypeFilter<T, U> filter, LazyIterationConsumer<U> consumer) {
        for (T entityLike : Collections.unmodifiableCollection(idToEntity.values())) {
            U entityLike2 = filter.downcast(entityLike);
            if (entityLike2 != null && consumer.accept(entityLike2).shouldAbort()) {
                return;
            }
        }
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityIndex operations implementation
     */
    @Overwrite
    public Iterable<T> iterate() {
        return Iterables.unmodifiableIterable(Collections.unmodifiableCollection(this.idToEntity.values()));
    }

//    public void add(T entity) {
//        UUID uUID = entity.getUuid();
//        if (this.uuidToEntity.containsKey(uUID)) {
//            LOGGER.warn("Duplicate entity UUID {}: {}", uUID, entity);
//        } else {
//            this.uuidToEntity.put(uUID, entity);
//            this.idToEntity.put(entity.getId(), entity);
//        }
//    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityIndex operations implementation
     */
    @Overwrite
    public void remove(T entity) {
        this.uuidToEntity.remove(entity.getUuid());
        this.idToEntity.remove(entity.getId());
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityIndex operations implementation
     */
    @Overwrite
    @Nullable
    public T get(int id) {
        return this.idToEntity.get(id);
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityIndex operations implementation
     */
    @Overwrite
    @Nullable
    public T get(UUID uuid) {
        return (T)this.uuidToEntity.get(uuid);
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityIndex operations implementation
     */
    @Overwrite
    public int size() {
        return this.uuidToEntity.size();
    }
}
