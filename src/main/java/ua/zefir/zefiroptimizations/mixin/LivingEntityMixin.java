package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;

import java.util.concurrent.CompletableFuture;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.getWorld().isClient && !(self instanceof PlayerEntity)) {
            ZefirOptimizations.getActorSystem()
                    .tell(new ZefirsActorMessages.EntityRemoved(self));
        }
    }

    @Redirect(method = "tickMovement",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;tickNewAi()V"
            )
    )
    private void redirectTickNewAi(LivingEntity self) {
        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
            CompletableFuture<Void> future = new CompletableFuture<>();

            ZefirOptimizations.SERVER.executeSync(() -> {
                ((LivingEntityAccessor) this).invokeTickNewAi();
                future.complete(null);
            });

            try {
                future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if(!(self instanceof PlayerEntity)) {
            if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
                ZefirOptimizations.getActorSystem().tell(new ZefirsActorMessages.TickSingleEntity(self));
                ci.cancel();
            }
        }
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true) // Fucked up
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread() && !(self instanceof PlayerEntity)) {
            ZefirOptimizations.getMainThreadActor().tell(
                    new ZefirsActorMessages.ApplyDamage(self, source, amount)
            );
            cir.cancel();
        }
    }

//    @Inject(method = "tickCramming", at = @At("HEAD"), cancellable = true)
//    private void onTickCramming(CallbackInfo ci) {
//        LivingEntity self = (LivingEntity) (Object) this;
//        if(!(self instanceof PlayerEntity)) {
//            if (Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
//                CompletableFuture<Void> future = new CompletableFuture<>();
//
//                // This is bad
//                ZefirOptimizations.SERVER.executeSync(() -> {
//                    ((LivingEntityAccessor) this).invokeTickCramming();
//                    future.complete(null);
//                });
//
//                try {
//                    future.get();
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//
//                ci.cancel();
//            }
//        }
//    }
}