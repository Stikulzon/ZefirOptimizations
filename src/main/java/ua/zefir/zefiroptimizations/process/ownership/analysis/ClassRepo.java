package ua.zefir.zefiroptimizations.process.ownership.analysis;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public final class ClassRepo {
  private static final int MAX_HIERARCHY_DEPTH = 64;
  private static final int DEFAULT_EXCLUDED =
      Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE;

  private static final String[] NEVER_ENTITY_PREFIXES = {
    "java/",
    "javax/",
    "jdk/",
    "sun/",
    "com/sun/",
    "com/google/",
    "com/mojang/",
    "it/unimi/",
    "org/objectweb/",
    "org/spongepowered/",
    "org/slf4j/",
    "org/apache/",
    "io/netty/",
    "net/fabricmc/loader/",
    "com/llamalad7/",
    "ua/zefir/zefiroptimizations/"
  };

  record Header(String superName, int access) {}

  private final ClassLoader loader;
  private final String entityRoot;
  private final Map<String, Optional<Header>> headers = new ConcurrentHashMap<>();
  private final Map<String, Optional<ClassNode>> entityBodies = new ConcurrentHashMap<>();
  private final Map<String, Boolean> subclassCache = new ConcurrentHashMap<>();
  private final Set<String> loaded = ConcurrentHashMap.newKeySet();
  private final Map<String, byte[]> loadedBytes = new ConcurrentHashMap<>();
  private final Map<String, Optional<ClassNode>> exactBodies = new ConcurrentHashMap<>();

  public ClassRepo(ClassLoader loader, String entityRoot) {
    this.loader = loader;
    this.entityRoot = entityRoot;
  }

  public void register(ClassNode node, boolean isEntity) {
    headers.put(node.name, Optional.of(new Header(node.superName, node.access)));
    if (isEntity) {
      entityBodies.put(node.name, Optional.of(node));
    }
  }

  public void recordLoaded(String internalName, byte[] bytes, boolean keepBytes) {
    if (keepBytes) {
      loadedBytes.put(internalName, bytes);
    }
    loaded.add(internalName);
  }

  public void keepBytes(String internalName, byte[] bytes) {
    loadedBytes.put(internalName, bytes);
  }

  ClassNode exactBody(String internalName) {
    if (internalName == null || internalName.startsWith("[")) {
      return null;
    }
    return exactBodies.computeIfAbsent(internalName, this::readExactBody).orElse(null);
  }

  private Optional<ClassNode> readExactBody(String internalName) {
    byte[] bytes = loadedBytes.get(internalName);
    if (bytes == null && loaded.contains(internalName)) {
      bytes = bytes(internalName);
    }
    if (bytes == null) {
      return Optional.empty();
    }
    ClassNode node = new ClassNode();
    new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return Optional.of(node);
  }

  MethodNode findExactMethod(String owner, String name, String desc, String[] declaringOut) {
    Deque<String> interfaces = new ArrayDeque<>();
    String current = owner;
    for (int depth = 0; current != null && depth < MAX_HIERARCHY_DEPTH; depth++) {
      ClassNode node = exactBody(current);
      if (node == null) {
        break;
      }
      MethodNode method = declared(node, name, desc);
      if (method != null) {
        declaringOut[0] = current;
        return method;
      }
      interfaces.addAll(node.interfaces);
      if ((node.access & Opcodes.ACC_INTERFACE) != 0) {
        break;
      }
      current = node.superName;
    }
    return findDefaultMethod(interfaces, name, desc, declaringOut);
  }

  private MethodNode findDefaultMethod(
      Deque<String> pending, String name, String desc, String[] declaringOut) {
    Set<String> seen = new HashSet<>();
    while (!pending.isEmpty() && seen.size() < MAX_HIERARCHY_DEPTH) {
      String current = pending.poll();
      if (!seen.add(current)) {
        continue;
      }
      ClassNode node = exactBody(current);
      if (node == null) {
        continue;
      }
      MethodNode method = declared(node, name, desc);
      if (method != null && (method.access & DEFAULT_EXCLUDED) == 0) {
        declaringOut[0] = current;
        return method;
      }
      pending.addAll(node.interfaces);
    }
    return null;
  }

  private static MethodNode declared(ClassNode node, String name, String desc) {
    for (MethodNode method : node.methods) {
      if (method.name.equals(name) && method.desc.equals(desc)) {
        return method;
      }
    }
    return null;
  }

  Header header(String internalName) {
    if (internalName == null || internalName.startsWith("[")) {
      return null;
    }
    return headers.computeIfAbsent(internalName, this::readHeader).orElse(null);
  }

  private Optional<Header> readHeader(String internalName) {
    byte[] bytes = bytes(internalName);
    if (bytes == null) {
      return Optional.empty();
    }
    ClassReader reader = new ClassReader(bytes);
    return Optional.of(new Header(reader.getSuperName(), reader.getAccess()));
  }

  private ClassNode body(String internalName) {
    return entityBodies
        .computeIfAbsent(
            internalName,
            name -> {
              byte[] bytes = bytes(name);
              if (bytes == null) {
                return Optional.empty();
              }
              ClassNode node = new ClassNode();
              new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
              return Optional.of(node);
            })
        .orElse(null);
  }

  private byte[] bytes(String internalName) {
    try (InputStream in = loader.getResourceAsStream(internalName + ".class")) {
      return in == null ? null : in.readAllBytes();
    } catch (Throwable t) {
      return null;
    }
  }

  public static boolean neverEntity(String internalName) {
    for (String prefix : NEVER_ENTITY_PREFIXES) {
      if (internalName.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  public boolean isEntity(String internalName) {
    return isSubclassOf(internalName, entityRoot);
  }

  public boolean isSubclassOf(String internalName, String base) {
    if (internalName == null || internalName.startsWith("[")) {
      return false;
    }
    if (internalName.equals(base)) {
      return true;
    }
    if (neverEntity(internalName)) {
      return false;
    }
    String key = internalName + '>' + base;
    Boolean cached = subclassCache.get(key);
    if (cached != null) {
      return cached;
    }
    boolean result = false;
    String current = internalName;
    for (int depth = 0; current != null && depth < MAX_HIERARCHY_DEPTH; depth++) {
      if (current.equals(base)) {
        result = true;
        break;
      }
      Header header = header(current);
      if (header == null) {
        break;
      }
      current = header.superName();
    }
    subclassCache.put(key, result);
    return result;
  }

  boolean isFinalClass(String internalName) {
    Header header = header(internalName);
    return header != null && (header.access() & Opcodes.ACC_FINAL) != 0;
  }

  boolean isEnum(String internalName) {
    Header header = header(internalName);
    return header != null && (header.access() & Opcodes.ACC_ENUM) != 0;
  }

  boolean isFinalEntityField(String owner, String name, String desc) {
    String current = owner;
    for (int depth = 0; depth < MAX_HIERARCHY_DEPTH && isEntity(current); depth++) {
      ClassNode node = body(current);
      if (node == null) {
        return false;
      }
      for (FieldNode field : node.fields) {
        if (field.name.equals(name) && field.desc.equals(desc)) {
          return (field.access & Opcodes.ACC_FINAL) != 0;
        }
      }
      current = node.superName;
    }
    return false;
  }

  MethodNode findEntityMethod(String owner, String name, String desc, String[] declaringOut) {
    String current = owner;
    for (int depth = 0; depth < MAX_HIERARCHY_DEPTH && isEntity(current); depth++) {
      ClassNode node = body(current);
      if (node == null) {
        return null;
      }
      for (MethodNode method : node.methods) {
        if (method.name.equals(name) && method.desc.equals(desc)) {
          declaringOut[0] = current;
          return method;
        }
      }
      current = node.superName;
    }
    return null;
  }
}
