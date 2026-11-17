package ua.zefir.zephyrium.process.network;

import io.netty.channel.Channel;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.zefir.zephyrium.config.ConfigService;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.sharedstate.SharedState;

public final class PacketFlushes {
  private static final Logger LOGGER = LoggerFactory.getLogger("Zephyrium/network");

  public static boolean deferred() {
    return ConfigService.get().batchPacketFlushes && Processes.inProcess();
  }

  public static boolean interceptedOnServerThread(Object connection) {
    return SharedState.routes(connection.getClass());
  }

  public static void flushSoon(Channel channel, AtomicBoolean pending, Runnable flush) {
    if (channel == null || !pending.compareAndSet(false, true)) {
      return;
    }
    try {
      channel
          .eventLoop()
          .execute(
              () -> {
                pending.set(false);
                channel.eventLoop().execute(flush);
              });
    } catch (RejectedExecutionException e) {
      pending.set(false);
      LOGGER.debug("Could not flush {}: its event loop is shut down", channel, e);
    }
  }
}
