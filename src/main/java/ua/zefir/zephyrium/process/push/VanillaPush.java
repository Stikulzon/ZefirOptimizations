package ua.zefir.zephyrium.process.push;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import ua.zefir.zephyrium.process.Processes;

public final class VanillaPush {
  public static final float MIN_DISTANCE = 0.01F;
  public static final float STRENGTH = 0.05F;

  /*? if >=26.1 {*/
  private static final String PUSH_AWAY_FROM = "push";
  /*?} else {*/
  /*private static final String PUSH_AWAY_FROM =
      net.fabricmc.loader.api.FabricLoader.getInstance()
          .getMappingResolver()
          .mapMethodName(
              "intermediary",
              "net.minecraft.class_1297",
              "method_5697",
              "(Lnet/minecraft/class_1297;)V");
  *//*?}*/

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
  private static final MethodType PUSH_TYPE = MethodType.methodType(void.class, Entity.class);

  private static final ClassValue<Boolean> VANILLA =
      new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
          try {
            Class<?> declaring =
                LOOKUP
                    .revealDirect(LOOKUP.findVirtual(type, PUSH_AWAY_FROM, PUSH_TYPE))
                    .getDeclaringClass();
            return declaring == Entity.class || declaring == LivingEntity.class;
          } catch (ReflectiveOperationException e) {
            return false;
          } catch (RuntimeException | LinkageError e) {
            Processes.LOGGER.warn(
                "Could not inspect how {} pushes entities; it is pushed without the fast path",
                type.getName(),
                e);
            return false;
          }
        }
      };

  public static boolean is(Entity entity) {
    return VANILLA.get(entity.getClass());
  }
}
