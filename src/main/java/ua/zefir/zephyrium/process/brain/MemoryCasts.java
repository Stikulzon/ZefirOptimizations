package ua.zefir.zephyrium.process.brain;

import net.minecraft.world.entity.LivingEntity;
import ua.zefir.zephyrium.config.ConfigService;
import ua.zefir.zephyrium.process.Guards;
import ua.zefir.zephyrium.process.Invocation;

public final class MemoryCasts {
  public static boolean castTo(LivingEntity entity, Invocation write) {
    if (!ConfigService.get().castMemoryWrites || !Guards.route(entity)) {
      return false;
    }
    Guards.castUntracked(entity, write);
    return true;
  }
}
