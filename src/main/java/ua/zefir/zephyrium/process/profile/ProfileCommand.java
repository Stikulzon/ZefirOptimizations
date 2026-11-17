package ua.zefir.zephyrium.process.profile;

import static ua.zefir.zephyrium.command.Feedback.message;

import com.mojang.brigadier.context.CommandContext;
import de.maxhenkel.admiral.annotations.Command;
import de.maxhenkel.admiral.annotations.MinMax;
import de.maxhenkel.admiral.annotations.Name;
import de.maxhenkel.admiral.annotations.OptionalArgument;
import de.maxhenkel.admiral.annotations.RequiresPermissionLevel;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import ua.zefir.zephyrium.ZephyrModInit;
import ua.zefir.zephyrium.command.Feedback;
import ua.zefir.zephyrium.command.ZephyrCommand;
import ua.zefir.zephyrium.process.Processes;

@Command(ZephyrCommand.ROOT)
@RequiresPermissionLevel(ZephyrCommand.PERMISSION_LEVEL)
public final class ProfileCommand {
  private static final String PROFILE = "profile";
  private static final String TARGET_ARGUMENT = "target";
  private static final String MIN_SECONDS = "" + Feedback.UNTIL_STOPPED;
  private static final String MAX_SECONDS = "" + Feedback.MAX_SECONDS;
  private static final String TEXT_REPORT = "zefir-entity-profile-%s.txt";
  private static final String JSON_REPORT = "zefir-entity-profile-%s.json";

  private static CommandSourceStack reportTo;

  public static void onServerTick(MinecraftServer server) {
    if (!Profiler.isDue()) {
      return;
    }
    finish(server, reportTo);
  }

  @Command({PROFILE, "start"})
  public int start(
      CommandContext<CommandSourceStack> context,
      @Name(Feedback.SECONDS_ARGUMENT)
          @MinMax(min = MIN_SECONDS, max = MAX_SECONDS)
          @OptionalArgument
          Integer requestedSeconds) {
    int seconds = requestedSeconds == null ? Feedback.DEFAULT_SECONDS : requestedSeconds;
    CommandSourceStack source = context.getSource();
    if (Profiler.isActive()) {
      source.sendFailure(message("profile.already_running", Profiler.elapsedSeconds()));
      return 0;
    }
    if (Processes.liveProcesses() == 0) {
      source.sendSuccess(
          () -> message("profile.no_processes").withStyle(ChatFormatting.YELLOW), false);
    }
    reportTo = source;
    Profiler.start(seconds);
    return Feedback.send(source, ProfileChat.started(seconds));
  }

  @Command({PROFILE, "stop"})
  public int stop(CommandContext<CommandSourceStack> context) {
    if (!Profiler.isActive()) {
      context.getSource().sendFailure(message("profile.not_running"));
      return 0;
    }
    finish(context.getSource().getServer(), context.getSource());
    return 1;
  }

  @Command({PROFILE, "status"})
  public int status(CommandContext<CommandSourceStack> context) {
    return Feedback.send(context.getSource(), ProfileChat.status(ProcessStatus.read()));
  }

  @Command({PROFILE, "entity"})
  public int entity(
      CommandContext<CommandSourceStack> context, @Name(TARGET_ARGUMENT) Entity target) {
    return Feedback.send(context.getSource(), ProfileChat.entity(EntityStatus.of(target)));
  }

  private static void finish(MinecraftServer server, CommandSourceStack source) {
    ProfileResult result = Profiler.stop();
    reportTo = null;
    List<String> text = ReportText.render(result);
    ZephyrModInit.LOGGER.info("Entity profile:\n{}", String.join("\n", text));
    LocalDateTime now = LocalDateTime.now();
    Path textPath = Feedback.reportPath(server, TEXT_REPORT, now);
    Path jsonPath = Feedback.reportPath(server, JSON_REPORT, now);
    CompletableFuture<Boolean> written =
        CompletableFuture.supplyAsync(
            () ->
                Feedback.writeReport(textPath, String.join(System.lineSeparator(), text))
                    & Feedback.writeReport(jsonPath, ReportJson.render(result)),
            Util.ioPool());
    if (source == null) {
      return;
    }
    Feedback.send(source, ProfileChat.summary(result, textPath.toAbsolutePath().toString()));
    written.thenAcceptAsync(
        ok -> {
          if (!ok) {
            Feedback.reportFailed(source);
          }
        },
        server);
  }
}
