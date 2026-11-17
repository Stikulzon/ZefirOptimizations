package ua.zefir.zefiroptimizations.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigService {
  private static final String MOD_ID = "zefiroptimizations";
  private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  private static final Path FILE =
      FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
  private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
  private static final Object WRITE_LOCK = new Object();

  private static volatile ZefirConfig config = new ZefirConfig();

  public static ZefirConfig get() {
    return config;
  }

  public static void load() {
    config = read();
    write(GSON.toJson(config));
  }

  public static void onReload(Runnable listener) {
    LISTENERS.add(listener);
  }

  public static CompletableFuture<ZefirConfig> reload(Executor io, Executor apply) {
    return CompletableFuture.supplyAsync(ConfigService::read, io)
        .thenApplyAsync(
            loaded -> {
              config = loaded;
              for (Runnable listener : LISTENERS) {
                listener.run();
              }
              return loaded;
            },
            apply);
  }

  static ZefirConfig parse(String json) {
    ZefirConfig parsed = GSON.fromJson(json, ZefirConfig.class);
    ZefirConfig result = parsed == null ? new ZefirConfig() : parsed;
    result.normalize();
    return result;
  }

  private static ZefirConfig read() {
    if (!Files.isRegularFile(FILE)) {
      return new ZefirConfig();
    }
    try {
      return parse(Files.readString(FILE, StandardCharsets.UTF_8));
    } catch (IOException | JsonParseException exception) {
      LOGGER.error("Failed to read config {}, using defaults", FILE, exception);
      return new ZefirConfig();
    }
  }

  private static void write(String json) {
    synchronized (WRITE_LOCK) {
      try {
        Files.createDirectories(FILE.getParent());
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        Files.writeString(temporary, json, StandardCharsets.UTF_8);
        move(temporary);
      } catch (IOException exception) {
        LOGGER.error("Failed to write config {}", FILE, exception);
      }
    }
  }

  private static void move(Path temporary) throws IOException {
    try {
      Files.move(
          temporary, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
