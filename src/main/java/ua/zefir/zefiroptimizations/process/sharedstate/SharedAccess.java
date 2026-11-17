package ua.zefir.zefiroptimizations.process.sharedstate;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;

final class SharedAccess {
  private static final int MAX_CALL_DEPTH = 8;
  private static final int MAX_HIERARCHY = 32;
  private static final int VALUE_DEPTH = 1;
  private static final int FIELD_OWNER_DEPTH = 2;
  private static final int ARRAY_DEPTH = 3;
  private static final int NO_CODE = Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE;

  private final ModCode code;
  private final StaticTypes types;
  private final AddedFields added;
  private final ClassLoader loader;
  private final ReadCalls reads;
  private final Map<String, Optional<ClassNode>> bodies = new ConcurrentHashMap<>();
  private final Map<String, Boolean> summaries = new ConcurrentHashMap<>();
  private final ThreadLocal<Set<String>> inProgress = ThreadLocal.withInitial(HashSet::new);
  private final ThreadLocal<boolean[]> recursionAssumed =
      ThreadLocal.withInitial(() -> new boolean[1]);

  SharedAccess(
      ModCode code, StaticTypes types, AddedFields added, ReadCalls reads, ClassLoader loader) {
    this.code = code;
    this.reads = reads;
    this.types = types;
    this.added = added;
    this.loader = loader;
  }

  static boolean isShared(long mask, int parameter) {
    return parameter < Long.SIZE && (mask & (1L << parameter)) != 0;
  }

  boolean isSharedStatic(FieldInsnNode field) {
    return !StaticTypes.isCompilerTable(field.name)
        && types.unsafeStatic(field.owner, field.name, field.desc);
  }

  boolean isSharedField(FieldInsnNode field, boolean ownerShared) {
    return (ownerShared || added.onSharedGameObject(field.owner, field.name))
        && types.mayChange(field.desc);
  }

  boolean mayChange(String desc) {
    return types.mayChange(desc);
  }

  boolean isAddedToSharedObject(FieldInsnNode field) {
    return added.onSharedGameObject(field.owner, field.name);
  }

  Analysis analyze(String owner, MethodNode method, boolean constructing) throws AnalyzerException {
    Frame<SharedFlow.Flow>[] frames = frames(owner, method, 0L, constructing);
    boolean[] sharedOnStack = new boolean[frames.length];
    for (int i = 0; i < frames.length; i++) {
      sharedOnStack[i] = frames[i] != null && sharedOnStack(frames[i]);
    }
    return new Analysis(triggers(method, frames, false, 0), sharedOnStack);
  }

  private static boolean sharedOnStack(Frame<SharedFlow.Flow> frame) {
    for (int i = 0; i < frame.getStackSize(); i++) {
      if (frame.getStack(i).shared) {
        return true;
      }
    }
    return false;
  }

  private Frame<SharedFlow.Flow>[] frames(
      String owner, MethodNode method, long sharedParameters, boolean constructing)
      throws AnalyzerException {
    boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
    return new Analyzer<>(
            new SharedFlow(this, method.desc, isStatic, sharedParameters, constructing))
        .analyze(owner, method);
  }

  private List<AbstractInsnNode> triggers(
      String owner, MethodNode method, long sharedParameters, boolean firstOnly, int depth)
      throws AnalyzerException {
    return triggers(method, frames(owner, method, sharedParameters, false), firstOnly, depth);
  }

  private List<AbstractInsnNode> triggers(
      MethodNode method, Frame<SharedFlow.Flow>[] frames, boolean firstOnly, int depth) {
    AbstractInsnNode[] insns = method.instructions.toArray();
    List<AbstractInsnNode> found = new ArrayList<>();
    for (int i = 0; i < insns.length; i++) {
      Frame<SharedFlow.Flow> frame = frames[i];
      if (frame == null || !isTrigger(insns[i], frame, depth)) {
        continue;
      }
      found.add(insns[i]);
      if (firstOnly) {
        return found;
      }
    }
    return found;
  }

  private boolean isTrigger(AbstractInsnNode insn, Frame<SharedFlow.Flow> frame, int depth) {
    int opcode = insn.getOpcode();
    return switch (opcode) {
      case Opcodes.PUTSTATIC -> !publishes((FieldInsnNode) insn, frame);
      case Opcodes.PUTFIELD -> putFieldTouches((FieldInsnNode) insn, frame);
      case Opcodes.IASTORE,
          Opcodes.LASTORE,
          Opcodes.FASTORE,
          Opcodes.DASTORE,
          Opcodes.AASTORE,
          Opcodes.BASTORE,
          Opcodes.CASTORE,
          Opcodes.SASTORE ->
          stack(frame, ARRAY_DEPTH).shared || stack(frame, VALUE_DEPTH).shared;
      case Opcodes.INVOKEVIRTUAL,
          Opcodes.INVOKESPECIAL,
          Opcodes.INVOKESTATIC,
          Opcodes.INVOKEINTERFACE ->
          callTouches((MethodInsnNode) insn, frame, depth);
      default -> false;
    };
  }

  private boolean putFieldTouches(FieldInsnNode field, Frame<SharedFlow.Flow> frame) {
    if (stack(frame, VALUE_DEPTH).shared) {
      return true;
    }
    SharedFlow.Flow owner = stack(frame, FIELD_OWNER_DEPTH);
    boolean sharedOwner = owner.shared || (!owner.constructing && isAddedToSharedObject(field));
    return sharedOwner && !publishes(field, frame);
  }

  private boolean publishes(FieldInsnNode field, Frame<SharedFlow.Flow> frame) {
    int sort = Type.getType(field.desc).getSort();
    return (sort == Type.OBJECT || sort == Type.ARRAY)
        && !stack(frame, VALUE_DEPTH).shared
        && isVolatile(field);
  }

  private boolean isVolatile(FieldInsnNode field) {
    String current = field.owner;
    for (int i = 0; i < MAX_HIERARCHY && current != null; i++) {
      ClassNode node = body(current);
      if (node == null) {
        return false;
      }
      for (FieldNode declared : node.fields) {
        if (declared.name.equals(field.name) && declared.desc.equals(field.desc)) {
          return (declared.access & Opcodes.ACC_VOLATILE) != 0;
        }
      }
      current = node.superName;
    }
    return false;
  }

  private static SharedFlow.Flow stack(Frame<SharedFlow.Flow> frame, int fromTop) {
    return frame.getStack(frame.getStackSize() - fromTop);
  }

  private boolean callTouches(MethodInsnNode call, Frame<SharedFlow.Flow> frame, int depth) {
    if (code.modOf(call.owner) == null && isKnownRead(call)) {
      return false;
    }
    int operands =
        Type.getArgumentTypes(call.desc).length
            + (call.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1);
    long mask = 0L;
    int first = frame.getStackSize() - operands;
    for (int i = 0; i < operands; i++) {
      if (!frame.getStack(first + i).shared) {
        continue;
      }
      if (i >= Long.SIZE) {
        return true;
      }
      mask |= 1L << i;
    }
    return mask != 0L && summaryTouches(call, mask, depth + 1);
  }

  private boolean isKnownRead(MethodInsnNode call) {
    return ReadCalls.isCollectionCopy(call.owner, call.name)
        || ReadCalls.isStaticRead(call.owner, call.name)
        || (call.getOpcode() != Opcodes.INVOKESTATIC && reads.isRead(call.owner, call.name));
  }

  private boolean summaryTouches(MethodInsnNode call, long mask, int depth) {
    if (call.getOpcode() == Opcodes.INVOKEINTERFACE || code.modOf(call.owner) == null) {
      return true;
    }
    Resolved resolved = resolve(call.owner, call.name, call.desc);
    if (resolved == null || (resolved.method.access & NO_CODE) != 0) {
      return true;
    }
    String key = resolved.owner + '.' + call.name + call.desc + '#' + mask;
    Boolean cached = summaries.get(key);
    if (cached != null) {
      return cached;
    }
    return summarize(key, resolved, mask, depth);
  }

  private boolean summarize(String key, Resolved resolved, long mask, int depth) {
    Set<String> active = inProgress.get();
    boolean[] assumed = recursionAssumed.get();
    if (active.contains(key)) {
      assumed[0] = true;
      return false;
    }
    if (depth > MAX_CALL_DEPTH) {
      return true;
    }
    active.add(key);
    boolean outerAssumed = assumed[0];
    assumed[0] = false;
    boolean touches;
    try {
      touches = anyTrigger(resolved, mask, depth);
    } finally {
      active.remove(key);
    }
    if (touches || !assumed[0] || active.isEmpty()) {
      summaries.put(key, touches);
    }
    assumed[0] |= outerAssumed;
    return touches;
  }

  private boolean anyTrigger(Resolved resolved, long mask, int depth) {
    try {
      return !triggers(resolved.owner, resolved.method, mask, true, depth).isEmpty();
    } catch (Throwable t) {
      return true;
    }
  }

  private ClassNode body(String internalName) {
    return bodies.computeIfAbsent(internalName, this::read).orElse(null);
  }

  private Resolved resolve(String owner, String name, String desc) {
    String current = owner;
    for (int i = 0; i < MAX_HIERARCHY && current != null && code.modOf(current) != null; i++) {
      ClassNode node = body(current);
      if (node == null) {
        return null;
      }
      for (MethodNode method : node.methods) {
        if (method.name.equals(name) && method.desc.equals(desc)) {
          return new Resolved(node.name, method);
        }
      }
      current = node.superName;
    }
    return null;
  }

  private Optional<ClassNode> read(String internalName) {
    try (InputStream in = loader.getResourceAsStream(internalName + ".class")) {
      if (in == null) {
        return Optional.empty();
      }
      ClassNode node = new ClassNode();
      new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      return Optional.of(node);
    } catch (Throwable t) {
      SharedState.LOGGER.error(
          "Could not read {} to follow shared values into it", internalName, t);
      return Optional.empty();
    }
  }

  record Analysis(List<AbstractInsnNode> triggers, boolean[] sharedOnStack) {}

  private record Resolved(String owner, MethodNode method) {}
}
