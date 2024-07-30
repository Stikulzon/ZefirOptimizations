package ua.zefir.zefiroptimizations.mixin;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.floats.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.event.GameEvent;
import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

import static ua.zefir.zefiroptimizations.Commands.*;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    protected Vec3d movementMultiplier;
    @Shadow
    private int fireTicks;
    @Shadow
    private float nextStepSoundDistance;
    @Shadow
    protected abstract int getBurningDuration();
    @Shadow
    protected abstract float calculateNextStepSoundDistance();
    @Shadow
    protected abstract Vec3d adjustMovementForPiston(Vec3d movement);
    @Shadow
    protected abstract Vec3d adjustMovementForSneaking(Vec3d movement, MovementType type);
    @Shadow
    protected abstract boolean hasCollidedSoftly(Vec3d adjustedMovement);
    @Shadow
    protected abstract boolean canClimb(BlockState state);
    @Shadow
    protected abstract boolean stepOnBlock(BlockPos pos, BlockState state, boolean playSound, boolean emitEvent, Vec3d movement);
    @Shadow
    protected abstract Entity.MoveEffect getMoveEffect();
    @Shadow
    protected abstract float getVelocityMultiplier();
    @Shadow
    protected abstract void playSwimSound();
    @Shadow
    protected abstract void playExtinguishSound();
    @Shadow
    protected abstract void tryCheckBlockCollision();
    @Shadow
    protected abstract void addAirTravelEffects();
    @Shadow
    protected abstract void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition);

    @Inject(method = "move", at = @At(value = "HEAD"), cancellable = true)
    private void move(MovementType movementType, Vec3d movement, CallbackInfo ci){
        if(entityOptimizations1) {
            customMove(movementType, movement);
            ci.cancel();
        }
    }
    @Inject(method = "getPosWithYOffset", at = @At(value = "HEAD"), cancellable = true)
    private void getPosWithYOffset(float offset, CallbackInfoReturnable<BlockPos> cir){
        if(entityOptimizations2) {
            cir.setReturnValue(zefiroptimizations$getPosWithYOffsetCustom(offset));
        }
    }

    @Unique
    private Entity self;
    @Unique
    public void customMove(MovementType movementType, Vec3d movement) {
        Entity self = (Entity) (Object) this;
        if (self.noClip) {
            self.setPosition(self.getX() + movement.x, self.getY() + movement.y, self.getZ() + movement.z);
        } else {
            self.wasOnFire = self.isOnFire();
            if (movementType == MovementType.PISTON) {
                movement = this.adjustMovementForPiston(movement);
                if (movement.equals(Vec3d.ZERO)) {
                    return;
                }
            }

            self.getWorld().getProfiler().push("move");
            if (this.movementMultiplier.lengthSquared() > 1.0E-7) {
                movement = movement.multiply(this.movementMultiplier);
                this.movementMultiplier = Vec3d.ZERO;
                self.setVelocity(Vec3d.ZERO);
            }

            movement = this.adjustMovementForSneaking(movement, movementType);
            Vec3d vec3d = zefiroptimizations$customAdjustMovementForCollisions(movement);
            double d = vec3d.lengthSquared();
            if (d > 1.0E-7) {
                if (self.fallDistance != 0.0F && d >= 1.0) {
                    BlockHitResult blockHitResult = self.getWorld()
                            .raycast(
                                    new RaycastContext(self.getPos(), self.getPos().add(vec3d), RaycastContext.ShapeType.FALLDAMAGE_RESETTING, RaycastContext.FluidHandling.WATER, self)
                            );
                    if (blockHitResult.getType() != HitResult.Type.MISS) {
                        self.onLanding();
                    }
                }

                self.setPosition(self.getX() + vec3d.x, self.getY() + vec3d.y, self.getZ() + vec3d.z);
            }

            self.getWorld().getProfiler().pop();
            self.getWorld().getProfiler().push("rest");
            boolean bl = !MathHelper.approximatelyEquals(movement.x, vec3d.x);
            boolean bl2 = !MathHelper.approximatelyEquals(movement.z, vec3d.z);
            self.horizontalCollision = bl || bl2;
            self.verticalCollision = movement.y != vec3d.y;
            self.groundCollision = self.verticalCollision && movement.y < 0.0;
            if (self.horizontalCollision) {
                self.collidedSoftly = this.hasCollidedSoftly(vec3d);
            } else {
                self.collidedSoftly = false;
            }

            self.setOnGround(self.groundCollision, vec3d);
            BlockPos blockPos = self.getLandingPos();
            BlockState blockState = self.getWorld().getBlockState(blockPos);
            this.fall(vec3d.y, self.isOnGround(), blockState, blockPos);
            if (self.isRemoved()) {
                self.getWorld().getProfiler().pop();
            } else {
                if (self.horizontalCollision) {
                    Vec3d vec3d2 = self.getVelocity();
                    self.setVelocity(bl ? 0.0 : vec3d2.x, vec3d2.y, bl2 ? 0.0 : vec3d2.z);
                }

                Block block = blockState.getBlock();
                if (movement.y != vec3d.y) {
                    block.onEntityLand(self.getWorld(), self);
                }

                if (self.isOnGround()) {
                    block.onSteppedOn(self.getWorld(), blockPos, blockState, self);
                }

                Entity.MoveEffect moveEffect = this.getMoveEffect();
                if (moveEffect.hasAny() && !self.hasVehicle()) {
                    double e = vec3d.x;
                    double f = vec3d.y;
                    double g = vec3d.z;
                    self.speed = self.speed + (float)(vec3d.length() * 0.6);
                    BlockPos blockPos2 = self.getSteppingPos();
                    BlockState blockState2 = self.getWorld().getBlockState(blockPos2);
                    boolean bl3 = this.canClimb(blockState2);
                    if (!bl3) {
                        f = 0.0;
                    }

                    self.horizontalSpeed = self.horizontalSpeed + (float)vec3d.horizontalLength() * 0.6F;
                    self.distanceTraveled = self.distanceTraveled + (float)Math.sqrt(e * e + f * f + g * g) * 0.6F;
                    if (self.distanceTraveled > this.nextStepSoundDistance && !blockState2.isAir()) {
                        boolean bl4 = blockPos2.equals(blockPos);
                        boolean bl5 = this.stepOnBlock(blockPos, blockState, moveEffect.playsSounds(), bl4, movement);
                        if (!bl4) {
                            bl5 |= this.stepOnBlock(blockPos2, blockState2, false, moveEffect.emitsGameEvents(), movement);
                        }

                        if (bl5) {
                            this.nextStepSoundDistance = this.calculateNextStepSoundDistance();
                        } else if (self.isTouchingWater()) {
                            this.nextStepSoundDistance = this.calculateNextStepSoundDistance();
                            if (moveEffect.playsSounds()) {
                                this.playSwimSound();
                            }

                            if (moveEffect.emitsGameEvents()) {
                                self.emitGameEvent(GameEvent.SWIM);
                            }
                        }
                    } else if (blockState2.isAir()) {
                        this.addAirTravelEffects();
                    }
                }

                this.tryCheckBlockCollision();
                float h = this.getVelocityMultiplier();
                self.setVelocity(self.getVelocity().multiply(h, 1.0, h));
                if (self.getWorld()
                        .getStatesInBoxIfLoaded(self.getBoundingBox().contract(1.0E-6))
                        .noneMatch(state -> state.isIn(BlockTags.FIRE) || state.isOf(Blocks.LAVA))) {
                    if (this.fireTicks <= 0) {
                        self.setFireTicks(-this.getBurningDuration());
                    }

                    if (self.wasOnFire && (self.inPowderSnow || self.isWet())) {
                        this.playExtinguishSound();
                    }
                }

                if (self.isOnFire() && (self.inPowderSnow || self.isWet())) {
                    self.setFireTicks(-this.getBurningDuration());
                }

                self.getWorld().getProfiler().pop();
            }
        }
    }

    @Unique
    private Vec3d zefiroptimizations$customAdjustMovementForCollisions(Vec3d movement) {
        Entity self = (Entity) (Object) this;

        // Early exit if movement is zero
        if (movement.equals(Vec3d.ZERO)) {
            return movement;
        }

        Box box = self.getBoundingBox();
        List<VoxelShape> collisions = self.getWorld().getEntityCollisions(self, box.stretch(movement));
        Vec3d adjustedMovement = Entity.adjustMovementForCollisions(self, movement, box, self.getWorld(), collisions);

        double stepHeight = self.getStepHeight();
        // Simplify step-up condition - check if horizontal movement is adjusted AND (falling or already adjusted vertically)
        if (stepHeight > 0.0F &&
                ((adjustedMovement.x != movement.x || adjustedMovement.z != movement.z) &&
                        (movement.y < 0.0 || adjustedMovement.y != movement.y || self.isOnGround()))) {

            // Use ternary operator for conciseness
            Box stepBox = movement.y >= 0.0 ? box : box.offset(0.0, adjustedMovement.y, 0.0);

            // Combined box expansion
            Box expandedStepBox = stepBox.stretch(movement.x, stepHeight + (movement.y < 0.0 ? 0.0 : -1.0E-5F), movement.z);
            List<VoxelShape> stepCollisions = zefiroptimizations$customFindCollisionsForMovement(self, self.getWorld(), collisions, expandedStepBox);

            float currentY = (float) adjustedMovement.y;
            float[] stepHeights = zefiroptimizations$customCollectStepHeights(stepBox, stepCollisions, (float) stepHeight, currentY);

            // Find best step-up without recalculating height difference every time
            Vec3d bestMovement = adjustedMovement;
            double bestHorizontalLengthSquared = adjustedMovement.horizontalLengthSquared();

            for (float stepUpAmount : stepHeights) {
                Vec3d potentialMovement = zefiroptimizations$customAdjustMovementForCollisions(new Vec3d(movement.x, stepUpAmount, movement.z), stepBox, stepCollisions);
                double horizontalLengthSquared = potentialMovement.horizontalLengthSquared();

                if (horizontalLengthSquared > bestHorizontalLengthSquared) {
                    bestMovement = potentialMovement;
                    bestHorizontalLengthSquared = horizontalLengthSquared;
                }
            }

            // Calculate height difference only for the best movement
            if (bestMovement != adjustedMovement) {
                return bestMovement.add(0.0, box.minY - stepBox.minY, 0.0);
            }
        }

        return adjustedMovement;
    }


    @Unique
    private static Vec3d zefiroptimizations$customAdjustMovementForCollisions(Vec3d movement, Box entityBoundingBox, List<VoxelShape> collisions) {
        if (collisions.isEmpty()) {
            return movement;
        }

        // Use a mutable box to avoid creating new objects in the loop
        Box mutableBox = new Box(entityBoundingBox.minX, entityBoundingBox.minY, entityBoundingBox.minZ,
                entityBoundingBox.maxX, entityBoundingBox.maxY, entityBoundingBox.maxZ);

        double d = movement.x;
        double e = movement.y;
        double f = movement.z;

        // Calculate Y offset first as it often has the most impact
        if (e != 0.0) {
            e = VoxelShapes.calculateMaxOffset(Direction.Axis.Y, mutableBox, collisions, e);
            if (e != 0.0) {
                mutableBox = mutableBox.offset(0.0, e, 0.0);
            }
        }

        // Determine the dominant axis (X or Z) for collision checking
        boolean isXDominant = Math.abs(d) >= Math.abs(f);

        // Calculate offset for the dominant axis
        if (isXDominant && d != 0.0) {
            d = VoxelShapes.calculateMaxOffset(Direction.Axis.X, mutableBox, collisions, d);
            if (d != 0.0) {
                mutableBox = mutableBox.offset(d, 0.0, 0.0);
            }
        } else if (f != 0.0) {
            f = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, mutableBox, collisions, f);
            if (f != 0.0) {
                mutableBox = mutableBox.offset(0.0, 0.0, f);
            }
        }

        // Calculate offset for the non-dominant axis, if needed
        if (isXDominant && f != 0.0) {
            f = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, mutableBox, collisions, f);
        } else if (d != 0.0) {
            d = VoxelShapes.calculateMaxOffset(Direction.Axis.X, mutableBox, collisions, d);
        }

        return new Vec3d(d, e, f);
    }


    @Unique
    private static List<VoxelShape> zefiroptimizations$customFindCollisionsForMovement(
            @Nullable Entity entity, World world, List<VoxelShape> regularCollisions, Box movingEntityBoundingBox
    ) {
        ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(regularCollisions.size() + 1);
        if (!regularCollisions.isEmpty()) {
            builder.addAll(regularCollisions);
        }

        WorldBorder worldBorder = world.getWorldBorder();
        boolean bl = entity != null && worldBorder.canCollide(entity, movingEntityBoundingBox);
        if (bl) {
            builder.add(worldBorder.asVoxelShape());
        }

        builder.addAll(world.getBlockCollisions(entity, movingEntityBoundingBox));
        return builder.build();
    }

    @Unique
    private static float[] zefiroptimizations$customCollectStepHeights(Box collisionBox, List<VoxelShape> collisions, float f, float stepHeight) {
        FloatList floatList = new FloatArrayList(4);

        // Iterate through collisions
        for (VoxelShape voxelShape : collisions) {
            // Only get Y points once per shape
            double[] yPoints = voxelShape.getPointPositions(Direction.Axis.Y).toDoubleArray();
            for (double d : yPoints) {
                float g = (float) (d - collisionBox.minY);

                // Combine conditions for efficiency
                if (g > 0.0F && g != stepHeight && g <= f) {
                    floatList.add(g);
                }
            }
        }

        // Convert to primitive array directly
        return floatList.toFloatArray();
    }

    @Unique
    private void handleFireAndBurning() {
        if (self.getWorld()
                .getStatesInBoxIfLoaded(self.getBoundingBox().contract(1.0E-6))
                .noneMatch(state -> state.isIn(BlockTags.FIRE) || state.isOf(Blocks.LAVA))) {
            if (this.fireTicks <= 0) {
                self.setFireTicks(-this.getBurningDuration());
            }

            if (self.wasOnFire && (self.inPowderSnow || self.isWet())) {
                this.playExtinguishSound();
            }
        }

        if (self.isOnFire() && (self.inPowderSnow || self.isWet())) {
            self.setFireTicks(-this.getBurningDuration());
        }
    }



    @Unique
    private BlockPos zefiroptimizations$getPosWithYOffsetCustom(float offset) {
        Entity self = (Entity) (Object) this;

        if (!self.supportingBlockPos.isPresent() || FastMath.abs(offset) <= 1.0E-5F) {
            return new BlockPos((int) self.getX(), (int) (self.getY() - offset), (int) self.getZ());
        }

        BlockPos blockPos = self.supportingBlockPos.get();

        if (offset > 0.5) {
            BlockState blockState = self.getWorld().getBlockState(blockPos);
            if (!(blockState.isIn(BlockTags.FENCES) || blockState.isIn(BlockTags.WALLS) || blockState.getBlock() instanceof FenceGateBlock)) {
                return blockPos.withY((int) (self.getY() - offset));
            }
        }

        return blockPos;
    }
}
