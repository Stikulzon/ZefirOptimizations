package ua.zefir.zefiroptimizations.command;

import static ua.zefir.zefiroptimizations.command.Feedback.message;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import de.maxhenkel.admiral.MinecraftAdmiral;
import de.maxhenkel.admiral.annotations.Command;
import de.maxhenkel.admiral.annotations.RequiresPermissionLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.Util;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.config.ConfigService;

@Command(ZefirCommand.ROOT)
@RequiresPermissionLevel(ZefirCommand.PERMISSION_LEVEL)
public final class ZefirCommand {
  public static final String ROOT = "zo";
  public static final int PERMISSION_LEVEL = 3;

  public static void register(
      CommandDispatcher<CommandSourceStack> dispatcher,
      CommandBuildContext context,
      Class<?>... subcommands) {
    MinecraftAdmiral.builder(dispatcher, context)
        .addCommandClasses(ZefirCommand.class)
        .addCommandClasses(subcommands)
        .build();
  }

  @Command("reload")
  public void reload(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    ConfigService.reload(Util.ioPool(), source.getServer())
        .whenComplete(
            (config, throwable) -> {
              if (throwable != null) {
                ZefirOptimizations.LOGGER.error("Failed to reload the config", throwable);
                source.sendFailure(message("reload.failed"));
                return;
              }
              source.sendSuccess(() -> message("reload.done"), true);
            });
  }
}
