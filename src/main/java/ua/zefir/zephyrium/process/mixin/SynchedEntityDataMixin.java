package ua.zefir.zephyrium.process.mixin;

import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zephyrium.process.snapshot.VersionedData;

@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataMixin implements VersionedData {
  @Unique private static final int NEVER_COPIED = -1;

  @Unique private int zo$version;

  @Unique private int zo$copiedVersion = NEVER_COPIED;

  @Inject(
      method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
      at = @At("HEAD"))
  private <T> void zo$changed(
      EntityDataAccessor<T> accessor, T value, boolean force, CallbackInfo ci) {
    zo$version++;
  }

  @Inject(method = "assignValues", at = @At("HEAD"))
  private void zo$assigned(List<SynchedEntityData.DataValue<?>> values, CallbackInfo ci) {
    zo$version++;
  }

  @Override
  public int zo$version() {
    return zo$version;
  }

  @Override
  public int zo$copiedVersion() {
    return zo$copiedVersion;
  }

  @Override
  public void zo$setCopiedVersion(int version) {
    zo$copiedVersion = version;
  }
}
