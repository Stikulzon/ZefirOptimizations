package ua.zefir.zephyrium.process.network.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.network.BatchedFlush;
import ua.zefir.zephyrium.process.network.PacketFlushes;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
  /*? if >=26.1 {*/
  @WrapOperation(
      method =
          "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"))
  private void zo$batchFlush(
      Connection connection,
      Packet<?> packet,
      io.netty.channel.ChannelFutureListener listener,
      boolean flush,
      Operation<Void> original) {
    /*?} else {*/
    /*@WrapOperation(
        method =
            "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
        at =
            @At(
                value = "INVOKE",
                target =
                    "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V"))
    private void zo$batchFlush(
        Connection connection,
        Packet<?> packet,
        net.minecraft.network.PacketSendListener listener,
        boolean flush,
        Operation<Void> original) {
    *//*?}*/
    if (!PacketFlushes.deferred()) {
      original.call(connection, packet, listener, flush);
      return;
    }
    if (PacketFlushes.interceptedOnServerThread(connection)) {
      Processes.castMain(() -> zo$sendBatched(connection, packet, listener, flush, original));
      return;
    }
    zo$sendBatched(connection, packet, listener, flush, original);
  }

  @Unique
  private static void zo$sendBatched(
      Connection connection,
      Packet<?> packet,
      Object listener,
      boolean flush,
      Operation<Void> original) {
    original.call(connection, packet, listener, false);
    if (flush) {
      ((BatchedFlush) connection).zo$flushSoon();
    }
  }
}
