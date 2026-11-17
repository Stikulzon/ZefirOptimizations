package ua.zefir.zephyrium.process.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import ua.zefir.zephyrium.process.Processes;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
  @WrapMethod(method = "broadcast")
  private void zo$sendToAround(
      @Nullable Player player,
      double x,
      double y,
      double z,
      double distance,
      ResourceKey<Level> worldKey,
      Packet<?> packet,
      Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(player, x, y, z, distance, worldKey, packet));
      return;
    }
    original.call(player, x, y, z, distance, worldKey, packet);
  }

  @WrapMethod(method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;)V")
  private void zo$sendToAll(Packet<?> packet, Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(packet));
      return;
    }
    original.call(packet);
  }

  @WrapMethod(
      method =
          "broadcastAll(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/resources/ResourceKey;)V")
  private void zo$sendToDimension(
      Packet<?> packet, ResourceKey<Level> dimension, Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(packet, dimension));
      return;
    }
    original.call(packet, dimension);
  }
}
