package ua.zefir.zefiroptimizations.process.sharedstate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import ua.zefir.zefiroptimizations.process.ownership.analysis.Bytecode;

final class Routing {
  private static final String ROUTED_PREFIX = "zo$sr$";
  private static final String SYNC_PREFIX = "zo$sync$";
  private static final String SITE_PREFIX = "zo$ss$";
  private static final String SITE_IMPL_PREFIX = "zo$ssi$";
  private static final String TAIL_PREFIX = "zo$tail$";
  private static final String CONSTRUCTOR = "<init>";
  private static final String ARRAY_PREFIX = "[";
  private static final int HELPER_ACCESS =
      Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
  private static final int TAIL_ACCESS = Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;
  private static final int COPY_KEPT_ACCESS =
      Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_VARARGS | Opcodes.ACC_STRICT;
  private static final Type OBJECT = Type.getType(Object.class);
  private static final Type OBJECT_ARRAY = Type.getType(Object[].class);
  private static final String ROUTE_DESC = "()Z";
  private static final int LAZY_CHECK_LENGTH = 3;
  private static final String CALL_DESC = "(L" + Bytecode.INVOCATION + ";)Ljava/lang/Object;";

  final List<MethodNode> added = new ArrayList<>();
  private final ClassNode node;
  private final Map<String, MethodNode> siteHelpers = new HashMap<>();
  private int counter;

  Routing(ClassNode node) {
    this.node = node;
  }

  private boolean isInterface() {
    return (node.access & Opcodes.ACC_INTERFACE) != 0;
  }

  private String newName(String prefix) {
    return prefix + counter++;
  }

  void routeMethod(MethodNode method, List<AbstractInsnNode> triggers) {
    FieldInsnNode lazy = lazyField(method, triggers);
    MethodNode target =
        (method.access & Opcodes.ACC_SYNCHRONIZED) != 0 ? splitSynchronized(method) : method;
    boolean isStatic = (target.access & Opcodes.ACC_STATIC) != 0;
    Type[] args = Type.getArgumentTypes(target.desc);
    Type ret = Type.getReturnType(target.desc);
    Type[] captured = isStatic ? args : prepend(Type.getObjectType(node.name), args);
    Forwarder impl = addForwarder(captured, isStatic, target.name, target.desc, ret);

    InsnList g = new InsnList();
    LabelNode direct = new LabelNode();
    if (lazy != null) {
      g.add(skipWhenSet(lazy, direct));
    }
    g.add(routeToMain(loads(captured), impl, false, ret, direct));
    g.add(direct);
    if (!startsWithFrame(target)) {
      g.add(Bytecode.frame(Bytecode.locals(node.name, !isStatic, args)));
    }
    target.instructions.insert(g);
  }

  private static InsnList skipWhenSet(FieldInsnNode lazy, LabelNode direct) {
    InsnList g = new InsnList();
    g.add(new VarInsnNode(Opcodes.ALOAD, 0));
    g.add(new FieldInsnNode(Opcodes.GETFIELD, lazy.owner, lazy.name, lazy.desc));
    g.add(new JumpInsnNode(Opcodes.IFNONNULL, direct));
    return g;
  }

  void routeTail(MethodNode method, TailSplit split) {
    boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
    Type[] slots = split.slots;
    Type[] params = isStatic ? slots : Arrays.copyOfRange(slots, 1, slots.length);
    Type ret = Type.getReturnType(method.desc);
    MethodNode tail = copyTail(method, split, Type.getMethodDescriptor(ret, params));
    added.add(tail);
    Forwarder impl = addForwarder(slots, isStatic, tail.name, tail.desc, ret);

    InsnList g = new InsnList();
    LabelNode direct = new LabelNode();
    g.add(routeToMain(slotLoads(split), impl, false, ret, direct));
    g.add(direct);
    g.add(Bytecode.frame(split.frameLocals.clone()));
    method.instructions.insertBefore(split.at, g);
  }

  private Forwarder addForwarder(
      Type[] params, boolean isStatic, String name, String desc, Type ret) {
    String implName = newName(ROUTED_PREFIX);
    String implDesc = Type.getMethodDescriptor(OBJECT, Bytecode.append(params, OBJECT));
    MethodNode impl = new MethodNode(HELPER_ACCESS, implName, implDesc, null, null);
    Bytecode.loadAll(impl.instructions, params, 0);
    impl.instructions.add(
        new MethodInsnNode(
            isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL, node.name, name, desc, false));
    Bytecode.box(impl.instructions, ret);
    impl.instructions.add(new InsnNode(Opcodes.ARETURN));
    added.add(impl);
    return new Forwarder(implName, implDesc, params);
  }

  private InsnList routeToMain(
      InsnList loadArgs, Forwarder impl, boolean ownerIsInterface, Type ret, LabelNode direct) {
    InsnList g = new InsnList();
    g.add(guard(SharedStateGuard.ROUTE, ROUTE_DESC));
    g.add(new JumpInsnNode(Opcodes.IFEQ, direct));
    g.add(loadArgs);
    g.add(Bytecode.invocation(impl.params, node.name, impl.name, impl.desc, ownerIsInterface));
    g.add(guard(SharedStateGuard.CALL, CALL_DESC));
    Bytecode.unbox(g, ret);
    g.add(Bytecode.returnInsn(ret));
    return g;
  }

  private static InsnList loads(Type[] types) {
    InsnList insns = new InsnList();
    Bytecode.loadAll(insns, types, 0);
    return insns;
  }

  private MethodNode copyTail(MethodNode method, TailSplit split, String desc) {
    boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
    MethodNode tail =
        new MethodNode(
            TAIL_ACCESS | (method.access & Opcodes.ACC_STATIC),
            newName(TAIL_PREFIX),
            desc,
            null,
            method.exceptions == null ? null : method.exceptions.toArray(new String[0]));
    Map<LabelNode, LabelNode> labels = freshLabels(method);
    AbstractInsnNode at = copyInstructions(method, tail, labels, split.at);
    copyTryCatchBlocks(method, tail, labels);
    boolean framed = precededByFrame(at);
    LabelNode resume = new LabelNode();
    tail.instructions.insertBefore(at, resume);
    if (!framed) {
      tail.instructions.insertBefore(at, Bytecode.frame(split.frameLocals.clone()));
    }
    InsnList entry = jumpToResume(split, resume);
    if (!startsWithFrame(method)) {
      entry.add(
          Bytecode.frame(
              Bytecode.locals(node.name, !isStatic, Type.getArgumentTypes(method.desc))));
    }
    tail.instructions.insert(entry);
    return tail;
  }

  private static Map<LabelNode, LabelNode> freshLabels(MethodNode method) {
    Map<LabelNode, LabelNode> labels = new HashMap<>();
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof LabelNode label) {
        labels.put(label, new LabelNode());
      }
    }
    return labels;
  }

  private static AbstractInsnNode copyInstructions(
      MethodNode from, MethodNode to, Map<LabelNode, LabelNode> labels, AbstractInsnNode mark) {
    AbstractInsnNode markCopy = null;
    for (AbstractInsnNode insn : from.instructions) {
      AbstractInsnNode copy = insn.clone(labels);
      to.instructions.add(copy);
      if (insn == mark) {
        markCopy = copy;
      }
    }
    return markCopy;
  }

  private static void copyTryCatchBlocks(
      MethodNode from, MethodNode to, Map<LabelNode, LabelNode> labels) {
    for (TryCatchBlockNode block : from.tryCatchBlocks) {
      to.tryCatchBlocks.add(
          new TryCatchBlockNode(
              labels.get(block.start),
              labels.get(block.end),
              labels.get(block.handler),
              block.type));
    }
  }

  private static InsnList jumpToResume(TailSplit split, LabelNode resume) {
    InsnList entry = new InsnList();
    for (int slot : split.nullSlots) {
      entry.add(new InsnNode(Opcodes.ACONST_NULL));
      entry.add(new VarInsnNode(Opcodes.ASTORE, slot));
    }
    entry.add(new JumpInsnNode(Opcodes.GOTO, resume));
    return entry;
  }

  private static InsnList slotLoads(TailSplit split) {
    InsnList insns = new InsnList();
    loadSlots(insns, split);
    return insns;
  }

  private static void loadSlots(InsnList insns, TailSplit split) {
    int slot = 0;
    for (int i = 0; i < split.slots.length; i++) {
      Object local = split.frameLocals[i];
      Type type = split.slots[i];
      if (local == Opcodes.TOP) {
        insns.add(new InsnNode(Opcodes.ICONST_0));
      } else if (local == Opcodes.NULL) {
        insns.add(new InsnNode(Opcodes.ACONST_NULL));
      } else {
        insns.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), slot));
      }
      slot += type.getSize();
    }
  }

  private static boolean precededByFrame(AbstractInsnNode insn) {
    for (AbstractInsnNode prev = insn.getPrevious(); prev != null; prev = prev.getPrevious()) {
      if (prev instanceof LabelNode || prev instanceof LineNumberNode) {
        continue;
      }
      return prev instanceof FrameNode;
    }
    return false;
  }

  private FieldInsnNode lazyField(MethodNode method, List<AbstractInsnNode> triggers) {
    if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED)) != 0
        || !method.tryCatchBlocks.isEmpty()) {
      return null;
    }
    List<AbstractInsnNode> code = firstInstructions(method, LAZY_CHECK_LENGTH);
    if (code.size() < LAZY_CHECK_LENGTH
        || !(code.get(0) instanceof VarInsnNode load)
        || load.getOpcode() != Opcodes.ALOAD
        || load.var != 0
        || !(code.get(1) instanceof FieldInsnNode field)
        || field.getOpcode() != Opcodes.GETFIELD
        || !field.owner.equals(node.name)
        || !(code.get(2) instanceof JumpInsnNode check)
        || check.getOpcode() != Opcodes.IFNONNULL) {
      return null;
    }
    InsnList insns = method.instructions;
    int from = insns.indexOf(check);
    int to = insns.indexOf(check.label);
    if (to <= from
        || !allWithin(triggers, insns, from, to)
        || anyJumpsInFromOutside(insns, from, to)) {
      return null;
    }
    return field;
  }

  private static List<AbstractInsnNode> firstInstructions(MethodNode method, int count) {
    List<AbstractInsnNode> code = new ArrayList<>();
    for (AbstractInsnNode insn : method.instructions) {
      if (insn.getOpcode() >= 0) {
        code.add(insn);
      }
      if (code.size() == count) {
        break;
      }
    }
    return code;
  }

  private static boolean allWithin(
      List<AbstractInsnNode> triggers, InsnList insns, int from, int to) {
    for (AbstractInsnNode trigger : triggers) {
      int at = insns.indexOf(trigger);
      if (at <= from || at >= to) {
        return false;
      }
    }
    return true;
  }

  private static boolean anyJumpsInFromOutside(InsnList insns, int from, int to) {
    for (AbstractInsnNode insn : insns) {
      int at = insns.indexOf(insn);
      if ((at <= from || at >= to) && jumpsInto(insn, insns, from, to)) {
        return true;
      }
    }
    return false;
  }

  private static boolean jumpsInto(AbstractInsnNode insn, InsnList insns, int from, int to) {
    List<LabelNode> targets = new ArrayList<>();
    switch (insn) {
      case JumpInsnNode jump -> targets.add(jump.label);
      case TableSwitchInsnNode table -> {
        targets.add(table.dflt);
        targets.addAll(table.labels);
      }
      case LookupSwitchInsnNode lookup -> {
        targets.add(lookup.dflt);
        targets.addAll(lookup.labels);
      }
      default -> {
        return false;
      }
    }
    for (LabelNode label : targets) {
      int at = insns.indexOf(label);
      if (at > from && at < to) {
        return true;
      }
    }
    return false;
  }

  private MethodNode splitSynchronized(MethodNode method) {
    boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
    MethodNode wrapper =
        new MethodNode(
            method.access & ~Opcodes.ACC_SYNCHRONIZED,
            method.name,
            method.desc,
            method.signature,
            method.exceptions == null ? null : method.exceptions.toArray(new String[0]));
    moveAnnotations(method, wrapper);
    method.name = newName(SYNC_PREFIX);
    method.access =
        (method.access & COPY_KEPT_ACCESS) | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;
    wrapper.instructions.add(forwardTo(method, isStatic));
    added.add(wrapper);
    return wrapper;
  }

  private static void moveAnnotations(MethodNode from, MethodNode to) {
    to.visibleAnnotations = from.visibleAnnotations;
    to.invisibleAnnotations = from.invisibleAnnotations;
    to.visibleParameterAnnotations = from.visibleParameterAnnotations;
    to.invisibleParameterAnnotations = from.invisibleParameterAnnotations;
    to.visibleAnnotableParameterCount = from.visibleAnnotableParameterCount;
    to.invisibleAnnotableParameterCount = from.invisibleAnnotableParameterCount;
    from.visibleAnnotations = null;
    from.invisibleAnnotations = null;
    from.visibleParameterAnnotations = null;
    from.invisibleParameterAnnotations = null;
  }

  private InsnList forwardTo(MethodNode method, boolean isStatic) {
    InsnList insns = new InsnList();
    if (!isStatic) {
      insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
    }
    Bytecode.loadAll(insns, Type.getArgumentTypes(method.desc), isStatic ? 0 : 1);
    insns.add(
        new MethodInsnNode(
            isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL,
            node.name,
            method.name,
            method.desc,
            false));
    insns.add(Bytecode.returnInsn(Type.getReturnType(method.desc)));
    return insns;
  }

  void routeSite(MethodNode method, AbstractInsnNode insn, String site) {
    Type[] params = siteParams(method, insn);
    if (params == null) {
      method.instructions.insertBefore(insn, touch(node.name, site));
      return;
    }
    Type ret = insn instanceof MethodInsnNode call ? Type.getReturnType(call.desc) : Type.VOID_TYPE;
    MethodNode helper =
        siteHelpers.computeIfAbsent(siteKey(insn), key -> siteHelper(params, ret, insn));
    method.instructions.set(
        insn,
        new MethodInsnNode(
            Opcodes.INVOKESTATIC, node.name, helper.name, helper.desc, isInterface()));
  }

  private Type[] siteParams(MethodNode method, AbstractInsnNode insn) {
    int opcode = insn.getOpcode();
    if (insn instanceof FieldInsnNode field) {
      Type value = Type.getType(field.desc);
      if (opcode == Opcodes.PUTSTATIC) {
        return new Type[] {value};
      }
      if (method.name.equals(CONSTRUCTOR) && field.owner.equals(node.name)) {
        return null;
      }
      return new Type[] {Type.getObjectType(field.owner), value};
    }
    if (insn instanceof MethodInsnNode call) {
      if (opcode == Opcodes.INVOKESPECIAL) {
        return null;
      }
      Type[] args = Type.getArgumentTypes(call.desc);
      return opcode == Opcodes.INVOKESTATIC ? args : prepend(Type.getObjectType(call.owner), args);
    }
    Type element =
        switch (opcode) {
          case Opcodes.IASTORE -> Type.INT_TYPE;
          case Opcodes.LASTORE -> Type.LONG_TYPE;
          case Opcodes.FASTORE -> Type.FLOAT_TYPE;
          case Opcodes.DASTORE -> Type.DOUBLE_TYPE;
          case Opcodes.CASTORE -> Type.CHAR_TYPE;
          case Opcodes.SASTORE -> Type.SHORT_TYPE;
          case Opcodes.AASTORE -> OBJECT;
          default -> null;
        };
    if (element == null) {
      return null;
    }
    Type array =
        element == OBJECT ? OBJECT_ARRAY : Type.getType(ARRAY_PREFIX + element.getDescriptor());
    return new Type[] {array, Type.INT_TYPE, element};
  }

  private static String siteKey(AbstractInsnNode insn) {
    if (insn instanceof FieldInsnNode field) {
      return insn.getOpcode() + field.owner + '.' + field.name + field.desc;
    }
    if (insn instanceof MethodInsnNode call) {
      return insn.getOpcode() + call.owner + '.' + call.name + call.desc;
    }
    return Integer.toString(insn.getOpcode());
  }

  private MethodNode siteHelper(Type[] params, Type ret, AbstractInsnNode op) {
    String implName = newName(SITE_IMPL_PREFIX);
    String implDesc = Type.getMethodDescriptor(OBJECT, Bytecode.append(params, OBJECT));
    MethodNode impl = new MethodNode(HELPER_ACCESS, implName, implDesc, null, null);
    Bytecode.loadAll(impl.instructions, params, 0);
    impl.instructions.add(op.clone(Map.of()));
    Bytecode.box(impl.instructions, ret);
    impl.instructions.add(new InsnNode(Opcodes.ARETURN));
    added.add(impl);

    MethodNode helper =
        new MethodNode(
            HELPER_ACCESS, newName(SITE_PREFIX), Type.getMethodDescriptor(ret, params), null, null);
    InsnList h = helper.instructions;
    LabelNode direct = new LabelNode();
    h.add(
        routeToMain(
            loads(params), new Forwarder(implName, implDesc, params), isInterface(), ret, direct));
    h.add(direct);
    h.add(Bytecode.frame(Bytecode.locals(node.name, false, params)));
    Bytecode.loadAll(h, params, 0);
    h.add(op.clone(Map.of()));
    h.add(Bytecode.returnInsn(ret));
    added.add(helper);
    return helper;
  }

  static InsnList touch(String owner, String site) {
    InsnList call = new InsnList();
    call.add(new LdcInsnNode(owner));
    call.add(new LdcInsnNode(site));
    call.add(guard(SharedStateGuard.TOUCH, SharedStateGuard.TOUCH_DESC));
    return call;
  }

  private static MethodInsnNode guard(String name, String desc) {
    return new MethodInsnNode(
        Opcodes.INVOKESTATIC, SharedStateGuard.INTERNAL_NAME, name, desc, false);
  }

  private static boolean startsWithFrame(MethodNode method) {
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof LabelNode || insn instanceof LineNumberNode) {
        continue;
      }
      return insn instanceof FrameNode;
    }
    return false;
  }

  private record Forwarder(String name, String desc, Type[] params) {}

  private static Type[] prepend(Type first, Type[] rest) {
    Type[] result = new Type[rest.length + 1];
    result[0] = first;
    System.arraycopy(rest, 0, result, 1, rest.length);
    return result;
  }
}
