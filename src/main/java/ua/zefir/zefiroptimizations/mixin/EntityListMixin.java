package ua.zefir.zefiroptimizations.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.world.EntityList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Consumer;

@Mixin(value = EntityList.class)
public class EntityListMixin {
    @Shadow
    private Int2ObjectMap<Entity> entities;

    @Overwrite
    public void add(Entity entity) {
        Int2ObjectMap<Entity> newEntities = new Int2ObjectLinkedOpenHashMap<>(this.entities);
        newEntities.put(entity.getId(), entity);
        this.entities = newEntities;
    }

    @Overwrite
    public void remove(Entity entity) {
        Int2ObjectMap<Entity> newEntities = new Int2ObjectLinkedOpenHashMap<>(this.entities);
        newEntities.remove(entity.getId());
        this.entities = newEntities;
    }

    @Overwrite
    public boolean has(Entity entity) {
        return this.entities.containsKey(entity.getId());
    }

    /**
     * Runs an {@code action} on every entity in this storage.
     *
     * <p>If this storage is updated during the iteration, the iteration will
     * not be updated to reflect updated contents. For example, if an entity
     * is added by the {@code action}, the {@code action} won't run on that
     * entity later.
     */
    @Overwrite
    public void forEach(Consumer<Entity> action) {
        // Create a local copy of the entities map for iteration
        Int2ObjectMap<Entity> entitiesCopy = this.entities;

        for (Entity entity : entitiesCopy.values()) {
            action.accept(entity);
        }
    }
}
