package ua.zefir.zephyrium.process.affinity;

import com.sun.jna.Library;
import com.sun.jna.Native;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Optional;

final class LinuxAffinity implements NativeAffinity {
  private static final int CALLING_THREAD = 0;
  private static final int MASK_WORDS = 16;
  private static final int MASK_BYTES = MASK_WORDS * Long.BYTES;

  private final LibC libc;

  private LinuxAffinity(LibC libc) {
    this.libc = libc;
  }

  interface LibC extends Library {
    int sched_getcpu();

    int sched_getaffinity(int pid, long size, long[] mask);

    int sched_setaffinity(int pid, long size, long[] mask);
  }

  static Optional<NativeAffinity> load() {
    return Optional.of(new LinuxAffinity(Native.load("c", LibC.class)));
  }

  @Override
  public int currentCpu() {
    return libc.sched_getcpu();
  }

  @Override
  public BitSet processCpus() {
    long[] mask = new long[MASK_WORDS];
    if (libc.sched_getaffinity(CALLING_THREAD, MASK_BYTES, mask) != 0) {
      return new BitSet();
    }
    return BitSet.valueOf(mask);
  }

  @Override
  public boolean pinCurrentThread(BitSet cpus) {
    long[] words = cpus.toLongArray();
    if (words.length > MASK_WORDS) {
      return false;
    }
    long[] mask = Arrays.copyOf(words, MASK_WORDS);
    return libc.sched_setaffinity(CALLING_THREAD, MASK_BYTES, mask) == 0;
  }
}
