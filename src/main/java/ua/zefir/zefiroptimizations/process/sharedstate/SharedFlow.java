package ua.zefir.zefiroptimizations.process.sharedstate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

final class SharedFlow extends Interpreter<SharedFlow.Flow> {
  static final class Flow implements Value {
    final int size;
    final boolean shared;
    final String desc;
    final boolean constructing;

    Flow(int size, boolean shared, String desc) {
      this(size, shared, desc, false);
    }

    Flow(int size, boolean shared, String desc, boolean constructing) {
      this.size = size;
      this.shared = shared;
      this.desc = desc;
      this.constructing = constructing;
    }

    @Override
    public int getSize() {
      return size;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Flow f
          && f.size == size
          && f.shared == shared
          && f.constructing == constructing
          && Objects.equals(f.desc, desc);
    }

    @Override
    public int hashCode() {
      return Objects.hash(size, shared, desc, constructing);
    }
  }

  private static final int NARROW = 1;
  private static final int WIDE = 2;
  private static final char ARRAY = '[';
  private static final Flow ONE = new Flow(NARROW, false, null);
  private static final Flow TWO = new Flow(WIDE, false, null);
  private static final Flow CONSTRUCTING = new Flow(NARROW, false, null, true);

  private final BasicInterpreter basic = new BasicInterpreter();
  private final SharedAccess access;
  private final int[] parameterOfSlot;
  private final long sharedParameters;
  private final boolean constructing;

  SharedFlow(
      SharedAccess access,
      String desc,
      boolean isStatic,
      long sharedParameters,
      boolean constructing) {
    super(Opcodes.ASM9);
    this.access = access;
    this.sharedParameters = sharedParameters;
    this.constructing = constructing && !isStatic;
    Type[] args = Type.getArgumentTypes(desc);
    int slots = isStatic ? 0 : 1;
    for (Type arg : args) {
      slots += arg.getSize();
    }
    parameterOfSlot = new int[slots];
    int slot = 0;
    int parameter = 0;
    if (!isStatic) {
      parameterOfSlot[slot++] = parameter++;
    }
    for (Type arg : args) {
      parameterOfSlot[slot] = parameter++;
      slot += arg.getSize();
    }
  }

  static Flow shared(String desc) {
    return new Flow(NARROW, true, desc);
  }

  private static Flow sized(BasicValue value) {
    if (value == null) {
      return null;
    }
    return value.getSize() == WIDE ? TWO : ONE;
  }

  private static BasicValue dummy(Flow value) {
    return value != null && value.size == WIDE ? BasicValue.LONG_VALUE : BasicValue.REFERENCE_VALUE;
  }

  @Override
  public Flow newValue(Type type) {
    if (type == null) {
      return ONE;
    }
    if (type.getSort() == Type.VOID) {
      return null;
    }
    return type.getSize() == WIDE ? TWO : ONE;
  }

  @Override
  public Flow newParameterValue(boolean isInstanceMethod, int local, Type type) {
    if (constructing && local == 0) {
      return CONSTRUCTING;
    }
    if (local < parameterOfSlot.length
        && SharedAccess.isShared(sharedParameters, parameterOfSlot[local])
        && type.getSize() == NARROW
        && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)) {
      return shared(type.getDescriptor());
    }
    return newValue(type);
  }

  @Override
  public Flow newOperation(AbstractInsnNode insn) throws AnalyzerException {
    if (insn.getOpcode() == Opcodes.GETSTATIC
        && insn instanceof FieldInsnNode field
        && access.isSharedStatic(field)) {
      return shared(field.desc);
    }
    return sized(basic.newOperation(insn));
  }

  @Override
  public Flow copyOperation(AbstractInsnNode insn, Flow value) {
    return value;
  }

  @Override
  public Flow unaryOperation(AbstractInsnNode insn, Flow value) throws AnalyzerException {
    if (insn.getOpcode() == Opcodes.CHECKCAST) {
      if (!value.shared) {
        return value;
      }
      String cast = ((TypeInsnNode) insn).desc;
      String desc = cast.charAt(0) == ARRAY ? cast : Type.getObjectType(cast).getDescriptor();
      if (!access.mayChange(desc)) {
        return sized(basic.unaryOperation(insn, dummy(value)));
      }
      return shared(desc);
    }
    if (insn.getOpcode() == Opcodes.GETFIELD
        && insn instanceof FieldInsnNode field
        && access.isSharedField(field, value.shared)) {
      return shared(field.desc);
    }
    return sized(basic.unaryOperation(insn, dummy(value)));
  }

  @Override
  public Flow binaryOperation(AbstractInsnNode insn, Flow value1, Flow value2)
      throws AnalyzerException {
    if (insn.getOpcode() == Opcodes.AALOAD && value1.shared) {
      String element =
          value1.desc != null && value1.desc.charAt(0) == ARRAY ? value1.desc.substring(1) : null;
      if (access.mayChange(element)) {
        return shared(element);
      }
    }
    return sized(basic.binaryOperation(insn, dummy(value1), dummy(value2)));
  }

  @Override
  public Flow ternaryOperation(AbstractInsnNode insn, Flow value1, Flow value2, Flow value3) {
    return null;
  }

  @Override
  public Flow naryOperation(AbstractInsnNode insn, List<? extends Flow> values)
      throws AnalyzerException {
    List<BasicValue> dummies = new ArrayList<>(values.size());
    boolean anyShared = false;
    for (Flow value : values) {
      dummies.add(dummy(value));
      anyShared |= value.shared;
    }
    Flow result = sized(basic.naryOperation(insn, dummies));
    if (!anyShared || result == null || result.size != NARROW) {
      return result;
    }
    if (insn instanceof InvokeDynamicInsnNode indy) {
      return shared(Type.getReturnType(indy.desc).getDescriptor());
    }
    if (insn instanceof MethodInsnNode call && ReadCalls.copies(call.name)) {
      return result;
    }
    String returned = insn instanceof MethodInsnNode call ? returnDescriptor(call.desc) : null;
    if (returned == null) {
      return result;
    }
    return access.mayChange(returned) ? shared(returned) : result;
  }

  private static String returnDescriptor(String desc) {
    Type returned = Type.getReturnType(desc);
    return returned.getSort() == Type.OBJECT || returned.getSort() == Type.ARRAY
        ? returned.getDescriptor()
        : null;
  }

  @Override
  public void returnOperation(AbstractInsnNode insn, Flow value, Flow expected) {}

  @Override
  public Flow merge(Flow a, Flow b) {
    if (a.equals(b)) {
      return a;
    }
    int size = a.size == b.size ? a.size : NARROW;
    boolean shared = a.shared || b.shared;
    String desc = Objects.equals(a.desc, b.desc) ? a.desc : null;
    Flow merged = new Flow(size, shared, shared ? desc : null, a.constructing && b.constructing);
    return merged.equals(a) ? a : merged;
  }
}
