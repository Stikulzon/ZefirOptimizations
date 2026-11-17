package ua.zefir.zephyrium.process.ownership;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.zefir.zephyrium.config.ConfigService;
import ua.zefir.zephyrium.config.ZephyrConfig;
import ua.zefir.zephyrium.process.ownership.analysis.ClassRepo;
import ua.zefir.zephyrium.process.ownership.analysis.McNames;
import ua.zefir.zephyrium.process.ownership.analysis.Purity;
import ua.zefir.zephyrium.process.snapshot.SnapshotReads;
import ua.zefir.zephyrium.transform.Instrumenter;

public final class Ownership {
  public static final Logger LOGGER = LoggerFactory.getLogger("Zephyrium/transformer");
  private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
  private static volatile boolean installed;
  private static volatile String entityRoot;

  public static Optional<Instrumenter> instrumenter(ClassLoader loader) {
    ZephyrConfig config = ConfigService.get();
    if (!config.bytecodeTransformer) {
      LOGGER.warn("Entity transformer disabled by bytecodeTransformer=false in the config");
      return Optional.empty();
    }
    try {
      McNames names = McNames.load(loader);
      String entityDotted = names.entity.replace('/', '.');
      if (FabricLauncherBase.getLauncher().isClassLoaded(entityDotted)) {
        throw new IllegalStateException(
            entityDotted
                + " was loaded before preLaunch; another mod loads Minecraft classes too early");
      }
      ClassRepo repo = new ClassRepo(loader, names.entity);
      Purity purity = new Purity(repo, names);
      SnapshotReads.install(purity);
      return Optional.of(new EntityTransformer(repo, names, purity, config.directFieldGetters));
    } catch (Throwable t) {
      LOGGER.error(
          "Could not prepare the entity transformer; entities tick on the server thread as in vanilla",
          t);
      return Optional.empty();
    }
  }

  public static boolean isInstalled() {
    return installed && FAILED.stream().noneMatch(name -> name.equals(entityRoot));
  }

  public static Set<String> failedClasses() {
    return Collections.unmodifiableSet(FAILED);
  }

  static void markInstalled(String root) {
    entityRoot = root;
    installed = true;
    LOGGER.info("Entity transformer installed (entity root {})", root);
  }

  static void recordFailure(String internalName, Throwable t) {
    LOGGER.error(
        "Could not transform {}; its entities will tick on the server thread", internalName, t);
    FAILED.add(internalName);
  }
}
