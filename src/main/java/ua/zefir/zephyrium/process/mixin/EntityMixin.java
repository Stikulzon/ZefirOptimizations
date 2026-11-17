package ua.zefir.zephyrium.process.mixin;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import ua.zefir.zephyrium.process.ProcessEntity;

@Mixin(Entity.class)
public abstract class EntityMixin implements ProcessEntity, Cloneable {
  @Shadow @Final @Mutable protected SynchedEntityData entityData;

  @Unique public volatile Thread zo$ownerThread;
  @Unique private volatile Object zo$processRef;
  @Unique private volatile Object zo$snapshotRef;
  @Unique private Object zo$liveRef;
  @Unique private static final VarHandle ZO$GATE = zo$gateHandle();
  @Unique private volatile int zo$gateCount;

  @Override
  public Thread zo$owner() {
    return zo$ownerThread;
  }

  @Override
  public void zo$setOwner(Thread owner) {
    zo$ownerThread = owner;
  }

  @Override
  public Object zo$process() {
    return zo$processRef;
  }

  @Override
  public void zo$setProcess(Object process) {
    zo$processRef = process;
  }

  @Override
  public Object zo$snapshot() {
    return zo$snapshotRef;
  }

  @Override
  public void zo$setSnapshot(Object snapshot) {
    zo$snapshotRef = snapshot;
  }

  @Override
  public Object zo$live() {
    return zo$liveRef;
  }

  @Override
  public void zo$setLive(Object live) {
    zo$liveRef = live;
  }

  @Unique
  private static VarHandle zo$gateHandle() {
    try {
      for (Field field : Entity.class.getDeclaredFields()) {
        if (field.getType() == int.class && field.getName().endsWith("zo$gateCount")) {
          return MethodHandles.privateLookupIn(Entity.class, MethodHandles.lookup())
              .unreflectVarHandle(field);
        }
      }
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
    throw new IllegalStateException("Entity has no read gate field");
  }

  @Override
  public int zo$readGate() {
    return zo$gateCount;
  }

  @Override
  public void zo$addReadGate(int delta) {
    ZO$GATE.getAndAdd(this, delta);
  }

  @Override
  public Object zo$cloneRaw() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void zo$setDataTracker(Object tracker) {
    this.entityData = (SynchedEntityData) tracker;
  }
}
