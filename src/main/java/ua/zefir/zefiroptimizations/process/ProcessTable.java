package ua.zefir.zefiroptimizations.process;

final class ProcessTable {
  private static final long EMPTY = 0L;
  private static final long TOMBSTONE = -1L;
  private static final int MIN_CAPACITY = 1024;
  private static final int LOAD_FACTOR_INVERSE = 2;
  private static final int GROWTH = 4;
  private static volatile Slots slots = new Slots(MIN_CAPACITY);
  private static int live;
  private static int used;

  private static final class Slots {
    final long[] ids;
    final EntityProcess[] processes;
    final int mask;

    Slots(int capacity) {
      ids = new long[capacity];
      processes = new EntityProcess[capacity];
      mask = capacity - 1;
    }
  }

  private static int slot(long threadId, int mask) {
    long h = threadId * 0x9E3779B97F4A7C15L;
    return (int) (h ^ (h >>> Integer.SIZE)) & mask;
  }

  static EntityProcess current() {
    Thread thread = Thread.currentThread();
    if (!thread.isVirtual()) {
      return null;
    }
    long id = thread.threadId();
    Slots s = slots;
    long[] ids = s.ids;
    int mask = s.mask;
    for (int i = slot(id, mask); ; i = (i + 1) & mask) {
      long entry = ids[i];
      if (entry == id) {
        return s.processes[i];
      }
      if (entry == EMPTY) {
        return null;
      }
    }
  }

  static synchronized void register(EntityProcess process) {
    if ((used + 1) * LOAD_FACTOR_INVERSE > slots.ids.length) {
      rehash(
          Math.max(
              MIN_CAPACITY,
              Integer.highestOneBit(Math.max(1, live + 1) * GROWTH) * LOAD_FACTOR_INVERSE));
    }
    Slots s = slots;
    long id = process.thread.threadId();
    int i = slot(id, s.mask);
    while (s.ids[i] != EMPTY && s.ids[i] != TOMBSTONE) {
      i = (i + 1) & s.mask;
    }
    if (s.ids[i] == EMPTY) {
      used++;
    }
    s.processes[i] = process;
    s.ids[i] = id;
    live++;
  }

  static synchronized void unregister(EntityProcess process) {
    Slots s = slots;
    long id = process.thread.threadId();
    for (int i = slot(id, s.mask); s.ids[i] != EMPTY; i = (i + 1) & s.mask) {
      if (s.ids[i] == id && s.processes[i] == process) {
        s.ids[i] = TOMBSTONE;
        s.processes[i] = null;
        live--;
        return;
      }
    }
  }

  private static void rehash(int capacity) {
    Slots old = slots;
    Slots fresh = new Slots(capacity);
    for (int k = 0; k < old.ids.length; k++) {
      EntityProcess process = old.processes[k];
      if (process == null) {
        continue;
      }
      long id = old.ids[k];
      int i = slot(id, fresh.mask);
      while (fresh.ids[i] != EMPTY) {
        i = (i + 1) & fresh.mask;
      }
      fresh.processes[i] = process;
      fresh.ids[i] = id;
    }
    used = live;
    slots = fresh;
  }
}
