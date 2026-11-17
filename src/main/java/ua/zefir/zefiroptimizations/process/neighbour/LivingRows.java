package ua.zefir.zefiroptimizations.process.neighbour;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LivingRows {
  private final SectionIndex index;
  private final int[] rows;
  private final LivingEntity[] entities;
  private final double[] x;
  private final double[] y;
  private final double[] z;

  private LivingRows(
      SectionIndex index, int[] rows, LivingEntity[] entities, double[] x, double[] y, double[] z) {
    this.index = index;
    this.rows = rows;
    this.entities = entities;
    this.x = x;
    this.y = y;
    this.z = z;
  }

  static LivingRows of(SectionIndex index) {
    Object[] all = index.entities;
    int[] rows = new int[all.length];
    LivingEntity[] entities = new LivingEntity[all.length];
    double[] x = new double[all.length];
    double[] y = new double[all.length];
    double[] z = new double[all.length];
    int n = 0;
    for (int i = 0; i < all.length; i++) {
      if (!(all[i] instanceof LivingEntity living) || !living.isAlive()) {
        continue;
      }
      Vec3 position = living.position();
      rows[n] = i;
      entities[n] = living;
      x[n] = position.x;
      y[n] = position.y;
      z[n] = position.z;
      n++;
    }
    return new LivingRows(index, rows, entities, x, y, z);
  }

  void collect(AABB box, LivingEntity viewer, Vec3 from, NeighbourBuffer out) {
    for (int k = 0; k < entities.length; k++) {
      LivingEntity entity = entities[k];
      if (entity == null) {
        return;
      }
      if (entity == viewer || !index.intersects(rows[k], box)) {
        continue;
      }
      double dx = from.x - x[k];
      double dy = from.y - y[k];
      double dz = from.z - z[k];
      out.add(entity, dx * dx + dy * dy + dz * dz);
    }
  }
}
