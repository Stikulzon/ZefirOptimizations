package ua.zefir.zefiroptimizations.mixin;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import ua.zefir.zefiroptimizations.actors.IAsyncLivingEntityAccess;

@Getter
@Mixin(Entity.class)
public abstract class EntityMixin implements IAsyncLivingEntityAccess {
    @Shadow
    private Vec3d pos;
    @Shadow
    private Vec3d velocity;
    @Shadow
    private World world;
    @Shadow
    private float yaw;
    @Shadow
    private float pitch;
    @Shadow
    private boolean onGround;
    @Shadow
    private net.minecraft.util.math.Box boundingBox;
    @Shadow
    protected Vec3d movementMultiplier;
    @Mutable
    @Final
    @Shadow
    private double[] pistonMovementDelta;
    @Shadow
    private long pistonMovementTick;
    @Shadow
    private int fireTicks;
    @Shadow
    protected boolean firstUpdate;
    @Shadow
    protected boolean touchingWater;
    @Shadow
    private BlockPos blockPos;
    @Shadow
    private ChunkPos chunkPos;
    @Shadow
    private BlockState stateAtPos;
    @Final
    @Shadow
    protected static int FALL_FLYING_FLAG_INDEX;
    @Shadow
    protected boolean submergedInWater;
    @Final
    @Shadow
    private EntityType<?> type;
    @Shadow
    protected abstract boolean getFlag(int index);
    @Shadow
    protected abstract boolean canClimb(BlockState state);
    @Shadow
    protected abstract float calculateNextStepSoundDistance();
    @Shadow
    protected abstract int getBurningDuration();
    @Shadow
    private float nextStepSoundDistance;
    @Shadow
    protected abstract void tryCheckBlockCollision();
    @Shadow
    protected abstract void playExtinguishSound();
    @Shadow
    protected abstract void addAirTravelEffects();
    @Shadow
    protected abstract void playSwimSound();
    @Shadow
    protected abstract void setFlag(int index, boolean value);
    @Shadow
    protected abstract Entity.MoveEffect getMoveEffect();
    @Shadow
    protected abstract boolean hasCollidedSoftly(Vec3d adjustedMovement);
    @Shadow
    protected abstract void playStepSounds(BlockPos pos, BlockState state);
    @Shadow
    protected abstract Vec3d adjustMovementForSneaking(Vec3d movement, MovementType type);
    @Shadow
    protected abstract Vec3d adjustMovementForCollisions(Vec3d movement);
//    @Shadow
//    private static Vec3d adjustMovementForCollisions(Vec3d movement, Box entityBoundingBox, List<VoxelShape> collisions);

    @Override
    public void zefiroptimizations$setBlockPos(BlockPos pos) {
        this.blockPos = pos;
    }

    @Override
    public void zefiroptimizations$setChunkPos(ChunkPos pos) {
        this.chunkPos = pos;
    }

    @Override
    public void zefiroptimizations$setStateAtPos(BlockState state) {
        this.stateAtPos = state;
    }
    @Override
    public Vec3d zefiroptimizations$adjustMovementForCollisions(Vec3d movement) {
        return adjustMovementForCollisions(movement);
    }

    @Override
    public Vec3d zefiroptimizations$getPos() {
        return pos;
    }

    @Override
    public void zefiroptimizations$setPos(double x, double y, double z) {
        this.pos = new Vec3d(x, y, z);
    }

    @Override
    public Vec3d zefiroptimizations$getVelocity() {
        return velocity;
    }

    @Override
    public boolean zefiroptimizations$canClimb(BlockState state) {
        return canClimb(state);
    }

    @Override
    public void zefiroptimizations$setVelocity(Vec3d velocity) {
        this.velocity = velocity;
    }

    @Override
    public float zefiroptimizations$getYaw() {
        return this.yaw;
    }

    @Override
    public void zefiroptimizations$setYaw(float yaw) {
        this.yaw = yaw;
    }

    @Override
    public float zefiroptimizations$getPitch() {
        return this.pitch;
    }

    @Override
    public void zefiroptimizations$setPitch(float pitch) {
        this.pitch = pitch;
    }

    @Override
    public void zefiroptimizations$playStepSounds(BlockPos pos, BlockState state) {
        playStepSounds(pos, state);
    }

    @Override
    public Box zefiroptimizations$getBoundingBox() {
        return boundingBox;
    }

    @Override
    public void zefiroptimizations$setBoundingBox(Box boundingBox) {
        this.boundingBox = boundingBox;
    }
    @Override
    public float zefiroptimizations$getNextStepSoundDistance() {
        return nextStepSoundDistance;
    }
    @Override
    public void zefiroptimizations$setNextStepSoundDistance(float distance) {
        this.nextStepSoundDistance = distance;
    }

    @Override
    public double[] zefiroptimizations$getPistonMovementDelta() {
        return pistonMovementDelta;
    }

    @Override
    public void zefiroptimizations$setPistonMovementDelta(double[] pistonMovementDelta) {
        this.pistonMovementDelta = pistonMovementDelta;
    }

    @Override
    public long zefiroptimizations$getPistonMovementTick() {
        return pistonMovementTick;
    }

    @Override
    public void zefiroptimizations$setPistonMovementTick(long pistonMovementTick) {
        this.pistonMovementTick = pistonMovementTick;
    }

    @Override
    public int zefiroptimizations$getFireTicks() {
        return fireTicks;
    }

    @Override
    public void zefiroptimizations$setFireTicks(int fireTicks) {
        this.fireTicks = fireTicks;
    }

    @Override
    public boolean zefiroptimizations$isOnGround() {
        return this.onGround;
    }

    @Override
    public void zefiroptimizations$setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

//    @Redirect(
//            method = "move",
//            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;adjustMovementForSneaking(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/MovementType;)Lnet/minecraft/util/math/Vec3d;")
//    )
//    private Vec3d zefiroptimizations$redirectAdjustMovementForSneaking(Entity instance, Vec3d movement, MovementType type) {
////        if (instance instanceof IAsyncTickingLivingEntity) {
//            // Prevent the original method from being called when async ticking
//            return movement;
////        }
////        else {
////            return ((IAsyncLivingEntityAccess) instance).zefiroptimizations$adjustMovementForSneaking(movement, type);
////        }
//    }
}
