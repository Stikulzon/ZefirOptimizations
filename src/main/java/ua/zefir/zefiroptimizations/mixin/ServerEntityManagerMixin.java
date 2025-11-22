package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

@Mixin(ServerEntityManager.class)
public abstract class ServerEntityManagerMixin<T extends EntityLike>{
    @Inject(method = "getLookup", at = @At("HEAD"))
    private void onGetLookup(CallbackInfoReturnable<EntityLookup<T>> cir) {
        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
            throw new RuntimeException("Unauthorized EntityLookup access. You need to request it from the ServerEntityManager actor.");
        }
    }
}
