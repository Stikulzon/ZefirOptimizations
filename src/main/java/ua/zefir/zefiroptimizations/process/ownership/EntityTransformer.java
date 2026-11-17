package ua.zefir.zefiroptimizations.process.ownership;

import static ua.zefir.zefiroptimizations.process.ownership.analysis.Bytecode.INVOCATION;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import ua.zefir.zefiroptimizations.process.Guards;
import ua.zefir.zefiroptimizations.process.ownership.analysis.Bytecode;
import ua.zefir.zefiroptimizations.process.ownership.analysis.ClassRepo;
import ua.zefir.zefiroptimizations.process.ownership.analysis.McNames;
import ua.zefir.zefiroptimizations.process.ownership.analysis.Purity;
import ua.zefir.zefiroptimizations.process.ownership.analysis.ThisInterpreter;
import ua.zefir.zefiroptimizations.process.snapshot.SnapshotReads;
import ua.zefir.zefiroptimizations.transform.Instrumenter;
import ua.zefir.zefiroptimizations.transform.MergedMixins;

final class EntityTransformer implements Instrumenter {
  private static final Set<String> NEVER_GUARDED =
      Set.of(
          "equals(Ljava/lang/Object;)Z",
          "hashCode()I",
          "toString()Ljava/lang/String;",
          "finalize()V",
          "clone()Ljava/lang/Object;");
  private static final String OBJ_DESC = "(Ljava/lang/Object;)";
  private static final String OWNER_FIELD = "zo$ownerThread";
  private static final String THREAD = "java/lang/Thread";
  private static final String THREAD_DESC = "L" + THREAD + ";";
  private static final String CALL_DESC =
      "(Ljava/lang/Object;L" + INVOCATION + ";)Ljava/lang/Object;";
  private static final String CAST_DESC = "(Ljava/lang/Object;L" + INVOCATION + ";)V";
  private static final String INVOKE_DESC = "(Ljava/lang/Object;)Ljava/lang/Object;";
  private static final int MAJOR_VERSION_MASK = 0xFFFF;
  private static final int GENERATED =
      Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
  private static final int UNGUARDABLE =
      Opcodes.ACC_STATIC
          | Opcodes.ACC_ABSTRACT
          | Opcodes.ACC_NATIVE
          | Opcodes.ACC_BRIDGE
          | Opcodes.ACC_SYNTHETIC
          | Opcodes.ACC_PRIVATE;

  private final ClassRepo repo;
  private final boolean directFieldGetters;
  private final McNames names;
  private final Purity purity;

  EntityTransformer(ClassRepo repo, McNames names, Purity purity, boolean directFieldGetters) {
    this.repo = repo;
    this.names = names;
    this.purity = purity;
    this.directFieldGetters = directFieldGetters;
  }

  @Override
  public byte[] instrument(String internalName, byte[] bytes) {
    try {
      if (bytes != null) {
        repo.recordLoaded(internalName, bytes, MergedMixins.present(bytes));
      }
      return transform(internalName, bytes);
    } catch (Throwable t) {
      Ownership.recordFailure(internalName, t);
      return bytes;
    }
  }

  @Override
  public boolean mightInstrument(String internalName) {
    return !ClassRepo.neverEntity(internalName);
  }

  @Override
  public void installed() {
    Ownership.markInstalled(names.entity);
  }

  byte[] transform(String internalName, byte[] bytes) {
    if (bytes == null || ClassRepo.neverEntity(internalName)) {
      return bytes;
    }
    ClassReader reader = new ClassReader(bytes);
    boolean isEntity = internalName.equals(names.entity) || repo.isEntity(reader.getSuperName());
    if (!isEntity && !touchesGuardedFields(reader)) {
      return bytes;
    }

    if (isEntity) {
      repo.keepBytes(internalName, bytes);
    }
    ClassNode node = new ClassNode();
    reader.accept(node, ClassReader.EXPAND_FRAMES);
    repo.register(node, isEntity);
    Context context = new Context(node);

    boolean changed = isEntity ? instrumentEntity(context) : rewriteFields(context, false);
    if (!changed) {
      return bytes;
    }
    node.methods.addAll(context.added);
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    node.accept(writer);
    return writer.toByteArray();
  }

  private boolean instrumentEntity(Context context) {
    ClassNode node = context.node;
    ReadKinds reads = classifyReads(node);
    boolean changed = false;
    for (MethodNode method : node.methods) {
      if (reads.pure.get(method)) {
        changed |= unsnapshotReturnedThis(node.name, method);
      }
    }
    changed |= rewriteFields(context, true);
    for (MethodNode method : new ArrayList<>(node.methods)) {
      if (reads.pure.containsKey(method)) {
        changed |= guard(context, method, reads.pure.get(method), reads.atomic.contains(method));
      }
    }
    return changed;
  }

  private ReadKinds classifyReads(ClassNode node) {
    ReadKinds reads = new ReadKinds(new HashMap<>(), new HashSet<>());
    for (MethodNode method : node.methods) {
      boolean isPure = purity.analyzeAndCache(node.name, method);
      reads.pure.put(method, isPure);
      if (isPure && purity.isAtomic(node.name, method)) {
        reads.atomic.add(method);
      }
    }
    return reads;
  }

  private boolean touchesGuardedFields(ClassReader reader) {
    boolean[] hit = {false};
    reader.accept(
        new ClassVisitor(Opcodes.ASM9) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            if (hit[0]) {
              return null;
            }
            return new MethodVisitor(Opcodes.ASM9) {
              @Override
              public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (!hit[0]
                    && (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD)
                    && (isWorldRandom(opcode, owner, name, descriptor) || repo.isEntity(owner))) {
                  hit[0] = true;
                }
              }
            };
          }
        },
        ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return hit[0];
  }

  private boolean isWorldRandom(int opcode, String owner, String name, String desc) {
    return opcode == Opcodes.GETFIELD
        && name.equals(names.worldRandom.name)
        && desc.equals(names.worldRandom.desc)
        && repo.isSubclassOf(owner, names.world);
  }

  private boolean guard(Context context, MethodNode method, boolean pure, boolean atomic) {
    String key = method.name + method.desc;
    if (isExemptFromGuard(method, key)) {
      return false;
    }
    if ((method.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
      Ownership.LOGGER.warn(
          "Not guarding synchronized entity method {}.{}", context.node.name, key);
      return false;
    }

    Target target = Target.of(context, method);
    Type ret = target.ret;
    boolean cast = !pure && ret.getSort() == Type.VOID && names.castable.contains(key);
    boolean untracked = cast && names.untrackedCast.contains(key);
    boolean maybeReadable =
        !pure && !cast && ret.getSort() != Type.VOID && purity.isSnapshottable(ret.getDescriptor());

    LabelNode direct = new LabelNode();
    InsnList g;
    if (pure) {
      g = pureReadGuard(target, atomic, direct);
    } else if (maybeReadable) {
      g = lazyReadGuard(target, key, direct);
    } else {
      g = routeGuard(target, Route.of(cast, untracked), direct);
    }
    g.add(direct);
    if (!startsWithFrame(method)) {
      g.add(Bytecode.frame(target.locals));
    }
    method.instructions.insert(g);
    return true;
  }

  private boolean isExemptFromGuard(MethodNode method, String key) {
    return (method.access & UNGUARDABLE) != 0
        || method.name.startsWith("<")
        || method.name.startsWith("zo$")
        || method.name.contains("$zo$")
        || NEVER_GUARDED.contains(key)
        || names.neverGuarded.contains(key)
        || method.instructions.size() == 0
        || Purity.isStateless(method)
        || readsUnsnapshottableField(method);
  }

  private InsnList pureReadGuard(Target target, boolean atomic, LabelNode direct) {
    InsnList g = new InsnList();
    LabelNode snapshot = new LabelNode();
    LabelNode call = new LabelNode();
    g.add(ownedHere(direct));
    g.add(new VarInsnNode(Opcodes.ALOAD, 0));
    g.add(Bytecode.guard(atomic ? "atomicReadMode" : "readMode", OBJ_DESC + "I"));
    g.add(new TableSwitchInsnNode(Guards.DIRECT, Guards.CALL, direct, direct, snapshot, call));

    g.add(snapshot);
    g.add(Bytecode.frame(target.locals));
    g.add(new VarInsnNode(Opcodes.ALOAD, 0));
    g.add(Bytecode.guard("snapshotOf", OBJ_DESC + "Ljava/lang/Object;"));
    g.add(new TypeInsnNode(Opcodes.CHECKCAST, target.owner()));
    Bytecode.loadAll(g, target.args, 1);
    g.add(
        new MethodInsnNode(
            Opcodes.INVOKESPECIAL, target.owner(), target.method.name, target.method.desc, false));
    g.add(Bytecode.returnInsn(target.ret));

    g.add(call);
    g.add(Bytecode.frame(target.locals));
    emitRouted(target, g, Route.CALL);
    return g;
  }

  private InsnList lazyReadGuard(Target target, String key, LabelNode direct) {
    InsnList g = new InsnList();
    LabelNode snapshot = new LabelNode();
    LabelNode call = new LabelNode();
    Impl impl = addInvocationImpl(target);
    g.add(ownedHere(direct));
    g.add(new VarInsnNode(Opcodes.ALOAD, 0));
    g.add(new LdcInsnNode(SnapshotReads.register(target.owner() + '.' + key)));
    g.add(Bytecode.guard("lazyReadMode", "(Ljava/lang/Object;I)I"));
    g.add(new TableSwitchInsnNode(Guards.DIRECT, Guards.CALL, direct, direct, snapshot, call));

    g.add(snapshot);
    g.add(Bytecode.frame(target.locals));
    emitInvoke(g, target, impl, "readSnapshot");

    g.add(call);
    g.add(Bytecode.frame(target.locals));
    emitInvoke(g, target, impl, "call");
    return g;
  }

  private InsnList routeGuard(Target target, Route route, LabelNode direct) {
    InsnList g = new InsnList();
    g.add(ownedHere(direct));
    g.add(new VarInsnNode(Opcodes.ALOAD, 0));
    g.add(Bytecode.guard("route", OBJ_DESC + "Z"));
    g.add(new JumpInsnNode(Opcodes.IFEQ, direct));
    emitRouted(target, g, route);
    return g;
  }

  private InsnList ownedHere(LabelNode direct) {
    InsnList g = new InsnList();
    g.add(new VarInsnNode(Opcodes.ALOAD, 0));
    g.add(new FieldInsnNode(Opcodes.GETFIELD, names.entity, OWNER_FIELD, THREAD_DESC));
    g.add(new JumpInsnNode(Opcodes.IFNULL, direct));
    g.add(new VarInsnNode(Opcodes.ALOAD, 0));
    g.add(new FieldInsnNode(Opcodes.GETFIELD, names.entity, OWNER_FIELD, THREAD_DESC));
    g.add(
        new MethodInsnNode(
            Opcodes.INVOKESTATIC, THREAD, "currentThread", "()" + THREAD_DESC, false));
    g.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, direct));
    return g;
  }

  private boolean readsUnsnapshottableField(MethodNode method) {
    if (!directFieldGetters) {
      return false;
    }
    FieldInsnNode field = Purity.getterField(method);
    return field != null && !purity.isSnapshottable(field.desc);
  }

  private boolean unsnapshotReturnedThis(String owner, MethodNode method) {
    if (Type.getReturnType(method.desc).getSort() != Type.OBJECT) {
      return false;
    }
    Frame<ThisInterpreter.ThisValue>[] frames = thisFrames(owner, method);
    if (frames == null) {
      return false;
    }
    String ret = Type.getReturnType(method.desc).getInternalName();
    AbstractInsnNode[] insns = method.instructions.toArray();
    boolean changed = false;
    for (int i = 0; i < insns.length; i++) {
      Frame<ThisInterpreter.ThisValue> frame = frames[i];
      if (insns[i].getOpcode() == Opcodes.ARETURN
          && frame != null
          && frame.getStack(frame.getStackSize() - 1).maybeThis) {
        method.instructions.insertBefore(insns[i], unsnapshot(ret));
        changed = true;
      }
    }
    return changed;
  }

  private static InsnList unsnapshot(String type) {
    InsnList unwrap = new InsnList();
    unwrap.add(Bytecode.guard("unsnapshot", OBJ_DESC + "Ljava/lang/Object;"));
    unwrap.add(new TypeInsnNode(Opcodes.CHECKCAST, type));
    return unwrap;
  }

  private static Frame<ThisInterpreter.ThisValue>[] thisFrames(String owner, MethodNode method) {
    try {
      return new Analyzer<>(new ThisInterpreter()).analyze(owner, method);
    } catch (Throwable t) {
      return null;
    }
  }

  private void emitRouted(Target target, InsnList g, Route route) {
    Impl impl = addInvocationImpl(target);
    g.add(new VarInsnNode(Opcodes.ALOAD, 0));
    Bytecode.loadAll(g, target.args, 1);
    g.add(
        Bytecode.invocation(
            target.args, target.owner(), impl.name, impl.desc, target.context.isInterface()));
    if (route == Route.CALL) {
      g.add(Bytecode.guard("call", CALL_DESC));
      Bytecode.unbox(g, target.ret);
      g.add(Bytecode.returnInsn(target.ret));
      return;
    }
    g.add(Bytecode.guard(route == Route.CAST_UNTRACKED ? "castUntracked" : "cast", CAST_DESC));
    g.add(new InsnNode(Opcodes.RETURN));
  }

  private Impl addInvocationImpl(Target target) {
    String owner = target.owner();
    String implName = target.context.newName("zo$inv$");
    String implDesc =
        Type.getMethodDescriptor(
            Type.getType(Object.class), Bytecode.append(target.args, Type.getType(Object.class)));
    MethodNode impl = new MethodNode(GENERATED, implName, implDesc, null, null);
    impl.instructions.add(new VarInsnNode(Opcodes.ALOAD, Bytecode.size(target.args)));
    impl.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, owner));
    Bytecode.loadAll(impl.instructions, target.args, 0);
    impl.instructions.add(
        new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL, owner, target.method.name, target.method.desc, false));
    Bytecode.box(impl.instructions, target.ret);
    impl.instructions.add(new InsnNode(Opcodes.ARETURN));
    target.context.added.add(impl);
    return new Impl(implName, implDesc);
  }

  private static void emitInvoke(InsnList g, Target target, Impl impl, String guardName) {
    g.add(new VarInsnNode(Opcodes.ALOAD, 0));
    Bytecode.loadAll(g, target.args, 1);
    g.add(Bytecode.invocation(target.args, target.owner(), impl.name, impl.desc, false));
    g.add(Bytecode.guard(guardName, CALL_DESC));
    Bytecode.unbox(g, target.ret);
    g.add(Bytecode.returnInsn(target.ret));
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

  private boolean rewriteFields(Context context, boolean entityClass) {
    boolean canGenerate =
        !context.isInterface() && (context.node.version & MAJOR_VERSION_MASK) >= Opcodes.V1_8;
    boolean changed = false;
    for (MethodNode method : context.node.methods) {
      changed |= rewriteFields(context, method, entityClass, canGenerate);
    }
    return changed;
  }

  private boolean rewriteFields(
      Context context, MethodNode method, boolean entityClass, boolean canGenerate) {
    List<FieldInsnNode> entityFields = new ArrayList<>();
    List<FieldInsnNode> randoms = new ArrayList<>();
    collectFieldAccesses(method, canGenerate, entityFields, randoms);
    boolean changed = replaceWorldRandoms(method, randoms);
    if (entityFields.isEmpty()) {
      return changed;
    }
    boolean routed = routeEntityFields(context, method, entityClass, entityFields);
    return changed || routed;
  }

  private void collectFieldAccesses(
      MethodNode method,
      boolean canGenerate,
      List<FieldInsnNode> entityFields,
      List<FieldInsnNode> randoms) {
    for (AbstractInsnNode insn : method.instructions) {
      if (!(insn instanceof FieldInsnNode field)
          || field.getOpcode() != Opcodes.GETFIELD && field.getOpcode() != Opcodes.PUTFIELD) {
        continue;
      }
      if (isWorldRandom(field.getOpcode(), field.owner, field.name, field.desc)) {
        randoms.add(field);
      } else if (canGenerate && repo.isEntity(field.owner)) {
        entityFields.add(field);
      }
    }
  }

  private static boolean replaceWorldRandoms(MethodNode method, List<FieldInsnNode> randoms) {
    for (FieldInsnNode field : randoms) {
      InsnList replacement = new InsnList();
      replacement.add(Bytecode.guard("worldRandom", OBJ_DESC + "Ljava/lang/Object;"));
      replacement.add(
          new TypeInsnNode(Opcodes.CHECKCAST, Type.getType(field.desc).getInternalName()));
      method.instructions.insert(field, replacement);
      method.instructions.remove(field);
    }
    return !randoms.isEmpty();
  }

  private boolean routeEntityFields(
      Context context, MethodNode method, boolean entityClass, List<FieldInsnNode> entityFields) {
    boolean instance = (method.access & Opcodes.ACC_STATIC) == 0;
    boolean constructor = method.name.equals("<init>");
    Frame<ThisInterpreter.ThisValue>[] frames =
        entityClass && instance && (method.access & Opcodes.ACC_SYNTHETIC) == 0
            ? thisFrames(context.node.name, method)
            : null;
    if (constructor && frames == null) {
      return false;
    }
    Map<AbstractInsnNode, Integer> index = frames != null ? indexOf(method) : Map.of();
    boolean changed = false;
    for (FieldInsnNode field : entityFields) {
      if (frames != null && !needsAccessor(frames[index.get(field)], field)) {
        continue;
      }
      MethodNode accessor = context.accessor(field, this);
      method.instructions.set(
          field,
          new MethodInsnNode(
              Opcodes.INVOKESTATIC, context.node.name, accessor.name, accessor.desc, false));
      changed = true;
    }
    return changed;
  }

  private static Map<AbstractInsnNode, Integer> indexOf(MethodNode method) {
    AbstractInsnNode[] insns = method.instructions.toArray();
    Map<AbstractInsnNode, Integer> index = new HashMap<>();
    for (int i = 0; i < insns.length; i++) {
      index.put(insns[i], i);
    }
    return index;
  }

  private static boolean needsAccessor(
      Frame<ThisInterpreter.ThisValue> frame, FieldInsnNode field) {
    if (frame == null) {
      return false;
    }
    int depth = field.getOpcode() == Opcodes.GETFIELD ? 0 : 1;
    return !frame.getStack(frame.getStackSize() - 1 - depth).definitelyThis;
  }

  MethodNode makeAccessor(Context context, FieldInsnNode field) {
    boolean get = field.getOpcode() == Opcodes.GETFIELD;
    String name = context.newName(get ? "zo$fg$" : "zo$fp$");
    String implName = context.newName(get ? "zo$fgi$" : "zo$fpi$");
    return get
        ? makeGetter(context, field, name, implName)
        : makeSetter(context, field, name, implName);
  }

  private MethodNode makeGetter(
      Context context, FieldInsnNode field, String name, String implName) {
    Type fieldType = Type.getType(field.desc);
    String desc = Type.getMethodDescriptor(fieldType, Type.getObjectType(field.owner));
    MethodNode accessor = new MethodNode(GENERATED, name, desc, null, null);
    InsnList a = accessor.instructions;
    LabelNode direct = new LabelNode();
    LabelNode call = new LabelNode();
    a.add(ownedHere(direct));
    a.add(new VarInsnNode(Opcodes.ALOAD, 0));
    if (purity.isSnapshottable(field.desc)) {
      addSnapshotRead(a, field, direct, call);
    } else {
      a.add(Bytecode.guard("route", OBJ_DESC + "Z"));
      a.add(new JumpInsnNode(Opcodes.IFNE, call));
      a.add(new JumpInsnNode(Opcodes.GOTO, direct));
    }
    a.add(direct);
    a.add(Bytecode.frame(field.owner));
    a.add(new VarInsnNode(Opcodes.ALOAD, 0));
    a.add(new FieldInsnNode(Opcodes.GETFIELD, field.owner, field.name, field.desc));
    a.add(Bytecode.returnInsn(fieldType));
    a.add(call);
    a.add(Bytecode.frame(field.owner));
    a.add(new VarInsnNode(Opcodes.ALOAD, 0));
    a.add(Bytecode.invocation(new Type[0], context.node.name, implName, INVOKE_DESC, false));
    a.add(Bytecode.guard("call", CALL_DESC));
    Bytecode.unbox(a, fieldType);
    a.add(Bytecode.returnInsn(fieldType));

    context.added.add(getterImpl(field, implName));
    return accessor;
  }

  private static void addSnapshotRead(
      InsnList a, FieldInsnNode field, LabelNode direct, LabelNode call) {
    LabelNode snapshot = new LabelNode();
    a.add(Bytecode.guard("atomicReadMode", OBJ_DESC + "I"));
    a.add(new TableSwitchInsnNode(Guards.DIRECT, Guards.CALL, direct, direct, snapshot, call));
    a.add(snapshot);
    a.add(Bytecode.frame(field.owner));
    a.add(new VarInsnNode(Opcodes.ALOAD, 0));
    a.add(Bytecode.guard("snapshotOf", OBJ_DESC + "Ljava/lang/Object;"));
    a.add(new TypeInsnNode(Opcodes.CHECKCAST, field.owner));
    a.add(new FieldInsnNode(Opcodes.GETFIELD, field.owner, field.name, field.desc));
    a.add(Bytecode.returnInsn(Type.getType(field.desc)));
  }

  private static MethodNode getterImpl(FieldInsnNode field, String implName) {
    MethodNode impl = new MethodNode(GENERATED, implName, INVOKE_DESC, null, null);
    impl.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    impl.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, field.owner));
    impl.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, field.owner, field.name, field.desc));
    Bytecode.box(impl.instructions, Type.getType(field.desc));
    impl.instructions.add(new InsnNode(Opcodes.ARETURN));
    return impl;
  }

  private MethodNode makeSetter(
      Context context, FieldInsnNode field, String name, String implName) {
    Type fieldType = Type.getType(field.desc);
    String desc =
        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getObjectType(field.owner), fieldType);
    MethodNode accessor = new MethodNode(GENERATED, name, desc, null, null);
    InsnList a = accessor.instructions;
    LabelNode direct = new LabelNode();
    LabelNode call = new LabelNode();
    a.add(ownedHere(direct));
    a.add(new VarInsnNode(Opcodes.ALOAD, 0));
    a.add(Bytecode.guard("route", OBJ_DESC + "Z"));
    a.add(new JumpInsnNode(Opcodes.IFNE, call));
    a.add(direct);
    a.add(Bytecode.frame(field.owner, Bytecode.frameType(fieldType)));
    a.add(new VarInsnNode(Opcodes.ALOAD, 0));
    a.add(new VarInsnNode(fieldType.getOpcode(Opcodes.ILOAD), 1));
    a.add(new FieldInsnNode(Opcodes.PUTFIELD, field.owner, field.name, field.desc));
    a.add(new InsnNode(Opcodes.RETURN));
    a.add(call);
    a.add(Bytecode.frame(field.owner, Bytecode.frameType(fieldType)));
    a.add(new VarInsnNode(Opcodes.ALOAD, 0));
    a.add(new VarInsnNode(fieldType.getOpcode(Opcodes.ILOAD), 1));
    String implDesc = "(" + field.desc + "Ljava/lang/Object;)Ljava/lang/Object;";
    a.add(
        Bytecode.invocation(new Type[] {fieldType}, context.node.name, implName, implDesc, false));
    a.add(Bytecode.guard("call", CALL_DESC));
    a.add(new InsnNode(Opcodes.POP));
    a.add(new InsnNode(Opcodes.RETURN));

    context.added.add(setterImpl(field, implName, implDesc));
    return accessor;
  }

  private static MethodNode setterImpl(FieldInsnNode field, String implName, String implDesc) {
    Type fieldType = Type.getType(field.desc);
    MethodNode impl = new MethodNode(GENERATED, implName, implDesc, null, null);
    impl.instructions.add(new VarInsnNode(Opcodes.ALOAD, fieldType.getSize()));
    impl.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, field.owner));
    impl.instructions.add(new VarInsnNode(fieldType.getOpcode(Opcodes.ILOAD), 0));
    impl.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, field.owner, field.name, field.desc));
    impl.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
    impl.instructions.add(new InsnNode(Opcodes.ARETURN));
    return impl;
  }

  private enum Route {
    CALL,
    CAST,
    CAST_UNTRACKED;

    static Route of(boolean cast, boolean untracked) {
      if (!cast) {
        return CALL;
      }
      return untracked ? CAST_UNTRACKED : CAST;
    }
  }

  private record Impl(String name, String desc) {}

  private record ReadKinds(Map<MethodNode, Boolean> pure, Set<MethodNode> atomic) {}

  private record Target(
      Context context, MethodNode method, Type[] args, Type ret, Object[] locals) {
    static Target of(Context context, MethodNode method) {
      Type[] args = Type.getArgumentTypes(method.desc);
      return new Target(
          context,
          method,
          args,
          Type.getReturnType(method.desc),
          Bytecode.locals(context.node.name, true, args));
    }

    String owner() {
      return context.node.name;
    }
  }

  static final class Context {
    final ClassNode node;
    final List<MethodNode> added = new ArrayList<>();
    private final Map<String, MethodNode> accessors = new HashMap<>();
    private int counter;

    Context(ClassNode node) {
      this.node = node;
    }

    boolean isInterface() {
      return (node.access & Opcodes.ACC_INTERFACE) != 0;
    }

    String newName(String prefix) {
      return prefix + counter++;
    }

    MethodNode accessor(FieldInsnNode field, EntityTransformer transformer) {
      String key = field.getOpcode() + field.owner + '.' + field.name + field.desc;
      MethodNode accessor = accessors.get(key);
      if (accessor == null) {
        accessor = transformer.makeAccessor(this, field);
        accessors.put(key, accessor);
        added.add(accessor);
      }
      return accessor;
    }
  }
}
