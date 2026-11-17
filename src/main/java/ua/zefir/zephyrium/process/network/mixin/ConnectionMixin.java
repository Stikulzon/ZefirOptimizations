package ua.zefir.zephyrium.process.network.mixin;

import io.netty.channel.Channel;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import ua.zefir.zephyrium.process.network.BatchedFlush;
import ua.zefir.zephyrium.process.network.PacketFlushes;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements BatchedFlush {
  @Shadow private Channel channel;

  @Unique private final AtomicBoolean zo$flushPending = new AtomicBoolean();

  @Shadow
  public abstract void flushChannel();

  @Override
  public void zo$flushSoon() {
    PacketFlushes.flushSoon(channel, zo$flushPending, this::flushChannel);
  }
}
