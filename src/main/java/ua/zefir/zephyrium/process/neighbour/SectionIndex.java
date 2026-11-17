package ua.zefir.zephyrium.process.neighbour;

import net.minecraft.world.phys.AABB;

public final class SectionIndex {
  private static final int STRIDE = 6;
  private static final int MIN_X = 0;
  private static final int MIN_Y = 1;
  private static final int MIN_Z = 2;
  private static final int MAX_X = 3;
  private static final int MAX_Y = 4;
  private static final int MAX_Z = 5;

  private static volatile int epoch;

  private final int builtEpoch;
  private final int builtMod;
  public final Object[] entities;
  private final double[] bounds;
  private volatile LivingRows living;

  public SectionIndex(int epoch, int mod, Object[] entities, AABB[] boxes) {
    this.builtEpoch = epoch;
    this.builtMod = mod;
    this.entities = entities;
    double[] b = new double[entities.length * STRIDE];
    for (int i = 0; i < entities.length; i++) {
      AABB box = boxes[i];
      int k = i * STRIDE;
      b[k + MIN_X] = box.minX;
      b[k + MIN_Y] = box.minY;
      b[k + MIN_Z] = box.minZ;
      b[k + MAX_X] = box.maxX;
      b[k + MAX_Y] = box.maxY;
      b[k + MAX_Z] = box.maxZ;
    }
    this.bounds = b;
  }

  public static void nextTick() {
    epoch++;
  }

  public static int epoch() {
    return epoch;
  }

  public boolean validFor(int epoch, int mod) {
    return builtEpoch == epoch && builtMod == mod;
  }

  public boolean intersects(int i, AABB box) {
    int k = i * STRIDE;
    double[] b = bounds;
    return b[k + MIN_X] < box.maxX
        && b[k + MAX_X] > box.minX
        && b[k + MIN_Y] < box.maxY
        && b[k + MAX_Y] > box.minY
        && b[k + MIN_Z] < box.maxZ
        && b[k + MAX_Z] > box.minZ;
  }

  public LivingRows living() {
    LivingRows rows = living;
    if (rows == null) {
      rows = LivingRows.of(this);
      living = rows;
    }
    return rows;
  }
}
