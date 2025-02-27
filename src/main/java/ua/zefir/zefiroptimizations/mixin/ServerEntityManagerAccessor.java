package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;
import java.util.UUID;

@Mixin(ServerEntityManager.class)
public interface ServerEntityManagerAccessor<T extends EntityLike> {
    @Accessor("cache")
    SectionedEntityCache<T> getCache();
    @Accessor("entityUuids")
    Set<UUID> getEntityUuids();

    @Invoker("entityLeftSection")
    void invokeEntityLeftSection(long sectionPos, EntityTrackingSection<T> section);
    @Invoker("startTicking")
    void invokeStartTicking(T entity);
    @Invoker("stopTicking")
    void invokeStopTicking(T entity);
    @Invoker("startTracking")
    void invokeStartTracking(T entity);
    @Invoker("stopTracking")
    void invokeStopTracking(T entity);
}
