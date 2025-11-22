package ua.zefir.zefiroptimizations.mixin;

import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;

@Getter
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("TAIL"))
    private void init(EntityType<? extends LivingEntity> entityType, World world, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!world.isClient) {
            ZefirOptimizations.getActorSystem()
                    .tell(new ZefirsActorMessages.EntityCreated(self));
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
            throw new RuntimeException("Entity ticking on main thread!");
        }
    }
}
