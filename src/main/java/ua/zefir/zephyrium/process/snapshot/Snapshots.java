package ua.zefir.zephyrium.process.snapshot;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import ua.zefir.zephyrium.config.ConfigService;
import ua.zefir.zephyrium.process.Guards;
import ua.zefir.zephyrium.process.ProcessEntity;

public final class Snapshots {
  public static void publish(Entity entity) {
    ProcessEntity live = (ProcessEntity) entity;
    if (live.zo$live() != null) {
      return;
    }
    SynchedEntityData previous =
        live.zo$snapshot() instanceof Entity old ? old.getEntityData() : null;
    Object raw = live.zo$cloneRaw();
    ProcessEntity snapshot = (ProcessEntity) raw;
    snapshot.zo$setOwner(Guards.SNAPSHOT);
    snapshot.zo$setProcess(null);
    snapshot.zo$setSnapshot(null);
    snapshot.zo$setLive(entity);
    snapshot.zo$setDataTracker(snapshotData(entity.getEntityData(), previous));
    live.zo$setSnapshot(raw);
  }

  private static SynchedEntityData snapshotData(
      SynchedEntityData tracker, SynchedEntityData previous) {
    int version = ((VersionedData) tracker).zo$version();
    if (previous != null
        && ConfigService.get().skipUnchangedSnapshotData
        && ((VersionedData) previous).zo$copiedVersion() == version
        && previous.itemsById.length == tracker.itemsById.length) {
      return previous;
    }
    SynchedEntityData copied = copy(tracker, previous);
    ((VersionedData) copied).zo$setCopiedVersion(version);
    return copied;
  }

  private static SynchedEntityData copy(SynchedEntityData tracker, SynchedEntityData previous) {
    SynchedEntityData.DataItem<?>[] source = tracker.itemsById;
    SynchedEntityData.DataItem<?>[] reuse =
        previous != null && previous.itemsById.length == source.length ? previous.itemsById : null;
    SynchedEntityData.DataItem<?>[] copy = null;
    for (int i = 0; i < source.length; i++) {
      if (source[i] == null) {
        continue;
      }
      if (reuse != null
          && reuse[i] != null
          && reuse[i].getValue() == source[i].getValue()
          && reuse[i].getAccessor() == source[i].getAccessor()) {
        if (copy != null) {
          copy[i] = reuse[i];
        }
        continue;
      }
      if (copy == null) {
        copy = new SynchedEntityData.DataItem<?>[source.length];
        if (reuse != null) {
          System.arraycopy(reuse, 0, copy, 0, i);
        }
      }
      copy[i] = copyEntry(source[i]);
    }
    if (copy == null) {
      return reuse != null
          ? previous
          : new SynchedEntityData(tracker.entity, new SynchedEntityData.DataItem<?>[source.length]);
    }
    return new SynchedEntityData(tracker.entity, copy);
  }

  private static <T> SynchedEntityData.DataItem<T> copyEntry(SynchedEntityData.DataItem<T> entry) {
    return new SynchedEntityData.DataItem<>(entry.getAccessor(), entry.getValue());
  }
}
