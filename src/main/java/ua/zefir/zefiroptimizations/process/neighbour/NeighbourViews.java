package ua.zefir.zefiroptimizations.process.neighbour;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ua.zefir.zefiroptimizations.config.ConfigService;
import ua.zefir.zefiroptimizations.process.EntityProcess;
import ua.zefir.zefiroptimizations.process.neighbour.mixin.LevelEntitiesInvoker;
import ua.zefir.zefiroptimizations.process.neighbour.mixin.LevelEntityGetterAdapterAccessor;
import ua.zefir.zefiroptimizations.process.world.WorldSeq;

public final class NeighbourViews {
  public static List<LivingEntity> nearestLiving(ServerLevel level, LivingEntity viewer, AABB box) {
    if (!ConfigService.get().neighbourViews
        || !(((LevelEntitiesInvoker) level).invokeGetEntities()
            instanceof LevelEntityGetterAdapterAccessor adapter)) {
      return null;
    }
    EntityProcess process = EntityProcess.current();
    if (process == null) {
      return null;
    }
    EntitySectionStorage<?> storage = adapter.getSectionStorage();
    NeighbourBuffer buffer = process.neighbours;
    Vec3 from = viewer.position();
    WorldSeq.read(
        () -> {
          buffer.clear();
          storage.forEachAccessibleNonEmptySection(
              box,
              section -> {
                ((IndexedSection) section)
                    .zo$sectionIndex()
                    .living()
                    .collect(box, viewer, from, buffer);
                return AbortableIterationConsumer.Continuation.CONTINUE;
              });
          return buffer;
        });
    List<LivingEntity> sorted = buffer.sortedByDistance();
    buffer.clear();
    return sorted;
  }

  public static boolean isPresorted(List<?> list) {
    return list instanceof NeighbourBuffer.PresortedList<?>;
  }
}
