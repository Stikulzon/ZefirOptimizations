package ua.zefir.zefiroptimizations;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Objects;

public class Commands {
    public static boolean isOptimizeVillagers = false;
    public static boolean isOptimizeVillagers1 = false;
    public static boolean entityOptimizations1 = false;
    public static boolean entityOptimizations2 = false;
    public static boolean collisionViewOptimization = false;
    public static boolean customComputeNextOptimization = false;
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("villagers_switch")
                    .executes(context -> switchVillagerOptimization(Objects.requireNonNull(context.getSource().getPlayer()))));
            dispatcher.register(CommandManager.literal("villagers_switch2")
                    .executes(context -> switchVillagerOptimization1(Objects.requireNonNull(context.getSource().getPlayer()))));
            dispatcher.register(CommandManager.literal("entityOptimizations1")
                    .executes(context -> entityOptimizations1(Objects.requireNonNull(context.getSource().getPlayer()))));
            dispatcher.register(CommandManager.literal("entityOptimizations2")
                    .executes(context -> entityOptimizations2(Objects.requireNonNull(context.getSource().getPlayer()))));
            dispatcher.register(CommandManager.literal("collisionViewOptimization")
                    .executes(context -> collisionViewOptimization(Objects.requireNonNull(context.getSource().getPlayer()))));
            dispatcher.register(CommandManager.literal("customComputeNextOptimization")
                    .executes(context -> customComputeNextOptimization(Objects.requireNonNull(context.getSource().getPlayer()))));
        });
    }

    private static int customComputeNextOptimization(ServerPlayerEntity player){
        customComputeNextOptimization = !customComputeNextOptimization;
        player.sendMessage(Text.literal("customComputeNextOptimization is now " + customComputeNextOptimization), false);
        return 1;
    }private static int entityOptimizations1(ServerPlayerEntity player){
        entityOptimizations1 = !entityOptimizations1;
        player.sendMessage(Text.literal("entityOptimizations1 is now " + entityOptimizations1), false);
        return 1;
    }
    private static int entityOptimizations2(ServerPlayerEntity player){
        entityOptimizations2 = !entityOptimizations2;
        player.sendMessage(Text.literal("entityOptimizations2 is now " + entityOptimizations2), false);
        return 1;
    }private static int collisionViewOptimization(ServerPlayerEntity player){
        collisionViewOptimization = !collisionViewOptimization;
        player.sendMessage(Text.literal("collisionViewOptimization is now " + collisionViewOptimization), false);
        return 1;
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
