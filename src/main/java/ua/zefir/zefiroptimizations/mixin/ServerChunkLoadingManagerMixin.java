package ua.zefir.zefiroptimizations.mixin;


import akka.actor.ActorRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.EntityActorMessages;

@Mixin(ServerChunkLoadingManager.class)
public class ServerChunkLoadingManagerMixin {
    @Inject(method = "unloadEntity", at = @At("HEAD"))
    private void onEntityUnload(Entity entity, CallbackInfo ci){
        if(entity instanceof LivingEntity livingEntity) {
            ZefirOptimizations.getAsyncTickManager()
                    .tell(new EntityActorMessages.EntityRemoved(livingEntity), ActorRef.noSender());
        }
    }
}
