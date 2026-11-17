package ua.zefir.zefiroptimizations.process.neighbour.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.process.Processes;
import ua.zefir.zefiroptimizations.process.neighbour.IndexedSection;
import ua.zefir.zefiroptimizations.process.neighbour.SectionIndex;

@Mixin(EntitySection.class)
public abstract class EntitySectionMixin<T extends EntityAccess> implements IndexedSection {
  @Shadow @Final private ClassInstanceMultiMap<T> storage;

  @Unique private volatile int zo$mod;

  @Unique private volatile SectionIndex zo$index;

  @Inject(method = "add", at = @At("HEAD"))
  private void zo$added(T entity, CallbackInfo ci) {
    zo$mod++;
  }

  @Inject(method = "remove", at = @At("HEAD"))
  private void zo$removed(T entity, CallbackInfoReturnable<Boolean> cir) {
    zo$mod++;
  }

  @Override
  public SectionIndex zo$sectionIndex() {
    return zo$index();
  }

  @Unique
  private SectionIndex zo$index() {
    int epoch = SectionIndex.epoch();
    int mod = zo$mod;
    SectionIndex index = zo$index;
    if (index != null && index.validFor(epoch, mod)) {
      return index;
    }
    Object[] entities = storage.toArray();
    AABB[] boxes = new AABB[entities.length];
    for (int i = 0; i < entities.length; i++) {
      boxes[i] = ((EntityAccess) entities[i]).getBoundingBox();
    }
    index = new SectionIndex(epoch, mod, entities, boxes);
    zo$index = index;
    return index;
  }

  @WrapMethod(
      method =
          "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;")
  @SuppressWarnings("unchecked")
  private AbortableIterationConsumer.Continuation zo$forEach(
      AABB box,
      AbortableIterationConsumer<T> consumer,
      Operation<AbortableIterationConsumer.Continuation> original) {
    if (!Processes.enabled()) {
      return original.call(box, consumer);
    }
    boolean live = Processes.onMain();
    SectionIndex index = zo$index();
    Object[] entities = index.entities;
    for (int i = 0; i < entities.length; i++) {
      T entity = (T) entities[i];
      boolean hit = live ? entity.getBoundingBox().intersects(box) : index.intersects(i, box);
      if (hit && consumer.accept(entity).shouldAbort()) {
        return AbortableIterationConsumer.Continuation.ABORT;
      }
    }
    return AbortableIterationConsumer.Continuation.CONTINUE;
  }

  @WrapMethod(
      method =
          "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;")
  @SuppressWarnings({"unchecked", "rawtypes"})
  private AbortableIterationConsumer.Continuation zo$forEachOfType(
      EntityTypeTest type,
      AABB box,
      AbortableIterationConsumer consumer,
      Operation<AbortableIterationConsumer.Continuation> original) {
    if (!Processes.enabled()) {
      return original.call(type, box, consumer);
    }
    boolean live = Processes.onMain();
    SectionIndex index = zo$index();
    Object[] entities = index.entities;
    Class<?> base = type.getBaseClass();
    for (int i = 0; i < entities.length; i++) {
      Object entity = entities[i];
      if (!base.isInstance(entity)) {
        continue;
      }
      boolean hit =
          live
              ? ((EntityAccess) entity).getBoundingBox().intersects(box)
              : index.intersects(i, box);
      if (!hit) {
        continue;
      }
      Object cast = type.tryCast(entity);
      if (cast != null && consumer.accept(cast).shouldAbort()) {
        return AbortableIterationConsumer.Continuation.ABORT;
      }
    }
    return AbortableIterationConsumer.Continuation.CONTINUE;
  }
}
