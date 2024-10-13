package ua.zefir.zefiroptimizations.actors;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.*;

public interface IAsyncLivingEntityAccess {
    Vec3d zefiroptimizations$getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput);

    float zefiroptimizations$getSaddledSpeed(PlayerEntity controllingPlayer);

    int zefiroptimizations$getBurningDuration();

    boolean zefiroptimizations$shouldSwimInFluids();

    Vec3i zefiroptimizations$getItemPickUpRangeExpander();

    void zefiroptimizations$loot(ItemEntity item);

    boolean zefiroptimizations$isTouchingWater();

    void zefiroptimizations$updateGoalControls();

    SoundEvent zefiroptimizations$getFallSound(int distance);

    float zefiroptimizations$getJumpVelocity();

    float zefiroptimizations$getBaseMovementSpeedMultiplier();

    int zefiroptimizations$USING_RIPTIDE_FLAG();

    void zefiroptimizations$tickCramming();

    void zefiroptimizations$setBlockPos(BlockPos pos);

    void zefiroptimizations$playStepSounds(BlockPos pos, BlockState state);

    Vec3d zefiroptimizations$adjustMovementForCollisions(Vec3d movement);

    void zefiroptimizations$setChunkPos(ChunkPos pos);

    void zefiroptimizations$setStateAtPos(BlockState state);

    void zefiroptimizations$attackLivingEntity(LivingEntity target);

    void zefiroptimizations$setLivingFlag(int mask, boolean value);

    void zefiroptimizations$fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition);

    float zefiroptimizations$getVelocityMultiplier();

    void zefiroptimizations$tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput);

    void zefiroptimizations$tickNewAi();

    boolean zefiroptimizations$hasCollidedSoftly(Vec3d adjustedMovement);

    void zefiroptimizations$pushAway(Entity entity);

    int zefiroptimizations$getJumpingCooldown();

    void zefiroptimizations$setJumpingCooldown(int jumpingCooldown);

    boolean zefiroptimizations$isJumping();

    void zefiroptimizations$setJumping(boolean jumping);

    float zefiroptimizations$getMovementSpeed();

    void zefiroptimizations$setMovementSpeed(float movementSpeed);

    int zefiroptimizations$getBodyTrackingIncrements();

    void zefiroptimizations$setBodyTrackingIncrements(int bodyTrackingIncrements);

    double zefiroptimizations$getServerX();

    void zefiroptimizations$setServerX(double serverX);

    double zefiroptimizations$getServerY();

    void zefiroptimizations$setServerY(double serverY);

    double zefiroptimizations$getServerZ();

    void zefiroptimizations$setServerZ(double serverZ);

    double zefiroptimizations$getServerYaw();

    void zefiroptimizations$setServerYaw(double serverYaw);

    double zefiroptimizations$getServerPitch();

    void zefiroptimizations$setServerPitch(double serverPitch);

    int zefiroptimizations$getHeadTrackingIncrements();

    void zefiroptimizations$setHeadTrackingIncrements(int headTrackingIncrements);

    double zefiroptimizations$getServerHeadYaw();

    void zefiroptimizations$setServerHeadYaw(double serverHeadYaw);

    float zefiroptimizations$getLookDirection();

    void zefiroptimizations$setLookDirection(float lookDirection);

    float zefiroptimizations$getPrevLookDirection();

    void zefiroptimizations$setPrevLookDirection(float prevLookDirection);

    float zefiroptimizations$getStepBobbingAmount();

    void zefiroptimizations$setStepBobbingAmount(float stepBobbingAmount);

    float zefiroptimizations$getPrevStepBobbingAmount();

    void zefiroptimizations$setPrevStepBobbingAmount(float prevStepBobbingAmount);

    int zefiroptimizations$getFallFlyingTicks();

    void zefiroptimizations$setFallFlyingTicks(int fallFlyingTicks);

    void zefiroptimizations$playExtinguishSound();

    int zefiroptimizations$getRiptideTicks();

    float zefiroptimizations$getRiptideAttackDamage();

    void zefiroptimizations$setRiptideTicks(int ticks);

    void zefiroptimizations$setRiptideAttackDamage(float damage);

    void zefiroptimizations$setRiptideStack(ItemStack stack);

    void zefiroptimizations$addAirTravelEffects();

    void zefiroptimizations$playSwimSound();

    float zefiroptimizations$calculateNextStepSoundDistance();

    void zefiroptimizations$removePowderSnowSlow();

    void zefiroptimizations$addPowderSnowSlowIfNeeded();

    boolean zefiroptimizations$isImmobile();

    Vec3d zefiroptimizations$getPos();

    void zefiroptimizations$setPos(double x, double y, double z);

    Vec3d zefiroptimizations$getVelocity();

    void zefiroptimizations$setFlag(int index, boolean value);

    int zefiroptimizations$FALL_FLYING_FLAG_INDEX();

    Vec3d zefiroptimizations$adjustMovementForSneaking(Vec3d movement, MovementType type);

    void zefiroptimizations$setVelocity(Vec3d velocity);

    float zefiroptimizations$getNextStepSoundDistance();

    void zefiroptimizations$setNextStepSoundDistance(float distance);

    void zefiroptimizations$setVelocity(double x, double y, double z);

    Entity.MoveEffect zefiroptimizations$getMoveEffect();

    boolean zefiroptimizations$canClimb(BlockState state);

    void zefiroptimizations$setPitch(float pitch);

    float zefiroptimizations$getYaw();

    void zefiroptimizations$setYaw(float yaw);

    float zefiroptimizations$getPitch();

    boolean zefiroptimizations$isOnGround();

    void zefiroptimizations$setOnGround(boolean onGround);

    net.minecraft.util.math.Box zefiroptimizations$getBoundingBox();

    void zefiroptimizations$setBoundingBox(Box boundingBox);

    Vec3d zefiroptimizations$getMovementMultiplier();

    void zefiroptimizations$setMovementMultiplier(Vec3d movementMultiplier);

    double[] zefiroptimizations$getPistonMovementDelta();

    void zefiroptimizations$setPistonMovementDelta(double[] pistonMovementDelta);

    long zefiroptimizations$getPistonMovementTick();

    void zefiroptimizations$setPistonMovementTick(long pistonMovementTick);

    boolean zefiroptimizations$getFlag(int index);

    int zefiroptimizations$getFireTicks();

    void zefiroptimizations$setFireTicks(int fireTicks);

    void zefiroptimizations$tryCheckBlockCollision();
}