package ua.zefir.zefiroptimizations.process.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import ua.zefir.zefiroptimizations.process.Processes;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
  /*? if >=26.1 {*/
  @WrapMethod(method = "sendToTrackingPlayers")
  private void zo$sendToOtherNearbyPlayers(
      Entity entity, Packet<?> packet, Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(entity, packet));
      return;
    }
    original.call(entity, packet);
  }

  @WrapMethod(method = "sendToTrackingPlayersFiltered")
  private void zo$sendToFilteredNearbyPlayers(
      Entity entity,
      Packet<?> packet,
      java.util.function.Predicate<net.minecraft.server.level.ServerPlayer> filter,
      Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(entity, packet, filter));
      return;
    }
    original.call(entity, packet, filter);
  }

  @WrapMethod(method = "sendToTrackingPlayersAndSelf")
  private void zo$sendToNearbyPlayers(Entity entity, Packet<?> packet, Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(entity, packet));
      return;
    }
    original.call(entity, packet);
  }
  /*?} else {*/
    /*@WrapMethod(method = "broadcast")
  private void zo$sendToOtherNearbyPlayers(
      Entity entity, Packet<?> packet, Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(entity, packet));
      return;
    }
    original.call(entity, packet);
  }

  @WrapMethod(method = "broadcastAndSend")
  private void zo$sendToNearbyPlayers(Entity entity, Packet<?> packet, Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(entity, packet));
      return;
    }
    original.call(entity, packet);
  }
  *//*?}*/
}
