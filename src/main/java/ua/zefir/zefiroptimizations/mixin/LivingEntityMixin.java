package ua.zefir.zefiroptimizations.mixin;

import akka.actor.ActorRef;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.*;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends EntityMixin implements IAsyncTickingLivingEntity, IAsyncLivingEntityAccess {
    @Shadow
    private int jumpingCooldown;
    @Shadow
    protected boolean jumping;
    @Shadow
    private float movementSpeed;
    @Shadow
    protected int bodyTrackingIncrements;
    @Shadow
    protected double serverX;
    @Shadow
    protected double serverY;
    @Shadow
    protected double serverZ;
    @Shadow
    protected double serverYaw;
    @Shadow
    protected double serverPitch;
    @Shadow
    protected int headTrackingIncrements;
    @Shadow
    protected double serverHeadYaw;
    @Shadow
    protected float lookDirection;
    @Shadow
    protected float prevLookDirection;
    @Shadow
    protected float stepBobbingAmount;
    @Shadow
    protected float prevStepBobbingAmount;
    @Shadow
    protected int fallFlyingTicks;
    @Shadow
    protected ItemStack activeItemStack;
    @Shadow
    protected int itemUseTimeLeft;
    @Shadow
    protected int riptideTicks;
    @Shadow
    protected static final int USING_RIPTIDE_FLAG = 4;
    @Shadow
    protected float riptideAttackDamage;
    @Shadow
    protected ItemStack riptideStack;

    @Shadow
    protected abstract SoundEvent getFallSound(int distance);
    @Shadow
    protected abstract float getJumpVelocity();
    @Shadow
    protected abstract boolean shouldSwimInFluids();
    @Shadow
    protected abstract void setLivingFlag(int mask, boolean value);
    @Shadow
    protected abstract Vec3d getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput);
    @Shadow
    protected abstract float getSaddledSpeed(PlayerEntity controllingPlayer);
    @Shadow
    protected abstract void attackLivingEntity(LivingEntity target);
    @Shadow
    protected abstract void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition);
    @Shadow
    protected abstract float getVelocityMultiplier();
    @Shadow
    protected abstract void tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput);
    @Shadow
    protected abstract void tickNewAi();
    @Shadow
    protected abstract float getBaseMovementSpeedMultiplier();
    @Shadow
    protected abstract void pushAway(Entity entity);
    @Shadow
    protected abstract void removePowderSnowSlow();
    @Shadow
    protected abstract void addPowderSnowSlowIfNeeded();
    @Shadow
    protected abstract boolean isImmobile();
    @Shadow
    protected abstract void tickCramming();
    @Shadow
    protected abstract void tickRiptide(Box a, Box b);

    @Override
    public boolean zefiroptimizations$isImmobile() {
        return this.isImmobile();
    }

    @Override
    public void zefiroptimizations$tickCramming() {
        this.tickCramming();
    }

//    @Override
//    public void zefiroptimizations$tickRiptide(Box a, Box b) {
//        this.tickRiptide(a, b);
//    }

    @Override
    public void zefiroptimizations$removePowderSnowSlow() {
        this.removePowderSnowSlow();
    }

    @Override
    public void zefiroptimizations$addPowderSnowSlowIfNeeded() {
        this.addPowderSnowSlowIfNeeded();
    }

    @Override
    public boolean zefiroptimizations$shouldSwimInFluids() {
        return this.shouldSwimInFluids();
    }

            @Override
    public boolean zefiroptimizations$isTouchingWater() {
                return touchingWater;
            }

            @Override
    public SoundEvent zefiroptimizations$getFallSound(int distance) {
                return this.getFallSound(distance);
            }

            @Override
    public float zefiroptimizations$getJumpVelocity() {
                return this.getJumpVelocity();
            }

    @Override
    public float zefiroptimizations$getBaseMovementSpeedMultiplier() {
        return getBaseMovementSpeedMultiplier();
    }

    @Override
    public Vec3d zefiroptimizations$getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput) {
        return this.getControlledMovementInput(controllingPlayer, movementInput);
    }

    @Override
    public int zefiroptimizations$getBurningDuration() {
        return getBurningDuration();
    }

    @Override
    public void zefiroptimizations$setLivingFlag(int mask, boolean value) {
        this.setLivingFlag(mask, value);
    }

    @Override
    public float zefiroptimizations$getSaddledSpeed(PlayerEntity controllingPlayer) {
        return this.getSaddledSpeed(controllingPlayer);
    }

    @Override
    public void zefiroptimizations$attackLivingEntity(LivingEntity target) {
        this.attackLivingEntity(target);
    }

    @Override
    public void zefiroptimizations$fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
        this.fall(heightDifference, onGround, state, landedPosition);
    }

    @Override
    public float zefiroptimizations$getVelocityMultiplier() {
        return this.getVelocityMultiplier();
    }

    @Override
    public void zefiroptimizations$tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
        this.tickControlled(controllingPlayer, movementInput);
    }

    @Override
    public void zefiroptimizations$tickNewAi() {
        this.tickNewAi();
    }

    @Override
    public boolean zefiroptimizations$hasCollidedSoftly(Vec3d adjustedMovement) {
        return this.hasCollidedSoftly(adjustedMovement);
    }

    @Override
    public void zefiroptimizations$pushAway(Entity entity) {
        this.pushAway(entity);
    }


    @Override
    public int zefiroptimizations$getJumpingCooldown() {
        return jumpingCooldown;
    }

    @Override
    public int zefiroptimizations$getRiptideTicks() {
        return riptideTicks;
    }

    @Override
    public void zefiroptimizations$setRiptideTicks(int ticks) {
        riptideTicks = ticks;
    }

    @Override
    public void zefiroptimizations$setRiptideAttackDamage(float damage) {
        riptideAttackDamage = damage;
    }

    @Override
    public float zefiroptimizations$getRiptideAttackDamage() {
        return riptideAttackDamage;
    }

    @Override
    public void zefiroptimizations$setRiptideStack(ItemStack stack) {
        riptideStack = stack;
    }

    @Override
    public ItemStack zefiroptimizations$getRiptideStack() {
        return riptideStack;
    }

    @Override
    public void zefiroptimizations$setJumpingCooldown(int jumpingCooldown) {
        this.jumpingCooldown = jumpingCooldown;
    }

    @Override
    public boolean zefiroptimizations$isJumping() {
        return jumping;
    }

    @Override
    public void zefiroptimizations$setJumping(boolean jumping) {
        this.jumping = jumping;
    }

    @Override
    public float zefiroptimizations$getMovementSpeed() {
        return movementSpeed;
    }

    @Override
    public void zefiroptimizations$setMovementSpeed(float movementSpeed) {
        this.movementSpeed = movementSpeed;
    }

    @Override
    public int zefiroptimizations$getBodyTrackingIncrements() {
        return bodyTrackingIncrements;
    }

    @Override
    public void zefiroptimizations$setBodyTrackingIncrements(int bodyTrackingIncrements) {
        this.bodyTrackingIncrements = bodyTrackingIncrements;
    }

    @Override
    public double zefiroptimizations$getServerX() {
        return serverX;
    }

    @Override
    public void zefiroptimizations$setServerX(double serverX) {
        this.serverX = serverX;
    }

    @Override
    public double zefiroptimizations$getServerY() {
        return serverY;
    }

    @Override
    public void zefiroptimizations$setServerY(double serverY) {
        this.serverY = serverY;
    }

    @Override
    public double zefiroptimizations$getServerZ() {
        return serverZ;
    }

    @Override
    public void zefiroptimizations$setServerZ(double serverZ) {
        this.serverZ = serverZ;
    }

    @Override
    public double zefiroptimizations$getServerYaw() {
        return serverYaw;
    }

    @Override
    public void zefiroptimizations$setServerYaw(double serverYaw) {
        this.serverYaw = serverYaw;
    }

    @Override
    public double zefiroptimizations$getServerPitch() {
        return serverPitch;
    }

    @Override
    public void zefiroptimizations$setServerPitch(double serverPitch) {
        this.serverPitch = serverPitch;
    }

    @Override
    public int zefiroptimizations$getHeadTrackingIncrements() {
        return headTrackingIncrements;
    }

    @Override
    public void zefiroptimizations$setHeadTrackingIncrements(int headTrackingIncrements) {
        this.headTrackingIncrements = headTrackingIncrements;
    }

    @Override
    public double zefiroptimizations$getServerHeadYaw() {
        return serverHeadYaw;
    }

    @Override
    public void zefiroptimizations$setServerHeadYaw(double serverHeadYaw) {
        this.serverHeadYaw = serverHeadYaw;
    }

    @Override
    public float zefiroptimizations$getLookDirection() {
        return lookDirection;
    }

    @Override
    public void zefiroptimizations$setLookDirection(float lookDirection) {
        this.lookDirection = lookDirection;
    }

    @Override
    public float zefiroptimizations$getPrevLookDirection() {
        return prevLookDirection;
    }

    @Override
    public void zefiroptimizations$setPrevLookDirection(float prevLookDirection) {
        this.prevLookDirection = prevLookDirection;
    }

    @Override
    public float zefiroptimizations$getStepBobbingAmount() {
        return stepBobbingAmount;
    }

    @Override
    public void zefiroptimizations$setStepBobbingAmount(float stepBobbingAmount) {
        this.stepBobbingAmount = stepBobbingAmount;
    }

    @Override
    public float zefiroptimizations$getPrevStepBobbingAmount() {
        return prevStepBobbingAmount;
    }

    @Override
    public void zefiroptimizations$setPrevStepBobbingAmount(float prevStepBobbingAmount) {
        this.prevStepBobbingAmount = prevStepBobbingAmount;
    }

    @Override
    public int zefiroptimizations$getFallFlyingTicks() {
        return fallFlyingTicks;
    }

    @Override
    public void zefiroptimizations$setFallFlyingTicks(int fallFlyingTicks) {
        this.fallFlyingTicks = fallFlyingTicks;
    }

    @Override
    public void zefiroptimizations$playExtinguishSound() {
        this.playExtinguishSound();
    }

    @Override
    public void zefiroptimizations$addAirTravelEffects() {
        this.addAirTravelEffects();
    }

    @Override
    public void zefiroptimizations$playSwimSound() {
        this.playSwimSound();
    }

    @Override
    public float zefiroptimizations$calculateNextStepSoundDistance() {
        return this.calculateNextStepSoundDistance();
    }


    @Override
    public void zefiroptimizations$setFlag(int index, boolean value) {
        setFlag(index, value);
    }

    @Override
    public int zefiroptimizations$FALL_FLYING_FLAG_INDEX() {
        return FALL_FLYING_FLAG_INDEX;
    }

    @Override
    public int zefiroptimizations$USING_RIPTIDE_FLAG() {
        return USING_RIPTIDE_FLAG;
    }

    @Override
    public Vec3d zefiroptimizations$adjustMovementForSneaking(Vec3d movement, MovementType type) {
        return adjustMovementForSneaking(movement, type);
    }

    @Override
    public Entity.MoveEffect zefiroptimizations$getMoveEffect() {
        return getMoveEffect();
    }

    @Override
    public void zefiroptimizations$setVelocity(double x, double y, double z) {
        this.zefiroptimizations$setVelocity(new Vec3d(x, y, z));
    }


    @Override
    public Vec3d zefiroptimizations$getMovementMultiplier() {
        return movementMultiplier;
    }

    @Override
    public void zefiroptimizations$setMovementMultiplier(Vec3d movementMultiplier) {
        this.movementMultiplier = movementMultiplier;
    }

//    @Override
//    public void zefiroptimizations$setRemoved(Entity.RemovalReason reason) {
//        this.setRemoved(reason);
//    }

    @Override
    public boolean zefiroptimizations$getFlag(int index) {
        return this.getFlag(index);
    }

    @Override
    public void zefiroptimizations$tryCheckBlockCollision() {
        this.tryCheckBlockCollision();
    }

    // ------------------------------------------------------------------

    @Unique
    private boolean isAsyncTicking = true;

    @Unique
    @Override
    public boolean zefiroptimizations$isAsyncTicking() {
        return isAsyncTicking;
    }

    @Unique
    @Override
    public void zefiroptimizations$setAsyncTicking(boolean asyncTicking) {
        isAsyncTicking = asyncTicking;
    }

//    @Inject(method = "<init>", at = @At("RETURN"))
//    private void init(EntityType<? extends LivingEntity> entityType, World world, CallbackInfo ci) {
//        LivingEntity self = (LivingEntity) (Object) this;
//        if(self instanceof ArmorStandEntity) {
//            zefiroptimizations$setAsyncTicking(true);
//            if (!world.isClient) {
//                ZefirOptimizations.getAsyncTickManager()
//                        .tell(new EntityActorMessages.EntityCreated(self), ActorRef.noSender());
//            }
//        }
//    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.getWorld().isClient) {
            ZefirOptimizations.getAsyncTickManager()
                    .tell(new ZefirsActorMessages.EntityRemoved(self), ActorRef.noSender());
        }
    }

    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
    private void onTickMovement(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ArmorStandEntity) {
            ZefirOptimizations.getAsyncTickManager().tell(new ZefirsActorMessages.TickSingleEntity(self), ActorRef.noSender());
            ci.cancel();
//        } else if (self instanceof MobEntity) {
//            ZefirOptimizations.getAsyncTickManager().tell(new EntityActorMessages.TickSingleEntity(self), ActorRef.noSender());
//            ci.cancel();
        }
    }
}