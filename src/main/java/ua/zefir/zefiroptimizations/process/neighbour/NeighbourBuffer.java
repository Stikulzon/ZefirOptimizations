package ua.zefir.zefiroptimizations.process.neighbour;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;

public final class NeighbourBuffer {
  private static final int INITIAL_CAPACITY = 64;
  private static final int GROWTH = 2;

  private LivingEntity[] entities = new LivingEntity[INITIAL_CAPACITY];
  private double[] distances = new double[INITIAL_CAPACITY];
  private static final int INDEX_BITS = Integer.SIZE;
  private static final long INDEX_MASK = (1L << INDEX_BITS) - 1;

  private long[] order = new long[INITIAL_CAPACITY];
  private int size;

  void clear() {
    Arrays.fill(entities, 0, size, null);
    size = 0;
  }

  void add(LivingEntity entity, double distance) {
    if (size == entities.length) {
      entities = Arrays.copyOf(entities, size * GROWTH);
      distances = Arrays.copyOf(distances, size * GROWTH);
    }
    entities[size] = entity;
    distances[size] = distance;
    size++;
  }

  List<LivingEntity> sortedByDistance() {
    if (order.length < size) {
      order = new long[Math.max(size, order.length * GROWTH)];
    }
    for (int i = 0; i < size; i++) {
      long key = Float.floatToIntBits((float) distances[i]);
      order[i] = key << INDEX_BITS | i;
    }
    Arrays.sort(order, 0, size);
    PresortedList<LivingEntity> list = new PresortedList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(entities[(int) (order[i] & INDEX_MASK)]);
    }
    return list;
  }

  static final class PresortedList<E> extends ArrayList<E> {
    PresortedList(int capacity) {
      super(capacity);
    }
  }
}
