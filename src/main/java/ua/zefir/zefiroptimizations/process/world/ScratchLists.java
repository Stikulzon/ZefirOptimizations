package ua.zefir.zefiroptimizations.process.world;

import java.util.ArrayList;
import ua.zefir.zefiroptimizations.process.EntityProcess;

public final class ScratchLists {
  private static final int DEPTH = 4;
  private static final int INITIAL_CAPACITY = 64;

  private static final int KEEP_MAX = 2048;

  @SuppressWarnings("unchecked")
  private final ArrayList<Object>[] lists = new ArrayList[DEPTH];

  private int depth;

  public static ArrayList<Object> take() {
    EntityProcess process = EntityProcess.current();
    if (process == null) {
      return new ArrayList<>(INITIAL_CAPACITY);
    }
    ScratchLists s = process.scratchLists;
    int d = s.depth;
    if (d == DEPTH) {
      return new ArrayList<>(INITIAL_CAPACITY);
    }
    ArrayList<Object> list = s.lists[d];
    if (list == null) {
      list = new ArrayList<>(INITIAL_CAPACITY);
      s.lists[d] = list;
    }
    s.depth = d + 1;
    return list;
  }

  public static void release(ArrayList<Object> list) {
    EntityProcess process = EntityProcess.current();
    if (process == null) {
      return;
    }
    ScratchLists s = process.scratchLists;
    int d = s.depth - 1;
    if (d < 0 || s.lists[d] != list) {
      return;
    }
    if (list.size() > KEEP_MAX) {
      s.lists[d] = null;
    } else {
      list.clear();
    }
    s.depth = d;
  }
}
