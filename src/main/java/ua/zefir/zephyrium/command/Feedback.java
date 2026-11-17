package ua.zefir.zephyrium.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import ua.zefir.zephyrium.ZephyrModInit;

public final class Feedback {
  public static final int DEFAULT_SECONDS = 10;
  public static final int MAX_SECONDS = 3600;
  public static final int UNTIL_STOPPED = 0;
  public static final String SECONDS_ARGUMENT = "seconds";
  private static final String MESSAGE_PREFIX = "command." + ZephyrModInit.MOD_ID + ".";
  private static final String REPORT_DIRECTORY = "debug";
  private static final DateTimeFormatter FILE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

  public static MutableComponent message(String key, Object... arguments) {
    String translationKey = MESSAGE_PREFIX + key;
    return Component.translatableWithFallback(
        translationKey, Language.getInstance().getOrDefault(translationKey), arguments);
  }

  public static int send(CommandSourceStack source, List<Component> lines) {
    for (Component line : lines) {
      source.sendSuccess(() -> line, false);
    }
    return lines.size();
  }

  public static void reportFailed(CommandSourceStack source) {
    source.sendFailure(message("profile.report_failed").withStyle(ChatFormatting.RED));
  }

  public static Path reportPath(MinecraftServer server, String fileName, LocalDateTime time) {
    return server
        .getServerDirectory()
        .resolve(REPORT_DIRECTORY)
        .resolve(fileName.formatted(time.format(FILE_TIME)));
  }

  public static boolean writeReport(Path path, String content) {
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, content);
      return true;
    } catch (IOException exception) {
      ZephyrModInit.LOGGER.error("Could not write {}", path, exception);
      return false;
    }
  }
}
