package ua.zefir.zefiroptimizations;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Objects;

public class Commands {
    public static boolean isOptimizeVillagers = false;
    public static boolean isOptimizeVillagers1 = false;
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("villagers_switch")
                    .executes(context -> switchVillagerOptimization(Objects.requireNonNull(context.getSource().getPlayer()))));
            dispatcher.register(CommandManager.literal("villagers_switch2")
                    .executes(context -> switchVillagerOptimization1(Objects.requireNonNull(context.getSource().getPlayer()))));
        });
    }

    private static int switchVillagerOptimization(ServerPlayerEntity player){
        isOptimizeVillagers = !isOptimizeVillagers;
        player.sendMessage(Text.literal("Villagers optimization is now " + isOptimizeVillagers), false);
        return 1;
    }
    private static int switchVillagerOptimization1(ServerPlayerEntity player){
        isOptimizeVillagers1 = !isOptimizeVillagers1;
        player.sendMessage(Text.literal("Villagers optimization1 is now " + isOptimizeVillagers1), false);
        return 1;
    }
}
