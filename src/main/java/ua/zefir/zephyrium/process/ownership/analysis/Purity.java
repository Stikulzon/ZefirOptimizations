package ua.zefir.zephyrium.process.ownership.analysis;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;

public final class Purity {
  private static final int MAX_READ_DEPTH = 8;
  private static final int NOT_ATOMIC = 2;
  private static final int LOAD_THIS = 0;
  private static final int READ_FIELD = 1;
  private static final int RETURN_FIELD = 2;
  private static final int GETTER_LENGTH = 3;
  private static final String JDK = "java/";
  private static final String JDK_UTIL = "java/util/";
  private static final String OBJECT = "java/lang/Object";
  private static final Set<String> CALLBACK_INFOS =
      Set.of(
          "org/spongepowered/asm/mixin/injection/callback/CallbackInfo",
          "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable");
  private static final String LEAK_MARKER = "l";
  private static final int UNANALYZABLE =
      Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED;
  private static final Set<String> READ_ONLY_JDK =
      Set.of(
          "get(Ljava/lang/Object;)Ljava/lang/Object;",
          "getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
          "containsKey(Ljava/lang/Object;)Z",
          "containsValue(Ljava/lang/Object;)Z",
          "contains(Ljava/lang/Object;)Z",
          "isEmpty()Z",
          "size()I",
          "get(I)Ljava/lang/Object;",
          "isPresent()Z",
          "iterator()Ljava/util/Iterator;",
          "hasNext()Z",
          "next()Ljava/lang/Object;",
          "orElse(Ljava/lang/Object;)Ljava/lang/Object;");

  private final Map<String, Boolean> readable = new ConcurrentHashMap<>();
  private final Map<String, Boolean> writeFree = new ConcurrentHashMap<>();
  private final ClassRepo repo;
  private final McNames names;
  private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
  private final ThreadLocal<Set<String>> inProgress = ThreadLocal.withInitial(HashSet::new);

  public Purity(ClassRepo repo, McNames names) {
    this.repo = repo;
    this.names = names;
  }

  public static boolean isStateless(MethodNode method) {
    for (AbstractInsnNode insn : method.instructions) {
      int op = insn.getOpcode();
      if (op < 0) {
        continue;
      }
      if (op <= Opcodes.SALOAD && op >= Opcodes.IALOAD
          || op >= Opcodes.IASTORE && op <= Opcodes.SASTORE
          || op == Opcodes.GETFIELD
          || op == Opcodes.PUTFIELD
          || op == Opcodes.PUTSTATIC
          || op >= Opcodes.INVOKEVIRTUAL && op <= Opcodes.INVOKEDYNAMIC
          || op == Opcodes.NEW
          || op == Opcodes.NEWARRAY
          || op == Opcodes.ANEWARRAY
          || op == Opcodes.MULTIANEWARRAY
          || op == Opcodes.ARRAYLENGTH
          || op == Opcodes.ATHROW
          || op == Opcodes.MONITORENTER
          || op == Opcodes.MONITOREXIT
          || op == Opcodes.JSR
          || op == Opcodes.RET) {
        return false;
      }
    }
    return true;
  }

  public static FieldInsnNode getterField(MethodNode method) {
    if ((method.access & Opcodes.ACC_STATIC) != 0) {
      return null;
    }
    FieldInsnNode field = null;
    int step = 0;
    for (AbstractInsnNode insn : method.instructions) {
      int op = insn.getOpcode();
      if (op < 0) {
        continue;
      }
      boolean matches =
          switch (step) {
            case LOAD_THIS -> op == Opcodes.ALOAD && ((VarInsnNode) insn).var == 0;
            case READ_FIELD -> op == Opcodes.GETFIELD && !isWide(((FieldInsnNode) insn).desc);
            case RETURN_FIELD -> op >= Opcodes.IRETURN && op <= Opcodes.ARETURN;
            default -> false;
          };
      if (!matches) {
        return null;
      }
      if (step == READ_FIELD) {
        field = (FieldInsnNode) insn;
      }
      step++;
    }
    return step == GETTER_LENGTH ? field : null;
  }

  private static boolean isWide(String desc) {
    int sort = Type.getType(desc).getSort();
    return sort == Type.LONG || sort == Type.DOUBLE;
  }

  public boolean isSnapshottable(String desc) {
    Type type = Type.getType(desc);
    int sort = type.getSort();
    if (sort != Type.OBJECT) {
      return sort != Type.ARRAY;
    }
    String name = type.getInternalName();
    return names.immutable.contains(name)
        || name.equals(names.dataTracker)
        || repo.isEntity(name)
        || repo.isSubclassOf(name, names.world)
        || repo.isEnum(name);
  }

  public boolean analyzeAndCache(String owner, MethodNode method) {
    String key = methodKey(owner, method.name, method.desc);
    boolean pure = names.snapshotReadable.contains(key) || analyze(owner, method);
    cache.put(key, pure);
    return pure;
  }

  private boolean isPure(String owner, String name, String desc) {
    String[] declaring = new String[1];
    MethodNode method = repo.findEntityMethod(owner, name, desc, declaring);
    if (method == null) {
      return false;
    }
    String key = methodKey(declaring[0], name, desc);
    if (names.snapshotReadable.contains(key)) {
      return true;
    }
    Boolean cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    return unlessReentered(
        key,
        () -> {
          boolean pure = analyze(declaring[0], method);
          cache.putIfAbsent(key, pure);
          return pure;
        });
  }

  private boolean analyze(String owner, MethodNode method) {
    if (hasAny(method.access, UNANALYZABLE)
        || isInitializer(method)
        || Type.getReturnType(method.desc).getSort() == Type.VOID
        || method.instructions.size() == 0
        || !method.tryCatchBlocks.isEmpty()) {
      return false;
    }
    Frame<ThisInterpreter.ThisValue>[] frames = frames(owner, method, new ThisInterpreter());
    return frames != null && everyReachable(method, frames, this::allowed);
  }

  private static Frame<ThisInterpreter.ThisValue>[] frames(
      String owner, MethodNode method, ThisInterpreter interpreter) {
    try {
      return new Analyzer<>(interpreter).analyze(owner, method);
    } catch (Throwable t) {
      return null;
    }
  }

  private static boolean everyReachable(
      MethodNode method, Frame<ThisInterpreter.ThisValue>[] frames, InsnCheck check) {
    AbstractInsnNode[] insns = method.instructions.toArray();
    for (int i = 0; i < insns.length; i++) {
      int op = insns[i].getOpcode();
      if (op >= 0 && frames[i] != null && !check.allows(insns[i], op, frames[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasAny(int access, int flags) {
    return (access & flags) != 0;
  }

  private static boolean isInitializer(MethodNode method) {
    return method.name.startsWith("<");
  }

  private static String methodKey(String owner, String name, String desc) {
    return owner + '.' + name + desc;
  }

  private boolean knownReadable(String key) {
    return Boolean.TRUE.equals(cache.get(key)) || names.snapshotReadable.contains(key);
  }

  private boolean unlessReentered(String marker, BooleanSupplier analysis) {
    Set<String> active = inProgress.get();
    if (!active.add(marker)) {
      return false;
    }
    try {
      return analysis.getAsBoolean();
    } finally {
      active.remove(marker);
    }
  }

  private boolean allowed(AbstractInsnNode insn, int op, Frame<ThisInterpreter.ThisValue> frame) {
    if (op <= Opcodes.ALOAD
        || op >= Opcodes.ISTORE && op <= Opcodes.ASTORE
        || op >= Opcodes.POP && op <= Opcodes.DCMPG
        || op >= Opcodes.IFEQ && op <= Opcodes.GOTO
        || op == Opcodes.TABLESWITCH
        || op == Opcodes.LOOKUPSWITCH
        || op == Opcodes.IFNULL
        || op == Opcodes.IFNONNULL
        || op == Opcodes.INSTANCEOF
        || op == Opcodes.CHECKCAST
        || op == Opcodes.GETSTATIC
        || op >= Opcodes.IRETURN && op <= Opcodes.DRETURN) {
      return true;
    }
    if (op >= Opcodes.IALOAD && op <= Opcodes.SALOAD) {
      return false;
    }
    switch (op) {
      case Opcodes.ARETURN:
        return true;
      case Opcodes.GETFIELD:
        {
          FieldInsnNode field = (FieldInsnNode) insn;
          if (repo.isEntity(field.owner)) {
            return isSnapshottable(field.desc);
          }
          return names.immutable.contains(field.owner);
        }
      case Opcodes.NEW:
        return names.immutable.contains(((TypeInsnNode) insn).desc);
      case Opcodes.INVOKEVIRTUAL:
      case Opcodes.INVOKESPECIAL:
        {
          MethodInsnNode call = (MethodInsnNode) insn;
          if (argumentsLeakThis(call, frame)) {
            return false;
          }
          if (call.name.equals("<init>")) {
            return names.immutable.contains(call.owner);
          }
          if (repo.isEntity(call.owner)) {
            return isPure(call.owner, call.name, call.desc);
          }
          return isDataTrackerGet(call) || names.immutable.contains(call.owner);
        }
      case Opcodes.INVOKESTATIC:
        {
          MethodInsnNode call = (MethodInsnNode) insn;
          return !argumentsLeakThis(call, frame) && names.pureStatic.contains(call.owner);
        }
      default:
        return false;
    }
  }

  public boolean isAtomic(String owner, MethodNode method) {
    Set<String> fields = new HashSet<>();
    int events = readEvents(owner, method, fields, 0);
    return events != NOT_ATOMIC && events + fields.size() <= 1;
  }

  private int readEvents(String owner, MethodNode method, Set<String> fields, int depth) {
    if (depth > MAX_READ_DEPTH) {
      return NOT_ATOMIC;
    }
    Frame<ThisInterpreter.ThisValue>[] frames = frames(owner, method, new ThisInterpreter());
    if (frames == null) {
      return NOT_ATOMIC;
    }
    InsnList insns = method.instructions;
    int events = 0;
    for (int i = 0; i < insns.size(); i++) {
      AbstractInsnNode insn = insns.get(i);
      if (insn instanceof JumpInsnNode jump && insns.indexOf(jump.label) < i) {
        return NOT_ATOMIC;
      }
      Frame<ThisInterpreter.ThisValue> frame = frames[i];
      if (frame == null) {
        continue;
      }
      int found = readEvents(insn, frame, fields, depth);
      if (found == NOT_ATOMIC) {
        return NOT_ATOMIC;
      }
      events += found;
      if (events + fields.size() > 1) {
        return NOT_ATOMIC;
      }
    }
    return events;
  }

  private int readEvents(
      AbstractInsnNode insn,
      Frame<ThisInterpreter.ThisValue> frame,
      Set<String> fields,
      int depth) {
    int op = insn.getOpcode();
    if (op == Opcodes.GETFIELD) {
      FieldInsnNode field = (FieldInsnNode) insn;
      if (receiver(frame, 0).maybeThis
          && repo.isEntity(field.owner)
          && !repo.isFinalEntityField(field.owner, field.name, field.desc)) {
        fields.add(field.name + field.desc);
      }
      return 0;
    }
    if (op != Opcodes.INVOKEVIRTUAL && op != Opcodes.INVOKESPECIAL) {
      return 0;
    }
    MethodInsnNode call = (MethodInsnNode) insn;
    if (isDataTrackerGet(call)) {
      return 1;
    }
    if (!repo.isEntity(call.owner)
        || !receiver(frame, Type.getArgumentTypes(call.desc).length).maybeThis) {
      return 0;
    }
    String[] declaring = new String[1];
    MethodNode callee = repo.findEntityMethod(call.owner, call.name, call.desc, declaring);
    if (callee == null) {
      return NOT_ATOMIC;
    }
    boolean bound =
        op == Opcodes.INVOKESPECIAL
            || hasAny(callee.access, Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE)
            || repo.isFinalClass(declaring[0]);
    return bound ? readEvents(declaring[0], callee, fields, depth + 1) : 1;
  }

  private static ThisInterpreter.ThisValue receiver(
      Frame<ThisInterpreter.ThisValue> frame, int arguments) {
    return frame.getStack(frame.getStackSize() - 1 - arguments);
  }

  public boolean isSnapshotReadable(String key) {
    return knownReadable(key) || readable(key, 0);
  }

  private boolean readable(String key, int depth) {
    Boolean cached = readable.get(key);
    if (cached != null) {
      return cached;
    }
    if (depth > MAX_READ_DEPTH) {
      return false;
    }
    return unlessReentered(
        "r" + key,
        () -> {
          String[] declaring = new String[1];
          MethodNode method = findByKey(key, declaring);
          boolean result =
              method != null && readOnlyEntityMethod(declaring[0], method, depth, depth == 0);
          readable.put(key, result);
          return result;
        });
  }

  private MethodNode findByKey(String key, String[] declaring) {
    int dot = key.indexOf('.');
    int paren = key.indexOf('(');
    return repo.findExactMethod(
        key.substring(0, dot), key.substring(dot + 1, paren), key.substring(paren), declaring);
  }

  private boolean readOnlyEntityMethod(
      String owner, MethodNode method, int depth, boolean returnsToCaller) {
    if (hasAny(method.access, UNANALYZABLE) || isInitializer(method)) {
      return false;
    }
    if (returnsToCaller && !returnsSnapshottable(method)) {
      return false;
    }
    Frame<ThisInterpreter.ThisValue>[] frames = frames(owner, method, new ThisInterpreter());
    return frames != null
        && everyReachable(method, frames, (insn, op, frame) -> readAllowed(insn, op, frame, depth));
  }

  private boolean returnsSnapshottable(MethodNode method) {
    Type ret = Type.getReturnType(method.desc);
    return ret.getSort() != Type.VOID && isSnapshottable(ret.getDescriptor());
  }

  private boolean writeFreeMethod(MethodNode method, int depth) {
    if (hasAny(
        method.access, Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED)) {
      return false;
    }
    for (AbstractInsnNode insn : method.instructions) {
      int op = insn.getOpcode();
      if (op >= 0 && !readAllowed(insn, op, null, depth)) {
        return false;
      }
    }
    return true;
  }

  private boolean readAllowed(
      AbstractInsnNode insn, int op, Frame<ThisInterpreter.ThisValue> frame, int depth) {
    if (op <= Opcodes.ALOAD
        || op >= Opcodes.ISTORE && op <= Opcodes.ASTORE
        || op >= Opcodes.POP && op <= Opcodes.DCMPG
        || op >= Opcodes.IFEQ && op <= Opcodes.GOTO
        || op >= Opcodes.IALOAD && op <= Opcodes.SALOAD
        || op >= Opcodes.IRETURN && op <= Opcodes.RETURN
        || op == Opcodes.TABLESWITCH
        || op == Opcodes.LOOKUPSWITCH
        || op == Opcodes.IFNULL
        || op == Opcodes.IFNONNULL
        || op == Opcodes.INSTANCEOF
        || op == Opcodes.CHECKCAST
        || op == Opcodes.GETSTATIC
        || op == Opcodes.GETFIELD
        || op == Opcodes.ARRAYLENGTH) {
      return true;
    }
    if (op == Opcodes.NEW) {
      String type = ((TypeInsnNode) insn).desc;
      return names.immutable.contains(type) || CALLBACK_INFOS.contains(type);
    }
    if (op < Opcodes.INVOKEVIRTUAL || op > Opcodes.INVOKEINTERFACE) {
      return false;
    }
    MethodInsnNode call = (MethodInsnNode) insn;
    if (frame != null && argumentsLeakThis(call, frame)) {
      return op == Opcodes.INVOKESTATIC && leakReadable(call, frame, depth + 1);
    }
    if (CALLBACK_INFOS.contains(call.owner)) {
      return true;
    }
    if (call.name.equals("<init>")) {
      return names.immutable.contains(call.owner);
    }
    if (names.immutable.contains(call.owner)
        || op == Opcodes.INVOKESTATIC && names.pureStatic.contains(call.owner)
        || isDataTrackerGet(call)) {
      return true;
    }
    if (op != Opcodes.INVOKESTATIC && repo.isEntity(call.owner)) {
      return entityCallReadable(call, depth);
    }
    return writeFreeCall(call, depth + 1);
  }

  private boolean isDataTrackerGet(MethodInsnNode call) {
    return call.owner.equals(names.dataTrackerGet.owner)
        && call.name.equals(names.dataTrackerGet.name)
        && call.desc.equals(names.dataTrackerGet.desc);
  }

  private boolean entityCallReadable(MethodInsnNode call, int depth) {
    String[] declaring = new String[1];
    MethodNode method = repo.findExactMethod(call.owner, call.name, call.desc, declaring);
    if (method == null) {
      return false;
    }
    if (names.immutable.contains(declaring[0])) {
      return true;
    }
    if (repo.isEntity(declaring[0]) && dispatchesThroughGuard(method)) {
      return true;
    }
    String key = methodKey(declaring[0], call.name, call.desc);
    return knownReadable(key) || readable(key, depth + 1);
  }

  private static boolean dispatchesThroughGuard(MethodNode method) {
    return !hasAny(
            method.access,
            Opcodes.ACC_STATIC
                | Opcodes.ACC_NATIVE
                | Opcodes.ACC_BRIDGE
                | Opcodes.ACC_SYNTHETIC
                | Opcodes.ACC_PRIVATE
                | Opcodes.ACC_SYNCHRONIZED)
        && !isInitializer(method);
  }

  private boolean writeFreeCall(MethodInsnNode call, int depth) {
    if (depth > MAX_READ_DEPTH || call.owner.startsWith("[")) {
      return false;
    }
    if (call.owner.startsWith(JDK_UTIL) || call.owner.equals(OBJECT)) {
      return READ_ONLY_JDK.contains(call.name + call.desc);
    }
    if (call.owner.startsWith(JDK) || call.getOpcode() == Opcodes.INVOKEINTERFACE) {
      return false;
    }
    String key = methodKey(call.owner, call.name, call.desc);
    Boolean cached = writeFree.get(key);
    if (cached != null) {
      return cached;
    }
    return unlessReentered(
        "w" + key,
        () -> {
          String[] declaring = new String[1];
          MethodNode method = repo.findExactMethod(call.owner, call.name, call.desc, declaring);
          boolean result = method != null && writeFreeMethod(method, depth);
          writeFree.put(key, result);
          return result;
        });
  }

  private boolean leakReadable(
      MethodInsnNode call, Frame<ThisInterpreter.ThisValue> frame, int depth) {
    if (depth > MAX_READ_DEPTH || call.owner.startsWith(JDK)) {
      return false;
    }
    long slots = leakedSlots(call, frame);
    String key = LEAK_MARKER + slots + methodKey(call.owner, call.name, call.desc);
    Boolean cached = writeFree.get(key);
    if (cached != null) {
      return cached;
    }
    return unlessReentered(
        key,
        () -> {
          String[] declaring = new String[1];
          MethodNode method = repo.findExactMethod(call.owner, call.name, call.desc, declaring);
          boolean result =
              method != null
                  && hasAny(method.access, Opcodes.ACC_STATIC)
                  && usesOnlyAsReceiver(declaring[0], method, slots, depth);
          writeFree.put(key, result);
          return result;
        });
  }

  private static long leakedSlots(MethodInsnNode call, Frame<ThisInterpreter.ThisValue> frame) {
    Type[] args = Type.getArgumentTypes(call.desc);
    long slots = 0L;
    int slot = 0;
    for (int i = 0; i < args.length; i++) {
      if (top(frame, args.length - 1 - i).maybeThis && slot < Long.SIZE) {
        slots |= 1L << slot;
      }
      slot += args[i].getSize();
    }
    return slots;
  }

  private boolean usesOnlyAsReceiver(String owner, MethodNode method, long slots, int depth) {
    if (hasAny(method.access, Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED)
        || !method.tryCatchBlocks.isEmpty()) {
      return false;
    }
    Frame<ThisInterpreter.ThisValue>[] frames = frames(owner, method, new ThisInterpreter(slots));
    return frames != null
        && everyReachable(
            method, frames, (insn, op, frame) -> usedOnlyAsReceiver(insn, op, frame, depth));
  }

  private boolean usedOnlyAsReceiver(
      AbstractInsnNode insn, int op, Frame<ThisInterpreter.ThisValue> frame, int depth) {
    return op != Opcodes.IF_ACMPEQ
        && op != Opcodes.IF_ACMPNE
        && !(op == Opcodes.ARETURN && top(frame, 0).maybeThis)
        && readAllowed(insn, op, frame, depth);
  }

  private static boolean argumentsLeakThis(
      MethodInsnNode call, Frame<ThisInterpreter.ThisValue> frame) {
    int args = Type.getArgumentTypes(call.desc).length;
    for (int i = 0; i < args; i++) {
      if (top(frame, i).maybeThis) {
        return true;
      }
    }
    return false;
  }

  private static ThisInterpreter.ThisValue top(
      Frame<ThisInterpreter.ThisValue> frame, int fromTop) {
    return frame.getStack(frame.getStackSize() - 1 - fromTop);
  }

  @FunctionalInterface
  private interface InsnCheck {
    boolean allows(AbstractInsnNode insn, int op, Frame<ThisInterpreter.ThisValue> frame);
  }
}
