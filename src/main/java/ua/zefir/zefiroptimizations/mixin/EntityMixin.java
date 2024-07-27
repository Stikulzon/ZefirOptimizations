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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
        if(entityOptimizations1) {
            this.customMove(movementType, movement);
            ci.cancel();
        }
    }
    @Inject(method = "getPosWithYOffset", at = @At(value = "HEAD"), cancellable = true)
    private void getPosWithYOffset(float offset, CallbackInfoReturnable<BlockPos> cir){
        if(entityOptimizations2) {
            this.getPosWithYOffsetCustom(offset);
            cir.cancel();
        }
    }

    @Unique
    public void customMove(MovementType movementType, Vec3d movement) {
        Entity self = (Entity) (Object) this;

        if (self.noClip) {
            self.setPosition(self.getX() + movement.x, self.getY() + movement.y, self.getZ() + movement.z);
            return;
        }

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
        Vec3d adjustedMovement = adjustMovementForCollisions(movement);
        double adjustedMovementLengthSquared = adjustedMovement.lengthSquared();

        if (adjustedMovementLengthSquared > 1.0E-7) {
            if (self.fallDistance != 0.0F && adjustedMovementLengthSquared >= 1.0) {
                BlockHitResult blockHitResult = self.getWorld()
                        .raycast(new RaycastContext(self.getPos(), self.getPos().add(adjustedMovement), RaycastContext.ShapeType.FALLDAMAGE_RESETTING, RaycastContext.FluidHandling.WATER, self));
                if (blockHitResult.getType() != HitResult.Type.MISS) {
                    self.onLanding();
                }
            }

            self.setPosition(self.getX() + adjustedMovement.x, self.getY() + adjustedMovement.y, self.getZ() + adjustedMovement.z);
        }

        self.getWorld().getProfiler().pop();
        self.getWorld().getProfiler().push("rest");

        boolean horizontalCollision = !MathHelper.approximatelyEquals(movement.x, adjustedMovement.x) || !MathHelper.approximatelyEquals(movement.z, adjustedMovement.z);
        boolean verticalCollision = movement.y != adjustedMovement.y;
        boolean groundCollision = verticalCollision && movement.y < 0.0;

        self.horizontalCollision = horizontalCollision;
        self.verticalCollision = verticalCollision;
        self.groundCollision = groundCollision;

        self.collidedSoftly = horizontalCollision && hasCollidedSoftly(adjustedMovement);

        self.setOnGround(groundCollision, adjustedMovement);
        BlockPos landingPos = self.getLandingPos();
        BlockState landingBlockState = self.getWorld().getBlockState(landingPos);
        fall(adjustedMovement.y, groundCollision, landingBlockState, landingPos);

        if (self.isRemoved()) {
            self.getWorld().getProfiler().pop();
            return;
        }

        if (horizontalCollision) {
            Vec3d currentVelocity = self.getVelocity();
            self.setVelocity(horizontalCollision ? 0.0 : currentVelocity.x, currentVelocity.y, horizontalCollision ? 0.0 : currentVelocity.z);
        }

        Block block = landingBlockState.getBlock();
        if (movement.y != adjustedMovement.y) {
            block.onEntityLand(self.getWorld(), self);
        }

        if (groundCollision) {
            block.onSteppedOn(self.getWorld(), landingPos, landingBlockState, self);
        }

        Entity.MoveEffect moveEffect = getMoveEffect();

        if (moveEffect.hasAny() && !self.hasVehicle()) {
            double xMovement = adjustedMovement.x;
            double yMovement = adjustedMovement.y;
            double zMovement = adjustedMovement.z;

            self.speed += (float) (adjustedMovement.length() * 0.6);
            BlockPos steppingPos = self.getSteppingPos();
            BlockState steppingBlockState = self.getWorld().getBlockState(steppingPos);

            if (!canClimb(steppingBlockState)) {
                yMovement = 0.0;
            }

            self.horizontalSpeed += (float) (adjustedMovement.horizontalLength() * 0.6F);
            self.distanceTraveled += (float) (Math.sqrt(xMovement * xMovement + yMovement * yMovement + zMovement * zMovement) * 0.6F);

            if (self.distanceTraveled > nextStepSoundDistance && !steppingBlockState.isAir()) {
                boolean sameBlockPos = steppingPos.equals(landingPos);
                boolean stepSoundPlayed = stepOnBlock(landingPos, landingBlockState, moveEffect.playsSounds(), sameBlockPos, movement);

                if (!sameBlockPos) {
                    stepSoundPlayed |= stepOnBlock(steppingPos, steppingBlockState, false, moveEffect.emitsGameEvents(), movement);
                }

                if (stepSoundPlayed) {
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
            } else if (steppingBlockState.isAir()) {
                addAirTravelEffects();
            }
        }

        tryCheckBlockCollision();
        float velocityMultiplier = getVelocityMultiplier();
        self.setVelocity(self.getVelocity().multiply(velocityMultiplier, 1.0, velocityMultiplier));

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

    @Unique
    protected BlockPos getPosWithYOffsetCustom(float offset) {
        Entity self = (Entity) (Object) this;
        Optional<BlockPos> supportingBlockPosOptional = self.supportingBlockPos;

        if (supportingBlockPosOptional.isPresent()) {
            BlockPos blockPos = supportingBlockPosOptional.get();

            if (offset <= 1.0E-5F) {
                return blockPos;
            }

            BlockState blockState = self.getWorld().getBlockState(blockPos);

            if (offset > 0.5) {
                return blockPos.withY(MathHelper.floor(self.getPos().y - (double) offset));
            }

            if (blockState.isIn(BlockTags.FENCES) || blockState.isIn(BlockTags.WALLS) || blockState.getBlock() instanceof FenceGateBlock) {
                return blockPos;
            }

            return blockPos.withY(MathHelper.floor(self.getPos().y - (double) offset));
        } else {
            double posX = self.getPos().x;
            double posY = self.getPos().y - (double) offset;
            double posZ = self.getPos().z;

            return new BlockPos(MathHelper.floor(posX), MathHelper.floor(posY), MathHelper.floor(posZ));
        }
    }
}
