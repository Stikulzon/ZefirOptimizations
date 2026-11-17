package ua.zefir.zephyrium.process.sharedstate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

final class ModCode {
  private static final String CLASS_SUFFIX = ".class";
  private static final char SEPARATOR = '/';
  private static final char BACKSLASH = '\\';
  private static final String OWN_MOD_ID = "zephyrium";
  private static final String BUILTIN = "builtin";

  private final Map<String, String> packages;

  ModCode(Map<String, String> packages) {
    this.packages = Map.copyOf(packages);
  }

  static ModCode scan() {
    Map<String, String> packages = new HashMap<>();
    for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
      if (isPlatformOrOwn(mod)) {
        continue;
      }
      String id = mod.getMetadata().getId();
      for (Path root : mod.getRootPaths()) {
        collectPackages(root, id, packages);
      }
    }
    return new ModCode(packages);
  }

  private static boolean isPlatformOrOwn(ModContainer mod) {
    if (mod.getMetadata().getType().equals(BUILTIN)) {
      return true;
    }
    for (ModContainer current = mod; current != null; ) {
      if (current.getMetadata().getId().equals(OWN_MOD_ID)) {
        return true;
      }
      current = current.getContainingMod().orElse(null);
    }
    return false;
  }

  private static void collectPackages(Path root, String id, Map<String, String> packages) {
    try (Stream<Path> files = Files.walk(root)) {
      files
          .filter(file -> file.getFileName() != null)
          .filter(file -> file.getFileName().toString().endsWith(CLASS_SUFFIX))
          .map(file -> root.relativize(file).getParent())
          .map(parent -> parent == null ? "" : parent.toString().replace(BACKSLASH, SEPARATOR))
          .forEach(pkg -> packages.putIfAbsent(pkg, id));
    } catch (IOException | RuntimeException e) {
      SharedState.LOGGER.error(
          "Could not list the classes of {} in {}; its code is not routed", id, root, e);
    }
  }

  String modOf(String internalName) {
    int slash = internalName.lastIndexOf(SEPARATOR);
    return packages.get(slash < 0 ? "" : internalName.substring(0, slash));
  }

  int packageCount() {
    return packages.size();
  }

  long modCount() {
    return packages.values().stream().distinct().count();
  }
}
