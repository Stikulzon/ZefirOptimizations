package ua.zefir.zefiroptimizations.process.sharedstate;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

final class StaticTypes {
  private static final String JDK_UTIL = "java/util/";
  private static final Set<String> SAFE_JDK_UTIL_PREFIXES =
      Set.of(
          "java/util/concurrent/",
          "java/util/function/",
          "java/util/regex/",
          "java/util/stream/",
          "java/util/logging/");
  private static final Set<String> SAFE_JDK_UTIL_TYPES =
      Set.of(
          "java/util/Optional",
          "java/util/OptionalInt",
          "java/util/OptionalLong",
          "java/util/OptionalDouble",
          "java/util/UUID",
          "java/util/Locale",
          "java/util/Comparator",
          "java/util/Base64",
          "java/util/Currency",
          "java/util/TimeZone");
  private static final String[] UNSAFE_PREFIXES = {
    "it/unimi/dsi/fastutil/", "com/google/common/collect/"
  };
  private static final String IMMUTABLE_PREFIX = "Immutable";
  private static final char SEPARATOR = '/';
  private static final String[] COMPILER_TABLES = {"$SwitchMap$", "$VALUES"};
  private static final Set<String> IMMUTABLE_ROOTS =
      Set.of("java/lang/Object", "java/lang/Record", "java/lang/Enum");
  private static final int MAX_SHAPE_DEPTH = 4;

  private static final String OBJECT = "Ljava/lang/Object;";
  private static final String CLASS_INITIALIZER = "<clinit>";
  private static final String NO_ARGUMENTS = "()V";
  private static final int FINAL_STATIC = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
  private static final int NO_CODE = Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE;
  private static final int MAX_VALUE_DEPTH = 4;
  private static final int MAX_HIERARCHY = 32;

  private final ModCode code;
  private final ClassLoader loader;
  private final String randomType;
  private final Map<String, Boolean> immutable = new ConcurrentHashMap<>();
  private final Map<String, Boolean> immutableStatics = new ConcurrentHashMap<>();
  private final Map<String, Boolean> immutableFactories = new ConcurrentHashMap<>();
  private final Map<String, Optional<ClassNode>> bodies = new ConcurrentHashMap<>();

  StaticTypes(ModCode code, ClassLoader loader, String randomType) {
    this.code = code;
    this.loader = loader;
    this.randomType = randomType;
  }

  static boolean isCompilerTable(String fieldName) {
    for (String prefix : COMPILER_TABLES) {
      if (fieldName.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  boolean unsafeToRead(String desc) {
    return unsafeToRead(desc, 0);
  }

  boolean unsafeStatic(String owner, String name, String desc) {
    return unsafeToRead(desc) && !holdsImmutable(owner, name, desc, 0);
  }

  boolean mayChange(String desc) {
    return desc == null || desc.equals(OBJECT) || unsafeToRead(desc, 0);
  }

  private boolean unsafeToRead(String desc, int depth) {
    Type type = Type.getType(desc);
    if (type.getSort() == Type.ARRAY) {
      return true;
    }
    if (type.getSort() != Type.OBJECT) {
      return false;
    }
    String name = type.getInternalName();
    if (name.equals(randomType)) {
      return true;
    }
    if (name.startsWith(JDK_UTIL)) {
      return unsafeJdkUtil(name);
    }
    for (String prefix : UNSAFE_PREFIXES) {
      if (name.startsWith(prefix)) {
        return !simpleName(name).startsWith(IMMUTABLE_PREFIX);
      }
    }
    return code.modOf(name) != null && !isImmutable(name, depth);
  }

  private static boolean unsafeJdkUtil(String name) {
    for (String prefix : SAFE_JDK_UTIL_PREFIXES) {
      if (name.startsWith(prefix)) {
        return false;
      }
    }
    return !SAFE_JDK_UTIL_TYPES.contains(name);
  }

  private static String simpleName(String name) {
    return name.substring(name.lastIndexOf(SEPARATOR) + 1);
  }

  private boolean isImmutable(String name, int depth) {
    return memoized(immutable, name, depth > MAX_SHAPE_DEPTH, () -> readIsImmutable(name, depth));
  }

  private static boolean memoized(
      Map<String, Boolean> cache, String key, boolean tooDeep, BooleanSupplier analysis) {
    Boolean cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    if (tooDeep) {
      return false;
    }
    cache.put(key, false);
    boolean result = analysis.getAsBoolean();
    cache.put(key, result);
    return result;
  }

  private boolean readIsImmutable(String name, int depth) {
    ClassNode node = read(name);
    if (node == null || (node.access & Opcodes.ACC_INTERFACE) != 0) {
      return false;
    }
    if ((node.access & Opcodes.ACC_ENUM) != 0) {
      return true;
    }
    if (!instanceFieldsSafe(node, depth)) {
      return false;
    }
    if (node.superName == null || IMMUTABLE_ROOTS.contains(node.superName)) {
      return true;
    }
    return code.modOf(node.superName) == null || isImmutable(node.superName, depth + 1);
  }

  private boolean instanceFieldsSafe(ClassNode node, int depth) {
    for (FieldNode field : node.fields) {
      if ((field.access & (Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE)) != 0) {
        continue;
      }
      if ((field.access & Opcodes.ACC_FINAL) == 0 || unsafeToRead(field.desc, depth + 1)) {
        return false;
      }
    }
    return true;
  }

  private boolean holdsImmutable(String owner, String name, String desc, int depth) {
    if (!unsafeToRead(desc)) {
      return true;
    }
    return memoized(
        immutableStatics,
        owner + '.' + name + ':' + desc,
        depth > MAX_VALUE_DEPTH,
        () -> readHoldsImmutable(owner, name, desc, depth));
  }

  private boolean readHoldsImmutable(String owner, String name, String desc, int depth) {
    ClassNode declaring = declaringClass(owner, name, desc);
    if (declaring == null) {
      return false;
    }
    MethodNode initializer = method(declaring, CLASS_INITIALIZER, NO_ARGUMENTS);
    if (initializer == null) {
      return false;
    }
    Frame<SourceValue>[] frames = analyze(declaring.name, initializer);
    if (frames == null) {
      return false;
    }
    SourceValue stored = onlyStoredValue(initializer, frames, declaring.name, name, desc);
    return stored != null && valueImmutable(initializer, frames, stored, depth);
  }

  private static SourceValue onlyStoredValue(
      MethodNode initializer, Frame<SourceValue>[] frames, String owner, String name, String desc) {
    AbstractInsnNode[] insns = initializer.instructions.toArray();
    SourceValue stored = null;
    for (int i = 0; i < insns.length; i++) {
      if (insns[i].getOpcode() != Opcodes.PUTSTATIC
          || !(insns[i] instanceof FieldInsnNode field)
          || !field.owner.equals(owner)
          || !field.name.equals(name)
          || !field.desc.equals(desc)) {
        continue;
      }
      if (stored != null || frames[i] == null) {
        return null;
      }
      stored = top(frames[i]);
    }
    return stored;
  }

  private ClassNode declaringClass(String owner, String name, String desc) {
    Deque<String> pending = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    pending.add(owner);
    while (!pending.isEmpty() && seen.size() < MAX_HIERARCHY) {
      String current = pending.poll();
      if (!seen.add(current)) {
        continue;
      }
      ClassNode node = body(current);
      if (node == null) {
        continue;
      }
      for (FieldNode field : node.fields) {
        if (field.name.equals(name) && field.desc.equals(desc)) {
          return (field.access & FINAL_STATIC) == FINAL_STATIC ? node : null;
        }
      }
      pending.addAll(node.interfaces);
      if (node.superName != null) {
        pending.add(node.superName);
      }
    }
    return null;
  }

  private boolean returnsImmutable(MethodInsnNode call, int depth) {
    if (!unsafeToRead(Type.getReturnType(call.desc).getDescriptor())) {
      return true;
    }
    return memoized(
        immutableFactories,
        call.owner + '.' + call.name + call.desc,
        depth > MAX_VALUE_DEPTH,
        () -> readReturnsImmutable(call, depth));
  }

  private boolean readReturnsImmutable(MethodInsnNode call, int depth) {
    ClassNode node = body(call.owner);
    MethodNode factory = node == null ? null : method(node, call.name, call.desc);
    if (factory == null
        || (factory.access & Opcodes.ACC_STATIC) == 0
        || (factory.access & NO_CODE) != 0) {
      return false;
    }
    Frame<SourceValue>[] frames = analyze(node.name, factory);
    if (frames == null) {
      return false;
    }
    AbstractInsnNode[] insns = factory.instructions.toArray();
    boolean returns = false;
    for (int i = 0; i < insns.length; i++) {
      if (insns[i].getOpcode() != Opcodes.ARETURN || frames[i] == null) {
        continue;
      }
      if (!valueImmutable(factory, frames, top(frames[i]), depth)) {
        return false;
      }
      returns = true;
    }
    return returns;
  }

  private boolean valueImmutable(
      MethodNode method, Frame<SourceValue>[] frames, SourceValue value, int depth) {
    if (value.insns.isEmpty()) {
      return false;
    }
    for (AbstractInsnNode insn : value.insns) {
      if (!producesImmutable(method, frames, insn, depth)) {
        return false;
      }
    }
    return true;
  }

  private boolean producesImmutable(
      MethodNode method, Frame<SourceValue>[] frames, AbstractInsnNode insn, int depth) {
    return switch (insn.getOpcode()) {
      case Opcodes.ACONST_NULL -> true;
      case Opcodes.LDC -> {
        Object constant = ((LdcInsnNode) insn).cst;
        yield constant instanceof String || constant instanceof Number || constant instanceof Type;
      }
      case Opcodes.NEW ->
          !unsafeToRead(Type.getObjectType(((TypeInsnNode) insn).desc).getDescriptor());
      case Opcodes.DUP, Opcodes.CHECKCAST -> {
        Frame<SourceValue> frame = frames[method.instructions.indexOf(insn)];
        yield frame != null && valueImmutable(method, frames, top(frame), depth);
      }
      case Opcodes.GETSTATIC -> {
        FieldInsnNode field = (FieldInsnNode) insn;
        yield holdsImmutable(field.owner, field.name, field.desc, depth + 1);
      }
      case Opcodes.INVOKESTATIC -> returnsImmutable((MethodInsnNode) insn, depth + 1);
      default -> false;
    };
  }

  private static SourceValue top(Frame<SourceValue> frame) {
    return frame.getStack(frame.getStackSize() - 1);
  }

  private static MethodNode method(ClassNode node, String name, String desc) {
    for (MethodNode method : node.methods) {
      if (method.name.equals(name) && method.desc.equals(desc)) {
        return method;
      }
    }
    return null;
  }

  private static Frame<SourceValue>[] analyze(String owner, MethodNode method) {
    try {
      return new Analyzer<>(new SourceInterpreter()).analyze(owner, method);
    } catch (AnalyzerException e) {
      SharedState.LOGGER.debug("Could not follow the values in {}.{}", owner, method.name, e);
      return null;
    }
  }

  private ClassNode body(String name) {
    return bodies.computeIfAbsent(name, this::readBody).orElse(null);
  }

  private Optional<ClassNode> readBody(String name) {
    try (InputStream in = loader.getResourceAsStream(name + ".class")) {
      if (in == null) {
        return Optional.empty();
      }
      ClassNode node = new ClassNode();
      new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      return Optional.of(node);
    } catch (Throwable t) {
      SharedState.LOGGER.error("Could not read {} to follow its static values", name, t);
      return Optional.empty();
    }
  }

  private ClassNode read(String name) {
    try (InputStream in = loader.getResourceAsStream(name + ".class")) {
      if (in == null) {
        return null;
      }
      ClassNode node = new ClassNode();
      new ClassReader(in)
          .accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      return node;
    } catch (Throwable t) {
      return null;
    }
  }
}
