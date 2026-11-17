package ua.zefir.zefiroptimizations.process.brain;

import net.minecraft.world.entity.LivingEntity;
import ua.zefir.zefiroptimizations.config.ConfigService;
import ua.zefir.zefiroptimizations.process.Guards;
import ua.zefir.zefiroptimizations.process.Invocation;

public final class MemoryCasts {
  public static boolean castTo(LivingEntity entity, Invocation write) {
    if (!ConfigService.get().castMemoryWrites || !Guards.route(entity)) {
      return false;
    }
    Guards.castUntracked(entity, write);
    return true;
  }
}
