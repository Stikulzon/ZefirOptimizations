package ua.zefir.zephyrium.process.neighbour;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.ToDoubleFunction;

public final class StableComparators {
  public static <T> Comparator<T> comparingDouble(ToDoubleFunction<? super T> key) {
    return new KeyComparator<>(key);
  }

  public static <T> boolean sortIfKeyed(List<T> list, Comparator<? super T> comparator) {
    if (!(comparator instanceof KeyComparator<? super T> keyed)) {
      return false;
    }
    keyed.sort(list);
    return true;
  }

  private record Keyed(double key, Object item) {}

  private static final class KeyComparator<T> implements Comparator<T> {
    private static final Comparator<Keyed> BY_KEY = Comparator.comparingDouble(Keyed::key);

    private final ToDoubleFunction<? super T> key;
    private KeyCache<T> keys;

    KeyComparator(ToDoubleFunction<? super T> key) {
      this.key = key;
    }

    @Override
    public int compare(T a, T b) {
      if (keys == null) {
        keys = new KeyCache<>(key);
      }
      return Double.compare(keys.get(a), keys.get(b));
    }

    @SuppressWarnings("unchecked")
    <U extends T> void sort(List<U> list) {
      Keyed[] keyed = new Keyed[list.size()];
      int i = 0;
      for (U item : list) {
        keyed[i++] = new Keyed(key.applyAsDouble(item), item);
      }
      Arrays.sort(keyed, BY_KEY);
      ListIterator<U> it = list.listIterator();
      for (Keyed entry : keyed) {
        it.next();
        it.set((U) entry.item());
      }
    }
  }

  private static final class KeyCache<T> {
    private static final int INITIAL_CAPACITY = 32;
    private static final int LOAD_FACTOR_INVERSE = 2;
    private static final int GOLDEN_RATIO = 0x9E3779B9;

    private final ToDoubleFunction<? super T> key;
    private Object[] items = new Object[INITIAL_CAPACITY];
    private double[] values = new double[INITIAL_CAPACITY];
    private int size;

    KeyCache(ToDoubleFunction<? super T> key) {
      this.key = key;
    }

    double get(T item) {
      int mask = items.length - 1;
      for (int i = slot(item, mask); ; i = (i + 1) & mask) {
        Object present = items[i];
        if (present == item) {
          return values[i];
        }
        if (present == null) {
          double value = key.applyAsDouble(item);
          items[i] = item;
          values[i] = value;
          if (++size * LOAD_FACTOR_INVERSE > items.length) {
            grow();
          }
          return value;
        }
      }
    }

    private static int slot(Object item, int mask) {
      return (System.identityHashCode(item) * GOLDEN_RATIO >>> Integer.SIZE / 2) & mask;
    }

    private void grow() {
      Object[] oldItems = items;
      double[] oldValues = values;
      items = new Object[oldItems.length * LOAD_FACTOR_INVERSE];
      values = new double[items.length];
      int mask = items.length - 1;
      for (int j = 0; j < oldItems.length; j++) {
        Object item = oldItems[j];
        if (item == null) {
          continue;
        }
        int i = slot(item, mask);
        while (items[i] != null) {
          i = (i + 1) & mask;
        }
        items[i] = item;
        values[i] = oldValues[j];
      }
    }
  }
}
