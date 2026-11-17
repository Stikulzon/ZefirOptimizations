package ua.zefir.zephyrium.process.affinity;

import com.sun.jna.Platform;
import java.util.BitSet;
import java.util.Optional;

interface NativeAffinity {
  int currentCpu();

  BitSet processCpus();

  boolean pinCurrentThread(BitSet cpus);

  static Optional<NativeAffinity> load() {
    if (Platform.isWindows()) {
      return WindowsAffinity.load();
    }
    if (Platform.isLinux()) {
      return LinuxAffinity.load();
    }
    return Optional.empty();
  }
}
