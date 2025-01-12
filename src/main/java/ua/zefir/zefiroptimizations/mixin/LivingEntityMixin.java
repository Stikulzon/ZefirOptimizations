package ua.zefir.zefiroptimizations.mixin;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.*;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements IAsyncTickingLivingEntity {
    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.getWorld().isClient && !(self instanceof PlayerEntity)) {
            ZefirOptimizations.getAsyncTickManager()
                    .tell(new ZefirsActorMessages.EntityRemoved(self), ActorRef.noSender());
        }
    }
//
//    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
//    private void onTickMovement(CallbackInfo ci) {
//        LivingEntity self = (LivingEntity) (Object) this;
//        if (self instanceof ArmorStandEntity) {
//            ZefirOptimizations.getAsyncTickManager().tell(new ZefirsActorMessages.TickSingleEntity(self), ActorRef.noSender());
//            ci.cancel();
////        } else if (self instanceof MobEntity) {
////            ZefirOptimizations.getAsyncTickManager().tell(new EntityActorMessages.TickSingleEntity(self), ActorRef.noSender());
////            ci.cancel();
//        }
//    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if(!(self instanceof PlayerEntity)) {
            if (Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
                ZefirOptimizations.getAsyncTickManager().tell(new ZefirsActorMessages.TickSingleEntity(self), ActorRef.noSender());
                ci.cancel();
            } else if (Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {

            } else {
                System.out.println("Weired behaviour in ticking loop, thread: " + Thread.currentThread() + ", is it server thread: " + (Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) + ", main thread: " + ZefirOptimizations.SERVER.getThread());
                ci.cancel();
            }
//            System.out.println("Ticking");
        }
//        } else if (self instanceof MobEntity) {
//            ZefirOptimizations.getAsyncTickManager().tell(new EntityActorMessages.TickSingleEntity(self), ActorRef.noSender());
//            ci.cancel();
    }

//    @Inject(method = "baseTick", at = @At("HEAD"), cancellable = true)
//    private void onBaseTick(CallbackInfo ci) {
//        LivingEntity self = (LivingEntity) (Object) this;
////        System.out.println("BaseTicking");
//        if(Thread.currentThread() == ZefirOptimizations.SERVER.getThread() && !(self instanceof PlayerEntity)) {
//            ZefirOptimizations.getAsyncTickManager().tell(new ZefirsActorMessages.BaseTickSingleEntity(self), ActorRef.noSender());
//            ci.cancel();
//        }
////        } else if (self instanceof MobEntity) {
////            ZefirOptimizations.getAsyncTickManager().tell(new EntityActorMessages.TickSingleEntity(self), ActorRef.noSender());
////            ci.cancel();
//    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true) // Fucked up
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread() && !(self instanceof PlayerEntity)) {
            ZefirOptimizations.getMainThreadActor().tell(
                    new ZefirsActorMessages.ApplyDamage(self, source, amount),
                    ActorRef.noSender()
            );
            cir.cancel();
        }
    }

    @Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
    private void onTickNewAi(CallbackInfo ci) {
//        ZefirOptimizations.LOGGER.info("Ticking new AI!");
        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
            ZefirOptimizations.LOGGER.info("Ticking new AI");
//            ZefirOptimizations.getMainThreadActor().tell(
//                    new ZefirsActorMessages.TickNewAiAndContinue(ActorRef.noSender(), (LivingEntity) (Object) this),
//                    ActorRef.noSender()
//            );
            // This is bad
            ZefirOptimizations.SERVER.execute(() -> {
                        ((LivingEntityAccessor) this).invokeTickNewAi();
            });
            ci.cancel();
        }
    }
//
//    @Redirect(method = "tickMovement", at = @At(
//            value = "INVOKE",
//            target = "Lnet/minecraft/entity/LivingEntity;tickNewAi()V"
//    ))
//    private void onTickMovement(LivingEntity instance) {
//        LivingEntity self = (LivingEntity) (Object) this;
//        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread() && !(self instanceof PlayerEntity)) {
//            // This is bad
//            ZefirOptimizations.SERVER.execute(() -> {
//                        ((LivingEntityAccessor) this).invokeTickNewAi();
//            });
//        }
//    }

    @Inject(method = "tickCramming", at = @At("HEAD"), cancellable = true)
    private void onTravel(CallbackInfo ci) { // TODO: Remove this
        LivingEntity self = (LivingEntity) (Object) this;
        if(!(self instanceof PlayerEntity)) {
            if (Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
                // This is bad
                ZefirOptimizations.SERVER.execute(() -> {
                    ((LivingEntityAccessor) this).invokeTickCramming();
                });
                ci.cancel();
            }
        }
    }
}