package ua.zefir.zefiroptimizations.process.sharedstate;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

final class AddedFields {
  private static final String[] GAME_PREFIXES = {"net/minecraft/", "com/mojang/"};
  private static final int MAX_HIERARCHY = 32;

  private final ClassLoader loader;
  private final Predicate<String> isEntity;
  private final Set<String> entityOwned;
  private final Map<String, Optional<Shape>> shapes = new ConcurrentHashMap<>();

  AddedFields(ClassLoader loader, Predicate<String> isEntity, Set<String> entityOwned) {
    this.loader = loader;
    this.isEntity = isEntity;
    this.entityOwned = entityOwned;
  }

  static boolean isGameClass(String internalName) {
    for (String prefix : GAME_PREFIXES) {
      if (internalName.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  boolean onSharedGameObject(String owner, String name) {
    if (!isGameClass(owner) || isEntity.test(owner)) {
      return false;
    }
    String current = owner;
    for (int i = 0; i < MAX_HIERARCHY && current != null && isGameClass(current); i++) {
      if (entityOwned.contains(current)) {
        return false;
      }
      Shape shape = shapes.computeIfAbsent(current, this::read).orElse(null);
      if (shape == null) {
        return false;
      }
      if (shape.fields.contains(name) || ownedInterface(shape)) {
        return false;
      }
      current = shape.superName;
    }
    return true;
  }

  private boolean ownedInterface(Shape shape) {
    for (String type : shape.interfaces) {
      if (entityOwned.contains(type)) {
        return true;
      }
    }
    return false;
  }

  private Optional<Shape> read(String internalName) {
    try (InputStream in = loader.getResourceAsStream(internalName + ".class")) {
      if (in == null) {
        return Optional.empty();
      }
      ClassNode node = new ClassNode();
      new ClassReader(in)
          .accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      Set<String> fields = new HashSet<>();
      for (FieldNode field : node.fields) {
        fields.add(field.name);
      }
      return Optional.of(
          new Shape(node.superName, List.copyOf(node.interfaces), Set.copyOf(fields)));
    } catch (Throwable t) {
      SharedState.LOGGER.error("Could not read the vanilla fields of {}", internalName, t);
      return Optional.empty();
    }
  }

  private record Shape(String superName, List<String> interfaces, Set<String> fields) {}
}
