package ua.zefir.zefiroptimizations.process.sharedstate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

final class TailSplit {
  static final int MAX_INSTRUCTIONS = 2000;
  private static final String CONSTRUCTOR = "<init>";
  private static final String OBJECT = "java/lang/Object";

  final AbstractInsnNode at;
  final Object[] frameLocals;
  final Type[] slots;
  final int[] nullSlots;

  private TailSplit(AbstractInsnNode at, Object[] frameLocals, Type[] slots, int[] nullSlots) {
    this.at = at;
    this.frameLocals = frameLocals;
    this.slots = slots;
    this.nullSlots = nullSlots;
  }

  static TailSplit find(
      String owner, MethodNode method, List<AbstractInsnNode> triggers, boolean[] sharedOnStack) {
    if ((method.access & Opcodes.ACC_SYNCHRONIZED) != 0
        || method.name.equals(CONSTRUCTOR)
        || method.instructions.size() > MAX_INSTRUCTIONS) {
      return null;
    }
    try {
      return split(owner, method, triggers, sharedOnStack);
    } catch (RuntimeException e) {
      SharedState.LOGGER.debug(
          "Could not split {}.{}{}; it is routed whole", owner, method.name, method.desc, e);
      return null;
    }
  }

  private static TailSplit split(
      String owner, MethodNode method, List<AbstractInsnNode> triggers, boolean[] sharedOnStack) {
    InsnList insns = method.instructions;
    int size = insns.size();
    boolean[] isTrigger = triggerMask(insns, triggers);
    int[][] successors = successors(method);
    boolean[] guarded = guarded(method);
    Object[][] locals = emptyStackLocals(owner, method);
    int best = -1;
    int bestReach = 0;
    for (int candidate = 0; candidate < size; candidate++) {
      if (insns.get(candidate).getOpcode() < 0
          || locals[candidate] == null
          || guarded[candidate]
          || sharedOnStack[candidate]) {
        continue;
      }
      int reach = prefixSize(insns, successors, candidate, isTrigger, sharedOnStack);
      if (reach > bestReach) {
        best = candidate;
        bestReach = reach;
      }
    }
    if (best < 0) {
      return null;
    }
    return of(insns.get(best), locals[best]);
  }

  private static boolean[] triggerMask(InsnList insns, List<AbstractInsnNode> triggers) {
    boolean[] isTrigger = new boolean[insns.size()];
    for (AbstractInsnNode trigger : triggers) {
      isTrigger[insns.indexOf(trigger)] = true;
    }
    return isTrigger;
  }

  private static TailSplit of(AbstractInsnNode at, Object[] frameLocals) {
    List<Type> slots = new ArrayList<>();
    List<Integer> nulls = new ArrayList<>();
    for (Object local : frameLocals) {
      Type type = slotType(local);
      if (type == null) {
        return null;
      }
      if (local == Opcodes.NULL) {
        nulls.add(slotIndex(slots));
      }
      slots.add(type);
    }
    return new TailSplit(
        at,
        frameLocals,
        slots.toArray(Type[]::new),
        nulls.stream().mapToInt(Integer::intValue).toArray());
  }

  private static int slotIndex(List<Type> slots) {
    int index = 0;
    for (Type slot : slots) {
      index += slot.getSize();
    }
    return index;
  }

  private static Type slotType(Object local) {
    if (local == Opcodes.TOP || local == Opcodes.INTEGER) {
      return Type.INT_TYPE;
    }
    if (local == Opcodes.FLOAT) {
      return Type.FLOAT_TYPE;
    }
    if (local == Opcodes.LONG) {
      return Type.LONG_TYPE;
    }
    if (local == Opcodes.DOUBLE) {
      return Type.DOUBLE_TYPE;
    }
    if (local == Opcodes.NULL) {
      return Type.getObjectType(OBJECT);
    }
    if (local instanceof String name) {
      return Type.getObjectType(name);
    }
    return null;
  }

  private static int prefixSize(
      InsnList insns, int[][] successors, int split, boolean[] isTrigger, boolean[] sharedOnStack) {
    boolean[] seen = new boolean[insns.size()];
    Deque<Integer> work = new ArrayDeque<>();
    work.add(0);
    seen[0] = true;
    int count = 0;
    boolean exits = false;
    while (!work.isEmpty()) {
      int index = work.poll();
      if (isTrigger[index] || sharedOnStack[index]) {
        return 0;
      }
      count++;
      int opcode = insns.get(index).getOpcode();
      exits |= opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN;
      for (int next : successors[index]) {
        if (next != split && !seen[next]) {
          seen[next] = true;
          work.add(next);
        }
      }
    }
    return exits ? count : 0;
  }

  private static int[][] successors(MethodNode method) {
    InsnList insns = method.instructions;
    int size = insns.size();
    int[][] result = new int[size][];
    for (int i = 0; i < size; i++) {
      List<Integer> next = new ArrayList<>();
      addFlowTargets(insns, i, next);
      addHandlers(method, i, next);
      result[i] = next.stream().mapToInt(Integer::intValue).toArray();
    }
    return result;
  }

  private static void addFlowTargets(InsnList insns, int i, List<Integer> next) {
    int size = insns.size();
    AbstractInsnNode insn = insns.get(i);
    switch (insn) {
      case JumpInsnNode jump -> {
        next.add(insns.indexOf(jump.label));
        if (jump.getOpcode() != Opcodes.GOTO && i + 1 < size) {
          next.add(i + 1);
        }
      }
      case TableSwitchInsnNode table -> addSwitchTargets(insns, table.dflt, table.labels, next);
      case LookupSwitchInsnNode lookup -> addSwitchTargets(insns, lookup.dflt, lookup.labels, next);
      default -> {
        if (!ends(insn.getOpcode()) && i + 1 < size) {
          next.add(i + 1);
        }
      }
    }
  }

  private static void addSwitchTargets(
      InsnList insns, LabelNode dflt, List<LabelNode> labels, List<Integer> next) {
    next.add(insns.indexOf(dflt));
    for (LabelNode label : labels) {
      next.add(insns.indexOf(label));
    }
  }

  private static void addHandlers(MethodNode method, int i, List<Integer> next) {
    InsnList insns = method.instructions;
    for (TryCatchBlockNode block : method.tryCatchBlocks) {
      if (i >= insns.indexOf(block.start) && i < insns.indexOf(block.end)) {
        next.add(insns.indexOf(block.handler));
      }
    }
  }

  private static boolean ends(int opcode) {
    return (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW;
  }

  private static boolean[] guarded(MethodNode method) {
    InsnList insns = method.instructions;
    boolean[] result = new boolean[insns.size()];
    for (TryCatchBlockNode block : method.tryCatchBlocks) {
      int end = insns.indexOf(block.end);
      for (int i = insns.indexOf(block.start); i < end; i++) {
        result[i] = true;
      }
    }
    return result;
  }

  private static Object[][] emptyStackLocals(String owner, MethodNode method) {
    InsnList insns = method.instructions;
    Object[][] result = new Object[insns.size()][];
    AnalyzerAdapter adapter =
        new AnalyzerAdapter(owner, method.access, method.name, method.desc, null);
    for (int i = 0; i < insns.size(); i++) {
      AbstractInsnNode insn = insns.get(i);
      if (insn.getOpcode() >= 0
          && adapter.locals != null
          && adapter.stack != null
          && adapter.stack.isEmpty()) {
        result[i] = compact(adapter.locals);
      }
      insn.accept(adapter);
    }
    return result;
  }

  private static Object[] compact(List<Object> expanded) {
    List<Object> locals = new ArrayList<>();
    for (int i = 0; i < expanded.size(); i++) {
      Object local = expanded.get(i);
      locals.add(local);
      if (local == Opcodes.LONG || local == Opcodes.DOUBLE) {
        i++;
      }
    }
    while (!locals.isEmpty() && locals.getLast() == Opcodes.TOP) {
      locals.removeLast();
    }
    return locals.toArray();
  }
}
