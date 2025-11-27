package ua.zefir.zefiroptimizations.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
//    @WrapOperation(
//            method = "createWorlds",
//            at = @At(value = "INVOKE", target = "")
//    )
//    private void redirectTickNewAi() {
//    }
}
