package ua.zefir.zephyrium.process;

import net.minecraft.world.level.Level;

final class McBridge {
  static Object worldRandom(Object world) {
    EntityProcess process = EntityProcess.current();
    return process != null ? process.random : ((Level) world).random;
  }
}
