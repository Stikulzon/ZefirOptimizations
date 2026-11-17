package ua.zefir.zefiroptimizations.process.affinity;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import java.util.BitSet;
import java.util.Optional;

final class WindowsAffinity implements NativeAffinity {
  private static final short ALL_PROCESSOR_GROUPS = (short) 0xFFFF;
  private static final int MAX_CPUS = Long.SIZE;

  private final Kernel32 kernel;

  private WindowsAffinity(Kernel32 kernel) {
    this.kernel = kernel;
  }

  interface Kernel32 extends Library {
    int GetCurrentProcessorNumber();

    int GetActiveProcessorCount(short groupNumber);

    Pointer GetCurrentThread();

    Pointer GetCurrentProcess();

    long SetThreadAffinityMask(Pointer thread, long mask);

    boolean GetProcessAffinityMask(
        Pointer process, LongByReference processMask, LongByReference systemMask);
  }

  static Optional<NativeAffinity> load() {
    Kernel32 kernel = Native.load("kernel32", Kernel32.class);
    if (kernel.GetActiveProcessorCount(ALL_PROCESSOR_GROUPS) > MAX_CPUS) {
      CpuAffinity.LOGGER.warn(
          "CPU reservation supports at most {} logical CPUs on Windows", MAX_CPUS);
      return Optional.empty();
    }
    return Optional.of(new WindowsAffinity(kernel));
  }

  @Override
  public int currentCpu() {
    return kernel.GetCurrentProcessorNumber();
  }

  @Override
  public BitSet processCpus() {
    LongByReference process = new LongByReference();
    LongByReference system = new LongByReference();
    if (!kernel.GetProcessAffinityMask(kernel.GetCurrentProcess(), process, system)) {
      return new BitSet();
    }
    return BitSet.valueOf(new long[] {process.getValue()});
  }

  @Override
  public boolean pinCurrentThread(BitSet cpus) {
    long[] words = cpus.toLongArray();
    return words.length == 1
        && kernel.SetThreadAffinityMask(kernel.GetCurrentThread(), words[0]) != 0;
  }
}
