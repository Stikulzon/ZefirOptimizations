package ua.zefir.zephyrium.process.ownership.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import ua.zefir.zephyrium.process.ownership.NameRefs;

public final class McNames {
  public final String entity;
  public final String world;
  final String dataTracker;
  public final String random;
  final Set<String> immutable;
  final Set<String> pureStatic;
  public final FieldInsnNode worldRandom;
  final MethodInsnNode dataTrackerGet;

  public final Set<String> neverGuarded;

  public final Set<String> castable;

  public final Set<String> untrackedCast;

  final Set<String> snapshotReadable;

  public final Set<String> entityOwned;

  private McNames(Map<String, List<AbstractInsnNode>> refs) {
    entity = onlyType(refs, "entity");
    world = onlyType(refs, "world");
    dataTracker = onlyType(refs, "dataTracker");
    random = onlyType(refs, "random");
    immutable = types(refs, "immutable");
    pureStatic = types(refs, "pureStatic");
    worldRandom = (FieldInsnNode) first(refs, "worldRandom", FieldInsnNode.class);
    dataTrackerGet = (MethodInsnNode) first(refs, "dataTrackerGet", MethodInsnNode.class);
    neverGuarded = methods(refs, "neverGuarded", false);
    castable = methods(refs, "castable", false);
    untrackedCast = methods(refs, "untrackedCast", false);
    snapshotReadable = methods(refs, "snapshotReadable", true);
    entityOwned = types(refs, "entityOwned");
  }

  private static Set<String> methods(
      Map<String, List<AbstractInsnNode>> refs, String key, boolean withOwner) {
    Set<String> methods = new HashSet<>();
    for (AbstractInsnNode insn : refs.get(key)) {
      if (insn instanceof MethodInsnNode method) {
        methods.add((withOwner ? method.owner + '.' : "") + method.name + method.desc);
      }
    }
    return methods;
  }

  public static McNames load(ClassLoader loader) throws IOException {
    String resource = NameRefs.class.getName().replace('.', '/') + ".class";
    try (InputStream in = loader.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IOException("Missing " + resource);
      }
      ClassNode node = new ClassNode();
      new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      Map<String, List<AbstractInsnNode>> refs = new HashMap<>();
      for (MethodNode method : node.methods) {
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
          continue;
        }
        List<AbstractInsnNode> insns = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
          if (insn instanceof LdcInsnNode
              || insn instanceof FieldInsnNode
              || insn instanceof MethodInsnNode) {
            insns.add(insn);
          }
        }
        refs.put(method.name, insns);
      }
      return new McNames(refs);
    }
  }

  private static String onlyType(Map<String, List<AbstractInsnNode>> refs, String key) {
    Set<String> types = types(refs, key);
    if (types.size() != 1) {
      throw new IllegalStateException("Expected one class literal in NameRefs." + key);
    }
    return types.iterator().next();
  }

  private static Set<String> types(Map<String, List<AbstractInsnNode>> refs, String key) {
    Set<String> types = new HashSet<>();
    for (AbstractInsnNode insn : refs.get(key)) {
      if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type) {
        types.add(type.getInternalName());
      }
    }
    return types;
  }

  private static AbstractInsnNode first(
      Map<String, List<AbstractInsnNode>> refs, String key, Class<?> kind) {
    for (AbstractInsnNode insn : refs.get(key)) {
      if (kind.isInstance(insn)) {
        return insn;
      }
    }
    throw new IllegalStateException("No member reference in NameRefs." + key);
  }
}
