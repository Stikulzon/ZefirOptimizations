package ua.zefir.zefiroptimizations.process.affinity;

import java.util.BitSet;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

final class CoreTopology {
  static BitSet siblingsOf(int cpu) {
    BitSet siblings = new BitSet();
    siblings.set(cpu);
    CentralProcessor.LogicalProcessor own = null;
    var processors = new SystemInfo().getHardware().getProcessor().getLogicalProcessors();
    for (CentralProcessor.LogicalProcessor processor : processors) {
      if (processor.getProcessorNumber() == cpu) {
        own = processor;
        break;
      }
    }
    if (own == null) {
      return siblings;
    }
    for (CentralProcessor.LogicalProcessor processor : processors) {
      if (processor.getPhysicalPackageNumber() == own.getPhysicalPackageNumber()
          && processor.getPhysicalProcessorNumber() == own.getPhysicalProcessorNumber()
          && processor.getProcessorGroup() == own.getProcessorGroup()) {
        siblings.set(processor.getProcessorNumber());
      }
    }
    return siblings;
  }
}
