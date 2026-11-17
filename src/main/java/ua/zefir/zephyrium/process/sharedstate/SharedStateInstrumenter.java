package ua.zefir.zephyrium.process.sharedstate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodTooLargeException;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import ua.zefir.zephyrium.process.ownership.analysis.ClassRepo;
import ua.zefir.zephyrium.transform.Instrumenter;
import ua.zefir.zephyrium.transform.MergedMixins;

final class SharedStateInstrumenter implements Instrumenter {
  private static final String CLASS_INITIALIZER = "<clinit>";
  private static final String CONSTRUCTOR = "<init>";
  private static final String OWN_PREFIX = "zo$";
  private static final String OWN_INFIX = "$zo$";
  private static final String NOT_ROUTED = "its code could not be routed";
  private static final int MAJOR_VERSION_MASK = 0xFFFF;
  private static final int SKIPPED_ACCESS =
      Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_BRIDGE;

  private final ModCode code;
  private final StaticTypes types;
  private final SharedAccess access;
  private final boolean splitRoutedMethods;

  SharedStateInstrumenter(
      ModCode code, StaticTypes types, SharedAccess access, boolean splitRoutedMethods) {
    this.code = code;
    this.types = types;
    this.access = access;
    this.splitRoutedMethods = splitRoutedMethods;
  }

  @Override
  public Object enterClassLoading() {
    SharedState.enterClassLoading();
    return null;
  }

  @Override
  public void exitClassLoading(Object token) {
    SharedState.exitClassLoading();
  }

  @Override
  public boolean mightInstrument(String internalName) {
    return code.modOf(internalName) != null || !ClassRepo.neverEntity(internalName);
  }

  @Override
  public byte[] instrument(String internalName, byte[] bytes) {
    if (bytes == null) {
      return bytes;
    }
    String classMod = code.modOf(internalName);
    if (classMod == null && (ClassRepo.neverEntity(internalName) || !hasForeignMixins(bytes))) {
      return bytes;
    }
    try {
      return transform(bytes, classMod);
    } catch (Throwable t) {
      SharedState.LOGGER.error(
          "Could not route shared state in {}; entities that run its code tick on the server"
              + " thread",
          internalName,
          t);
      SharedState.mark(internalName, NOT_ROUTED);
      return bytes;
    }
  }

  private boolean hasForeignMixins(byte[] bytes) {
    for (String mixin : MergedMixins.of(bytes)) {
      if (code.modOf(mixin) != null) {
        return true;
      }
    }
    return false;
  }

  byte[] transform(byte[] bytes, String classMod) {
    Set<String> siteOnly = new HashSet<>();
    while (true) {
      try {
        return transformOnce(bytes, classMod, siteOnly);
      } catch (MethodTooLargeException e) {
        if (!siteOnly.add(e.getMethodName() + e.getDescriptor())) {
          throw e;
        }
      }
    }
  }

  private byte[] transformOnce(byte[] bytes, String classMod, Set<String> siteOnly) {
    ClassNode node = new ClassNode();
    new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
    Pass pass = new Pass(node, constructingHelpers(node), siteOnly);
    boolean changed = false;
    for (MethodNode method : new ArrayList<>(node.methods)) {
      String codeMod = classMod != null ? classMod : mergedFrom(method);
      if (codeMod == null || skipped(method)) {
        continue;
      }
      changed |= instrumentMethod(pass, method, codeMod);
    }
    if (!changed) {
      return bytes;
    }
    node.methods.addAll(pass.routing.added);
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    node.accept(writer);
    byte[] result = writer.toByteArray();
    SharedState.routedMethods(pass.routedMethods);
    return result;
  }

  private boolean instrumentMethod(Pass pass, MethodNode method, String codeMod) {
    String owner = pass.node.name;
    SharedAccess.Analysis analysis;
    try {
      analysis = access.analyze(owner, method, pass.runsOnNewObject(method));
    } catch (Throwable t) {
      SharedState.LOGGER.debug(
          "Could not follow shared values in {}.{}{}", owner, method.name, method.desc, t);
      return alarmStatics(owner, method, codeMod);
    }
    List<AbstractInsnNode> triggers = analysis.triggers();
    if (triggers.isEmpty()) {
      return false;
    }
    if (!pass.canGenerate) {
      alarmTriggers(owner, method, codeMod, triggers);
      return true;
    }
    pass.routedMethods++;
    SharedState.routeReason(owner, site(codeMod, owner, method, triggers.getFirst()));
    if (pass.routesWholeMethod(method)) {
      routeWholeMethod(pass, method, analysis);
    } else {
      routeSites(pass, method, codeMod, triggers);
    }
    return true;
  }

  private static void alarmTriggers(
      String owner, MethodNode method, String codeMod, List<AbstractInsnNode> triggers) {
    for (AbstractInsnNode trigger : triggers) {
      method.instructions.insertBefore(
          trigger, Routing.touch(owner, site(codeMod, owner, method, trigger)));
    }
  }

  private void routeWholeMethod(Pass pass, MethodNode method, SharedAccess.Analysis analysis) {
    List<AbstractInsnNode> triggers = analysis.triggers();
    TailSplit split =
        splitRoutedMethods
            ? TailSplit.find(pass.node.name, method, triggers, analysis.sharedOnStack())
            : null;
    if (split != null) {
      pass.routing.routeTail(method, split);
      return;
    }
    pass.routing.routeMethod(method, triggers);
  }

  private static void routeSites(
      Pass pass, MethodNode method, String codeMod, List<AbstractInsnNode> triggers) {
    String owner = pass.node.name;
    for (AbstractInsnNode trigger : triggers) {
      pass.routing.routeSite(method, trigger, site(codeMod, owner, method, trigger));
    }
  }

  private static final class Pass {
    private final ClassNode node;
    private final Routing routing;
    private final Set<String> constructing;
    private final Set<String> siteOnly;
    private final boolean isInterface;
    private final boolean canGenerate;
    private int routedMethods;

    private Pass(ClassNode node, Set<String> constructing, Set<String> siteOnly) {
      this.node = node;
      this.routing = new Routing(node);
      this.constructing = constructing;
      this.siteOnly = siteOnly;
      this.isInterface = (node.access & Opcodes.ACC_INTERFACE) != 0;
      int major = node.version & MAJOR_VERSION_MASK;
      this.canGenerate = major >= (isInterface ? Opcodes.V9 : Opcodes.V1_8);
    }

    private boolean runsOnNewObject(MethodNode method) {
      return method.name.equals(CONSTRUCTOR) || constructing.contains(method.name + method.desc);
    }

    private boolean routesWholeMethod(MethodNode method) {
      return !isInterface
          && !method.name.equals(CONSTRUCTOR)
          && !siteOnly.contains(method.name + method.desc);
    }
  }

  private static Set<String> constructingHelpers(ClassNode node) {
    Set<String> fromConstructors = new HashSet<>();
    Set<String> fromElsewhere = new HashSet<>();
    collectSelfCalls(node, fromConstructors, fromElsewhere);
    Set<String> helpers = new HashSet<>();
    for (MethodNode method : node.methods) {
      String key = method.name + method.desc;
      if ((method.access & Opcodes.ACC_PRIVATE) != 0
          && (method.access & Opcodes.ACC_STATIC) == 0
          && fromConstructors.contains(key)
          && !fromElsewhere.contains(key)) {
        helpers.add(key);
      }
    }
    return helpers;
  }

  private static void collectSelfCalls(
      ClassNode node, Set<String> fromConstructors, Set<String> fromElsewhere) {
    for (MethodNode method : node.methods) {
      Frame<SourceValue>[] frames = method.name.equals(CONSTRUCTOR) ? sources(node, method) : null;
      AbstractInsnNode[] insns = method.instructions.toArray();
      for (int i = 0; i < insns.length; i++) {
        if (!(insns[i] instanceof MethodInsnNode call)
            || !call.owner.equals(node.name)
            || call.name.equals(CONSTRUCTOR)) {
          continue;
        }
        boolean onThis =
            frames != null
                && frames[i] != null
                && call.getOpcode() == Opcodes.INVOKESPECIAL
                && receiverIsThis(frames[i], call);
        (onThis ? fromConstructors : fromElsewhere).add(call.name + call.desc);
      }
    }
  }

  private static Frame<SourceValue>[] sources(ClassNode node, MethodNode method) {
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof VarInsnNode store
          && store.var == 0
          && store.getOpcode() == Opcodes.ASTORE) {
        return null;
      }
    }
    try {
      return new Analyzer<>(new SourceInterpreter()).analyze(node.name, method);
    } catch (AnalyzerException e) {
      SharedState.LOGGER.debug(
          "Could not follow this in {}.{}{}", node.name, method.name, method.desc, e);
      return null;
    }
  }

  private static boolean receiverIsThis(Frame<SourceValue> frame, MethodInsnNode call) {
    int receiver = frame.getStackSize() - Type.getArgumentTypes(call.desc).length - 1;
    for (AbstractInsnNode source : frame.getStack(receiver).insns) {
      if (!(source instanceof VarInsnNode load)
          || load.getOpcode() != Opcodes.ALOAD
          || load.var != 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean skipped(MethodNode method) {
    return (method.access & SKIPPED_ACCESS) != 0
        || method.name.equals(CLASS_INITIALIZER)
        || method.name.startsWith(OWN_PREFIX)
        || method.name.contains(OWN_INFIX)
        || method.instructions.size() == 0;
  }

  private boolean alarmStatics(String owner, MethodNode method, String codeMod) {
    boolean changed = false;
    for (AbstractInsnNode insn : method.instructions.toArray()) {
      if (!(insn instanceof FieldInsnNode field) || !touchesUnsafeStatic(field)) {
        continue;
      }
      method.instructions.insertBefore(
          insn, Routing.touch(owner, site(codeMod, owner, method, insn)));
      changed = true;
    }
    return changed;
  }

  private boolean touchesUnsafeStatic(FieldInsnNode field) {
    if (field.getOpcode() == Opcodes.PUTSTATIC) {
      return true;
    }
    return field.getOpcode() == Opcodes.GETSTATIC
        && !StaticTypes.isCompilerTable(field.name)
        && types.unsafeStatic(field.owner, field.name, field.desc);
  }

  private String mergedFrom(MethodNode method) {
    String mixin = mixinOf(method.visibleAnnotations);
    if (mixin == null) {
      mixin = mixinOf(method.invisibleAnnotations);
    }
    return mixin == null ? null : code.modOf(mixin.replace('.', '/'));
  }

  private static String mixinOf(List<AnnotationNode> annotations) {
    if (annotations == null) {
      return null;
    }
    for (AnnotationNode annotation : annotations) {
      if (!annotation.desc.equals(MergedMixins.DESCRIPTOR) || annotation.values == null) {
        continue;
      }
      for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
        if (MergedMixins.MIXIN_KEY.equals(annotation.values.get(i))
            && annotation.values.get(i + 1) instanceof String name) {
          return name;
        }
      }
    }
    return null;
  }

  static String site(String codeMod, String owner, MethodNode method, AbstractInsnNode insn) {
    String target =
        switch (insn) {
          case FieldInsnNode field -> field.owner.replace('/', '.') + '.' + field.name;
          case MethodInsnNode call -> call.owner.replace('/', '.') + '.' + call.name;
          default -> "an array or monitor";
        };
    return codeMod
        + ": "
        + owner.replace('/', '.')
        + '.'
        + method.name
        + (insn.getOpcode() == Opcodes.GETSTATIC ? " reads " : " touches ")
        + target;
  }
}
