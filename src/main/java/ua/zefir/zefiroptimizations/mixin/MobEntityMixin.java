package ua.zefir.zefiroptimizations.mixin;

import akka.actor.ActorRef;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.EntityActorMessages;
import ua.zefir.zefiroptimizations.actors.IAsyncLivingEntityAccess;
import ua.zefir.zefiroptimizations.actors.IAsyncTickingLivingEntity;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin extends LivingEntityMixin implements IAsyncTickingLivingEntity, IAsyncLivingEntityAccess {
    @Shadow
    protected abstract Vec3i getItemPickUpRangeExpander();
    @Shadow
    protected abstract void loot(ItemEntity item);
    @Shadow
    protected abstract void updateGoalControls();

    @Override
    public Vec3i zefiroptimizations$getItemPickUpRangeExpander() {
        return this.getItemPickUpRangeExpander();
    }

    @Override
    public void zefiroptimizations$loot(ItemEntity item) {
        this.loot(item);
    }
    @Override
    public void zefiroptimizations$updateGoalControls() {
        this.updateGoalControls();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(EntityType<? extends LivingEntity> entityType, World world, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        zefiroptimizations$setAsyncTicking(true);
        if (!world.isClient) {
//            ZefirOptimizations.LOGGER.info("getUuid: {}", self.getUuid());
            ZefirOptimizations.getAsyncTickManager()
                    .tell(new EntityActorMessages.EntityCreated(self), ActorRef.noSender());
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;tick()V", shift = At.Shift.AFTER), cancellable = true)
    private void onTick(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
    private void onTickMovement(CallbackInfo ci) {
        MobEntity self = (MobEntity) (Object) this;
        ZefirOptimizations.getAsyncTickManager().tell(new EntityActorMessages.TickSingleEntity(self), ActorRef.noSender());
        ci.cancel();
    }
}
