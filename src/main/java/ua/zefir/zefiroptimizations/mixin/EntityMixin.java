package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    protected abstract Vec3d adjustMovementForCollisions(Vec3d movement);
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
//        if(isOptimizeVillagers) {
        this.customMove(movementType, movement);
        ci.cancel();
//        }
    }

    @Unique
    public void customMove(MovementType movementType, Vec3d movement) {
        Entity self = (Entity) (Object) this;
        if (self.noClip) {
            self.setPosition(self.getX() + movement.x, self.getY() + movement.y, self.getZ() + movement.z);
        } else {
            self.wasOnFire = self.isOnFire();
            if (movementType == MovementType.PISTON) {
                movement = adjustMovementForPiston(movement);
                if (movement.equals(Vec3d.ZERO)) {
                    return;
                }
            }

            self.getWorld().getProfiler().push("move");
            if (movementMultiplier.lengthSquared() > 1.0E-7) {
                movement = movement.multiply(movementMultiplier);
                movementMultiplier = Vec3d.ZERO;
                self.setVelocity(Vec3d.ZERO);
            }

            movement = adjustMovementForSneaking(movement, movementType);
            Vec3d vec3d = adjustMovementForCollisions(movement);
            double d = vec3d.lengthSquared();
            if (d > 1.0E-7) {
                if (self.fallDistance != 0.0F && d >= 1.0) {
                    BlockHitResult blockHitResult = self.getWorld()
                            .raycast(new RaycastContext(self.getPos(), self.getPos().add(vec3d), RaycastContext.ShapeType.FALLDAMAGE_RESETTING, RaycastContext.FluidHandling.WATER, self));
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
                self.collidedSoftly = hasCollidedSoftly(vec3d);
            } else {
                self.collidedSoftly = false;
            }

            self.setOnGround(self.groundCollision, vec3d);
            BlockPos blockPos = self.getLandingPos();
            BlockState blockState = self.getWorld().getBlockState(blockPos);
            fall(vec3d.y, self.isOnGround(), blockState, blockPos);
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

                Entity.MoveEffect moveEffect = getMoveEffect();
                if (moveEffect.hasAny() && !self.hasVehicle()) {
                    double e = vec3d.x;
                    double f = vec3d.y;
                    double g = vec3d.z;
                    self.speed = self.speed + (float)(vec3d.length() * 0.6);
                    BlockPos blockPos2 = self.getSteppingPos();
                    BlockState blockState2 = self.getWorld().getBlockState(blockPos2);
                    boolean bl3 = canClimb(blockState2);
                    if (!bl3) {
                        f = 0.0;
                    }

                    self.horizontalSpeed = self.horizontalSpeed + (float)vec3d.horizontalLength() * 0.6F;
                    self.distanceTraveled = self.distanceTraveled + (float)Math.sqrt(e * e + f * f + g * g) * 0.6F;
                    if (self.distanceTraveled > nextStepSoundDistance && !blockState2.isAir()) {
                        boolean bl4 = blockPos2.equals(blockPos);
                        boolean bl5 = stepOnBlock(blockPos, blockState, moveEffect.playsSounds(), bl4, movement);
                        if (!bl4) {
                            bl5 |= stepOnBlock(blockPos2, blockState2, false, moveEffect.emitsGameEvents(), movement);
                        }

                        if (bl5) {
                            nextStepSoundDistance = calculateNextStepSoundDistance();
                        } else if (self.isTouchingWater()) {
                            nextStepSoundDistance = calculateNextStepSoundDistance();
                            if (moveEffect.playsSounds()) {
                                playSwimSound();
                            }

                            if (moveEffect.emitsGameEvents()) {
                                self.emitGameEvent(GameEvent.SWIM);
                            }
                        }
                    } else if (blockState2.isAir()) {
                        addAirTravelEffects();
                    }
                }

                tryCheckBlockCollision();
                float h = getVelocityMultiplier();
                self.setVelocity(self.getVelocity().multiply((double)h, 1.0, (double)h));
                if (self.getWorld()
                        .getStatesInBoxIfLoaded(self.getBoundingBox().contract(1.0E-6))
                        .noneMatch(state -> state.isIn(BlockTags.FIRE) || state.isOf(Blocks.LAVA))) {
                    if (fireTicks <= 0) {
                        self.setFireTicks(-getBurningDuration());
                    }

                    if (self.wasOnFire && (self.inPowderSnow || self.isWet())) {
                        playExtinguishSound();
                    }
                }

                if (self.isOnFire() && (self.inPowderSnow || self.isWet())) {
                    self.setFireTicks(-getBurningDuration());
                }

                self.getWorld().getProfiler().pop();
            }
        }
    }

    @Unique
    protected BlockPos getPosWithYOffsetCustom(float offset) {
        Entity self = (Entity) (Object) this;
        if (self.supportingBlockPos.isPresent()) {
            BlockPos blockPos = (BlockPos)self.supportingBlockPos.get();
            if (!(offset > 1.0E-5F)) {
                return blockPos;
            } else {
                BlockState blockState = self.getWorld().getBlockState(blockPos);
                return (!((double)offset <= 0.5) || !blockState.isIn(BlockTags.FENCES))
                        && !blockState.isIn(BlockTags.WALLS)
                        && !(blockState.getBlock() instanceof FenceGateBlock)
                        ? blockPos.withY(MathHelper.floor(self.getPos().y - (double)offset))
                        : blockPos;
            }
        } else {
            int i = MathHelper.floor(self.getPos().x);
            int j = MathHelper.floor(self.getPos().y - (double)offset);
            int k = MathHelper.floor(self.getPos().z);
            return new BlockPos(i, j, k);
        }
    }
}
