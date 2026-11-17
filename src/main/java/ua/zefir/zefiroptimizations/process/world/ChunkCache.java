package ua.zefir.zefiroptimizations.process.world;

import org.jetbrains.annotations.Nullable;
import ua.zefir.zefiroptimizations.process.EntityProcess;

public final class ChunkCache {
  public static final int WRAPPER_PRIORITY = 1500;

  private Object manager0, manager1;
  private long pos0, pos1;
  private Object status0, status1;
  private Object chunk0, chunk1;

  public static @Nullable ChunkCache current() {
    EntityProcess process = EntityProcess.current();
    return process != null ? process.chunkCache : null;
  }

  public @Nullable Object get(Object manager, long pos, Object status) {
    if (chunk0 != null && pos0 == pos && status0 == status && manager0 == manager) {
      return chunk0;
    }
    if (chunk1 != null && pos1 == pos && status1 == status && manager1 == manager) {
      return chunk1;
    }
    return null;
  }

  public void put(Object manager, long pos, Object status, Object chunk) {
    manager1 = manager0;
    pos1 = pos0;
    status1 = status0;
    chunk1 = chunk0;
    manager0 = manager;
    pos0 = pos;
    status0 = status;
    chunk0 = chunk;
  }

  public void clear() {
    chunk0 = null;
    chunk1 = null;
    manager0 = null;
    manager1 = null;
  }
}
