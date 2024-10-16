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

    /**
     * @author Zefir
     * @reason Thread-safe EntityList operations implementation
     */
    @Overwrite
    public void add(Entity entity) {
        Int2ObjectMap<Entity> newEntities = new Int2ObjectLinkedOpenHashMap<>(this.entities);
        newEntities.put(entity.getId(), entity);
        this.entities = newEntities;
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityList operations implementation
     */
    @Overwrite
    public void remove(Entity entity) {
        Int2ObjectMap<Entity> newEntities = new Int2ObjectLinkedOpenHashMap<>(this.entities);
        newEntities.remove(entity.getId());
        this.entities = newEntities;
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityList operations implementation
     */
    @Overwrite
    public boolean has(Entity entity) {
        return this.entities.containsKey(entity.getId());
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityList operations implementation
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
