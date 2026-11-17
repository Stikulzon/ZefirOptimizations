package ua.zefir.zephyrium.process.ownership.analysis;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

public final class ThisInterpreter extends Interpreter<ThisInterpreter.ThisValue> {
  public static final class ThisValue implements Value {
    final int size;
    public final boolean maybeThis;
    public final boolean definitelyThis;

    ThisValue(int size, boolean maybeThis, boolean definitelyThis) {
      this.size = size;
      this.maybeThis = maybeThis;
      this.definitelyThis = definitelyThis;
    }

    @Override
    public int getSize() {
      return size;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof ThisValue v
          && v.size == size
          && v.maybeThis == maybeThis
          && v.definitelyThis == definitelyThis;
    }

    @Override
    public int hashCode() {
      return size * SIZE_HASH_FACTOR
          + (maybeThis ? MAYBE_THIS_HASH : 0)
          + (definitelyThis ? DEFINITELY_THIS_HASH : 0);
    }
  }

  private static final int NARROW = 1;
  private static final int WIDE = 2;
  private static final int SIZE_HASH_FACTOR = 4;
  private static final int MAYBE_THIS_HASH = 2;
  private static final int DEFINITELY_THIS_HASH = 1;

  private static final ThisValue ONE = new ThisValue(NARROW, false, false);
  private static final ThisValue TWO = new ThisValue(WIDE, false, false);
  private static final ThisValue THIS = new ThisValue(NARROW, true, true);

  private final BasicInterpreter basic = new BasicInterpreter();
  private final long thisSlots;

  public ThisInterpreter() {
    this(0L);
  }

  public ThisInterpreter(long thisSlots) {
    super(Opcodes.ASM9);
    this.thisSlots = thisSlots;
  }

  private static ThisValue sized(BasicValue value) {
    if (value == null) {
      return null;
    }
    return value.getSize() == WIDE ? TWO : ONE;
  }

  private static BasicValue dummy(ThisValue value) {
    return value != null && value.size == WIDE ? BasicValue.LONG_VALUE : BasicValue.REFERENCE_VALUE;
  }

  @Override
  public ThisValue newValue(Type type) {
    if (type == null) {
      return ONE;
    }
    if (type.getSort() == Type.VOID) {
      return null;
    }
    return type.getSize() == WIDE ? TWO : ONE;
  }

  @Override
  public ThisValue newParameterValue(boolean isInstanceMethod, int local, Type type) {
    if (isInstanceMethod && local == 0 || local < Long.SIZE && (thisSlots & (1L << local)) != 0) {
      return THIS;
    }
    return newValue(type);
  }

  @Override
  public ThisValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
    return sized(basic.newOperation(insn));
  }

  @Override
  public ThisValue copyOperation(AbstractInsnNode insn, ThisValue value) {
    return value;
  }

  @Override
  public ThisValue unaryOperation(AbstractInsnNode insn, ThisValue value) throws AnalyzerException {
    if (insn.getOpcode() == Opcodes.CHECKCAST) {
      return value;
    }
    return sized(basic.unaryOperation(insn, dummy(value)));
  }

  @Override
  public ThisValue binaryOperation(AbstractInsnNode insn, ThisValue value1, ThisValue value2)
      throws AnalyzerException {
    return sized(basic.binaryOperation(insn, dummy(value1), dummy(value2)));
  }

  @Override
  public ThisValue ternaryOperation(
      AbstractInsnNode insn, ThisValue value1, ThisValue value2, ThisValue value3) {
    return null;
  }

  @Override
  public ThisValue naryOperation(AbstractInsnNode insn, List<? extends ThisValue> values)
      throws AnalyzerException {
    List<BasicValue> dummies = new ArrayList<>(values.size());
    for (ThisValue value : values) {
      dummies.add(dummy(value));
    }
    return sized(basic.naryOperation(insn, dummies));
  }

  @Override
  public void returnOperation(AbstractInsnNode insn, ThisValue value, ThisValue expected) {}

  @Override
  public ThisValue merge(ThisValue a, ThisValue b) {
    if (a.equals(b)) {
      return a;
    }
    int size = a.size == b.size ? a.size : 1;
    ThisValue merged =
        new ThisValue(size, a.maybeThis || b.maybeThis, a.definitelyThis && b.definitelyThis);
    return merged.equals(a) ? a : merged;
  }
}
