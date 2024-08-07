package ua.zefir.zefiroptimizations.actors;

import akka.actor.AbstractActor;
import akka.actor.Props;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

@Getter
@Setter
public class EntityActor extends AbstractActor {
    @Getter
    private final LivingEntity entity;
    @Getter
    private final IAsyncLivingEntityAccess entityAccess;
    private final Random random = Random.create();
    private Vec3d position;
    private Vec3d velocity;

    public EntityActor(LivingEntity entity) {
        this.entity = entity;
        this.entityAccess = (IAsyncLivingEntityAccess) entity;
        this.position = entity.getPos();
        this.velocity = entity.getVelocity();
    }

    public static Props props(LivingEntity entity) {
        return Props.create(EntityActor.class, entity);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(EntityActorMessages.AsyncTick.class, this::handleAsyncTick)
                .build();
    }

    private void handleAsyncTick(EntityActorMessages.AsyncTick msg) {
        if (entity instanceof IAsyncTickingLivingEntity
                && ((IAsyncTickingLivingEntity) entity).zefiroptimizations$isAsyncTicking()) {
            this.tickMovement();
            this.travel(
                    new Vec3d(
                            entity.sidewaysSpeed,
                            entity.upwardSpeed,
                            entity.forwardSpeed
                    )
            );
            ZefirOptimizations.getAsyncTickManager()
                    .tell(new EntityActorMessages.SyncPosition(entity, position), getSelf());
        }
    }

    private void tickMovement() {
        if (entityAccess.zefiroptimizations$getJumpingCooldown() > 0) {
            entityAccess.zefiroptimizations$setJumpingCooldown(
                    entityAccess.zefiroptimizations$getJumpingCooldown() - 1
            );
        }

        if (entity.isLogicalSideForUpdatingMovement()) {
            entityAccess.zefiroptimizations$setBodyTrackingIncrements(0);
            entity.updateTrackedPosition(position.x, position.y, position.z);
        }

        if (entityAccess.zefiroptimizations$getBodyTrackingIncrements() > 0) {
            this.lerpPosAndRotation(
                    entityAccess.zefiroptimizations$getBodyTrackingIncrements(),
                    entityAccess.zefiroptimizations$getServerX(),
                    entityAccess.zefiroptimizations$getServerY(),
                    entityAccess.zefiroptimizations$getServerZ(),
                    entityAccess.zefiroptimizations$getServerYaw(),
                    entityAccess.zefiroptimizations$getServerPitch()
            );
            entityAccess.zefiroptimizations$setBodyTrackingIncrements(
                    entityAccess.zefiroptimizations$getBodyTrackingIncrements() - 1
            );
        } else if (!entity.canMoveVoluntarily()) {
            velocity = velocity.multiply(0.98);
        }

        if (entityAccess.zefiroptimizations$getHeadTrackingIncrements() > 0) {
            this.lerpHeadYaw(
                    entityAccess.zefiroptimizations$getHeadTrackingIncrements(),
                    entityAccess.zefiroptimizations$getServerHeadYaw()
            );
            entityAccess.zefiroptimizations$setHeadTrackingIncrements(
                    entityAccess.zefiroptimizations$getHeadTrackingIncrements() - 1
            );
        }

        double d = velocity.x;
        double e = velocity.y;
        double f = velocity.z;
        if (Math.abs(velocity.x) < 0.003) {
            d = 0.0;
        }

        if (Math.abs(velocity.y) < 0.003) {
            e = 0.0;
        }

        if (Math.abs(velocity.z) < 0.003) {
            f = 0.0;
        }

        velocity = new Vec3d(d, e, f);

        if (entityAccess.zefiroptimizations$isJumping() && entityAccess.zefiroptimizations$shouldSwimInFluids()) {
            double g;
            if (entity.isInLava()) {
                g = entity.getFluidHeight(FluidTags.LAVA);
            } else {
                g = entity.getFluidHeight(FluidTags.WATER);
            }

            boolean bl = entity.getWorld().getFluidState(entity.getBlockPos()).isIn(FluidTags.WATER) && g > 0.0;
            double h = entity.getSwimHeight();
            if (!bl || entityAccess.zefiroptimizations$isOnGround() && !(g > h)) {
                if (!entity.isInLava() || entityAccess.zefiroptimizations$isOnGround() && !(g > h)) {
                    if ((entityAccess.zefiroptimizations$isOnGround() || bl && g <= h) && entityAccess.zefiroptimizations$getJumpingCooldown() == 0) {
                        this.jump();
                        entityAccess.zefiroptimizations$setJumpingCooldown(10);
                    }
                } else {
                    this.swimUpward(FluidTags.LAVA);
                }
            } else {
                this.swimUpward(FluidTags.WATER);
            }
        } else {
            entityAccess.zefiroptimizations$setJumpingCooldown(0);
        }

        entity.sidewaysSpeed *= 0.98F;
        entity.forwardSpeed *= 0.98F;
        tickFallFlying();

        if (entity.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || entity.hasStatusEffect(StatusEffects.LEVITATION)) {
            entity.onLanding();
        }

        if (entity.getControllingPassenger() instanceof PlayerEntity playerEntity
                && entity.isAlive()) {
            travelControlled(playerEntity);
        }

        if (entityAccess.zefiroptimizations$getRiptideTicks() > 0) {
            entityAccess.zefiroptimizations$setRiptideTicks(entityAccess.zefiroptimizations$getRiptideTicks() - 1);
            this.tickRiptide();
        }
    }

    private void lerpHeadYaw(int headTrackingIncrements, double serverHeadYaw) {
        entityAccess.zefiroptimizations$setYaw(
                (float) MathHelper.lerpAngleDegrees(
                        1.0 / (double) headTrackingIncrements,
                        (double) entityAccess.zefiroptimizations$getYaw(), serverHeadYaw
                )
        );
    }

    private void lerpPosAndRotation(int step, double x, double y, double z, double yaw, double pitch) {
        double d = 1.0 / (double) step;
        double e = MathHelper.lerp(d, position.x, x);
        double f = MathHelper.lerp(d, position.y, y);
        double g = MathHelper.lerp(d, position.z, z);
        float h = (float) MathHelper.lerpAngleDegrees(d, (double) entityAccess.zefiroptimizations$getYaw(), yaw);
        float i = (float) MathHelper.lerp(d, (double) entityAccess.zefiroptimizations$getPitch(), pitch);
        this.setPosition(e, f, g);
        this.setRotation(h, i);
    }

    public void setRotation(float yaw, float pitch) {
        entityAccess.zefiroptimizations$setYaw(yaw % 360.0F);
        entityAccess.zefiroptimizations$setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F) % 360.0F);
    }

    public void setPosition(double x, double y, double z) {
        position = new Vec3d(x, y, z);
    }

    private void tickRiptide() {
        if (entityAccess.zefiroptimizations$hasCollidedSoftly(velocity)) {
            entityAccess.zefiroptimizations$setRiptideTicks(0);
        }

        if (!entity.getWorld().isClient && entityAccess.zefiroptimizations$getRiptideTicks() <= 0) {
            entityAccess.zefiroptimizations$setLivingFlag(
                    entityAccess.zefiroptimizations$USING_RIPTIDE_FLAG(), false
            );
            entityAccess.zefiroptimizations$setRiptideAttackDamage(0.0F);
            entityAccess.zefiroptimizations$setRiptideStack(ItemStack.EMPTY);
        }
    }

    private void tickFallFlying() {
        boolean bl = entityAccess.zefiroptimizations$getFlag(
                entityAccess.zefiroptimizations$FALL_FLYING_FLAG_INDEX()
        );
        if (bl && !entityAccess.zefiroptimizations$isOnGround() && !entity.hasVehicle()
                && !entity.hasStatusEffect(StatusEffects.LEVITATION)) {
            ItemStack itemStack = entity.getEquippedStack(EquipmentSlot.CHEST);
            if (itemStack.isOf(Items.ELYTRA) && ElytraItem.isUsable(itemStack)) {
                bl = true;
                int i = entityAccess.zefiroptimizations$getFallFlyingTicks() + 1;
                if (!entity.getWorld().isClient && i % 10 == 0) {
                    int j = i / 10;
                    if (j % 2 == 0) {
                        itemStack.damage(1, entity, EquipmentSlot.CHEST);
                    }

                    entity.emitGameEvent(GameEvent.ELYTRA_GLIDE);
                }
            } else {
                bl = false;
            }
        } else {
            bl = false;
        }

        if (!entity.getWorld().isClient) {
            entityAccess.zefiroptimizations$setFlag(
                    entityAccess.zefiroptimizations$FALL_FLYING_FLAG_INDEX(), bl
            );
        }
    }

    private void travelControlled(PlayerEntity controllingPlayer) {
        Vec3d vec3d =
                entityAccess.zefiroptimizations$getControlledMovementInput(
                        controllingPlayer,
                        new Vec3d(
                                entity.sidewaysSpeed,
                                entity.upwardSpeed,
                                entity.forwardSpeed
                        )
                );
        entityAccess.zefiroptimizations$tickControlled(controllingPlayer, vec3d);
        if (entity.isLogicalSideForUpdatingMovement()) {
            entityAccess.zefiroptimizations$setMovementSpeed(
                    entityAccess.zefiroptimizations$getSaddledSpeed(controllingPlayer)
            );
            this.travel(vec3d);
        } else {
            this.updateLimbs(false);
            velocity = Vec3d.ZERO;
            entityAccess.zefiroptimizations$tryCheckBlockCollision();
        }
    }

    public void travel(Vec3d movementInput) {
        if (entity.isLogicalSideForUpdatingMovement()) {
            double d = entity.getFinalGravity();
            boolean bl = velocity.y <= 0.0;
            if (bl && entity.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
                d = Math.min(d, 0.01);
            }

            FluidState fluidState =
                    entity.getWorld().getFluidState(entity.getBlockPos());
            if (entityAccess.zefiroptimizations$isTouchingWater() && entityAccess.zefiroptimizations$shouldSwimInFluids()
                    && !entity.canWalkOnFluid(fluidState)) {
                double e = position.y;
                float f = entity.isSprinting() ? 0.9F : entityAccess.zefiroptimizations$getBaseMovementSpeedMultiplier();
                float g = 0.02F;
                float h = (float) entity.getAttributeValue(EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY);
                if (h > 0.0F) {
                    f += (0.54600006F - f) * h;
                    g += (entityAccess.zefiroptimizations$getMovementSpeed() - g) * h;
                }

                if (entity.hasStatusEffect(StatusEffects.DOLPHINS_GRACE)) {
                    f = 0.96F;
                }

                this.updateVelocity(g, movementInput);
                this.move(MovementType.SELF, velocity);
                Vec3d vec3d = velocity;
                if (entity.horizontalCollision && entity.isClimbing()) {
                    vec3d = new Vec3d(vec3d.x, 0.2, vec3d.z);
                }

                velocity = vec3d.multiply((double) f, 0.8F, (double) f);
                Vec3d vec3d2 = entity.applyFluidMovingSpeed(d, bl, velocity);
                velocity = vec3d2;
                if (entity.horizontalCollision && this.doesNotCollide(
                        vec3d2.x, vec3d2.y + 0.6F - position.y + e, vec3d2.z
                )) {
                    velocity = new Vec3d(vec3d2.x, 0.3F, vec3d2.z);
                }
            } else if (entity.isInLava() && entityAccess.zefiroptimizations$shouldSwimInFluids()
                    && !entity.canWalkOnFluid(fluidState)) {
                double ex = position.y;
                this.updateVelocity(0.02F, movementInput);
                this.move(MovementType.SELF, velocity);
                if (entity.getFluidHeight(FluidTags.LAVA) <= entity.getSwimHeight()) {
                    velocity = velocity.multiply(0.5, 0.8F, 0.5);
                    Vec3d vec3d3 = entity.applyFluidMovingSpeed(d, bl, velocity);
                    velocity = vec3d3;
                } else {
                    velocity = velocity.multiply(0.5);
                }

                if (d != 0.0) {
                    velocity = velocity.add(0.0, -d / 4.0, 0.0);
                }

                Vec3d vec3d3 = velocity;
                if (entity.horizontalCollision
                        && this.doesNotCollide(
                        vec3d3.x, vec3d3.y + 0.6F - position.y + ex, vec3d3.z
                )) {
                    velocity = new Vec3d(vec3d3.x, 0.3F, vec3d3.z);
                }
            } else if (entity.isFallFlying()) {
                this.limitFallDistance();
                Vec3d vec3d4 = velocity;
                Vec3d vec3d5 = entity.getRotationVector();
                float fx = entityAccess.zefiroptimizations$getPitch() * (float) (Math.PI / 180.0);
                double i = Math.sqrt(vec3d5.x * vec3d5.x + vec3d5.z * vec3d5.z);
                double j = velocity.horizontalLength();
                double k = vec3d5.length();
                double l = Math.cos((double) fx);
                l = l * l * Math.min(1.0, k / 0.4);
                velocity = velocity.add(0.0, d * (-1.0 + l * 0.75), 0.0);
                if (velocity.y < 0.0 && i > 0.0) {
                    double m = velocity.y * -0.1 * l;
                    velocity = velocity.add(vec3d5.x * m / i, m, vec3d5.z * m / i);
                }

                if (fx < 0.0F && i > 0.0) {
                    double m = j * (double) (-MathHelper.sin(fx)) * 0.04;
                    velocity = velocity.add(-vec3d5.x * m / i, m * 3.2, -vec3d5.z * m / i);
                }

                if (i > 0.0) {
                    velocity = velocity.add((vec3d5.x / i * j - velocity.x) * 0.1, 0.0,
                            (vec3d5.z / i * j - velocity.z) * 0.1);
                }

                velocity = velocity.multiply(0.99F, 0.98F, 0.99F);
                this.move(MovementType.SELF, velocity);
                if (entity.horizontalCollision && !entity.getWorld().isClient) {
                    double m = velocity.horizontalLength();
                    double n = j - m;
                    float o = (float) (n * 10.0 - 3.0);
                    if (o > 0.0F) {
                        entity.playSound(entityAccess.zefiroptimizations$getFallSound((int) o), 1.0F, 1.0F);
                        entity.damage(entity.getDamageSources().flyIntoWall(), o);
                    }
                }

                if (entityAccess.zefiroptimizations$isOnGround() && !entity.getWorld().isClient) {
                    entityAccess.zefiroptimizations$setFlag(entityAccess.zefiroptimizations$FALL_FLYING_FLAG_INDEX(), false);
                }
            } else {
                BlockPos blockPos = entity.getVelocityAffectingPos();
                float p =
                        entity.getWorld().getBlockState(blockPos).getBlock().getSlipperiness();
                float fxx = entityAccess.zefiroptimizations$isOnGround() ? p * 0.91F : 0.91F;
                Vec3d vec3d6 = this.applyMovementInput(movementInput, p);
                double q = vec3d6.y;
                if (entity.hasStatusEffect(StatusEffects.LEVITATION)) {
                    q += (0.05 * (double) (entity.getStatusEffect(StatusEffects.LEVITATION).getAmplifier()
                            + 1) - vec3d6.y) * 0.2;
                    entity.onLanding();
                } else if (!entity.getWorld().isClient || entity.getWorld().isChunkLoaded(blockPos)) {
                    q -= d;
                } else if (position.y > (double) entity.getWorld().getBottomY()) {
                    q = -0.1;
                } else {
                    q = 0.0;
                }

                velocity = new Vec3d(vec3d6.x * (double) fxx, q * 0.98F, vec3d6.z * (double) fxx);
            }
        }

        this.updateLimbs(entity instanceof Flutterer);
    }

    public void updateLimbs(boolean flutter) {
        float f = (float) MathHelper.magnitude(
                position.x - entity.prevX,
                flutter ? position.y - entity.prevY : 0.0,
                position.z - entity.prevZ
        );
        this.updateLimbs(f);
    }

    protected void updateLimbs(float posDelta) {
        float f = Math.min(posDelta * 4.0F, 1.0F);
        entity.limbAnimator.updateLimbs(f, 0.4F);
    }

    protected Vec3d applyMovementInput(Vec3d movementInput, float slipperiness) {
        this.updateVelocity(this.getMovementSpeed(slipperiness), movementInput);
        velocity = this.applyClimbingSpeed(velocity);
        this.move(MovementType.SELF, velocity);
        Vec3d vec3d = velocity;
        if ((entity.horizontalCollision
                || entityAccess.zefiroptimizations$isJumping())
                && (entity.isClimbing() || entity.getBlockStateAtPos().isOf(Blocks.POWDER_SNOW)
                && PowderSnowBlock.canWalkOnPowderSnow(entity))) {
            vec3d = new Vec3d(vec3d.x, 0.2, vec3d.z);
        }

        return vec3d;
    }

    private Vec3d applyClimbingSpeed(Vec3d motion) {
        if (entity.isClimbing()) {
            entity.onLanding();
            float f = 0.15F;
            double d = MathHelper.clamp(motion.x, -0.15F, 0.15F);
            double e = MathHelper.clamp(motion.z, -0.15F, 0.15F);
            double g = Math.max(motion.y, -0.15F);
            if (g < 0.0 && !entity.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING)
                    && entity.isHoldingOntoLadder() && entity instanceof PlayerEntity) {
                g = 0.0;
            }

            motion = new Vec3d(d, g, e);
        }

        return motion;
    }

    private float getMovementSpeed(float slipperiness) {
        return entityAccess.zefiroptimizations$isOnGround() ? entityAccess.zefiroptimizations$getMovementSpeed()
                * (0.21600002F / (slipperiness * slipperiness * slipperiness))
                : this.getOffGroundSpeed();
    }

    protected float getOffGroundSpeed() {
        return entity.getControllingPassenger() instanceof PlayerEntity
                ? entityAccess.zefiroptimizations$getMovementSpeed() * 0.1F
                : 0.02F;
    }

    public void updateVelocity(float speed, Vec3d movementInput) {
        Vec3d vec3d =
                movementInputToVelocity(movementInput, speed, entityAccess.zefiroptimizations$getYaw());
        velocity = velocity.add(vec3d);
    }

    private static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7) {
            return Vec3d.ZERO;
        } else {
            Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply((double) speed);
            float f = MathHelper.sin(yaw * (float) (Math.PI / 180.0));
            float g = MathHelper.cos(yaw * (float) (Math.PI / 180.0));
            return new Vec3d(vec3d.x * (double) g - vec3d.z * (double) f, vec3d.y,
                    vec3d.z * (double) g + vec3d.x * (double) f);
        }
    }


    private boolean doesNotCollide(double offsetX, double offsetY, double offsetZ) {
        return this.doesNotCollide(entityAccess.zefiroptimizations$getBoundingBox().offset(offsetX, offsetY, offsetZ));
    }

    private boolean doesNotCollide(Box box) {
        return entity.getWorld().isSpaceEmpty(entity, box) && !entity.getWorld().containsFluid(box);
    }

    public void swimUpward(TagKey<Fluid> fluid) {
        velocity = velocity.add(0.0, 0.04F, 0.0);
    }

    private void limitFallDistance() {
        if (velocity.getY() > -0.5 && entity.fallDistance > 1.0F) {
            entity.fallDistance = 1.0F;
        }
    }

    protected void move(MovementType movementType, Vec3d movement) {
        if (entity.noClip) {
            this.setPosition(position.x + movement.x, position.y + movement.y, position.z + movement.z);
        } else {
            if (movementType == MovementType.PISTON) {
                movement = this.adjustMovementForPiston(movement);
                if (movement.equals(Vec3d.ZERO)) {
                    return;
                }
            }

            if (entityAccess.zefiroptimizations$getMovementMultiplier().lengthSquared() > 1.0E-7) {
                movement = movement.multiply(entityAccess.zefiroptimizations$getMovementMultiplier());
                entityAccess.zefiroptimizations$setMovementMultiplier(Vec3d.ZERO);
                velocity = Vec3d.ZERO;
            }

            movement = entityAccess.zefiroptimizations$adjustMovementForSneaking(movement, movementType);
            Vec3d vec3d = this.adjustMovementForCollisions(movement);
            if (vec3d.lengthSquared() > 1.0E-7) {
                if (entity.fallDistance != 0.0F && vec3d.lengthSquared() >= 1.0) {
                    BlockHitResult blockHitResult = entity.getWorld()
                            .raycast(
                                    new RaycastContext(entity.getPos(), entity.getPos().add(vec3d), RaycastContext.ShapeType.FALLDAMAGE_RESETTING, RaycastContext.FluidHandling.WATER, entity)
                            );
                    if (blockHitResult.getType() != HitResult.Type.MISS) {
                        entity.onLanding();
                    }
                }

                this.setPosition(position.x + vec3d.x, position.y + vec3d.y, position.z + vec3d.z);
            }

            entity.horizontalCollision =
                    !MathHelper.approximatelyEquals(movement.x, vec3d.x)
                            || !MathHelper.approximatelyEquals(movement.z, vec3d.z);
            entity.verticalCollision = movement.y != vec3d.y;
            entity.groundCollision = entity.verticalCollision && movement.y < 0.0;

            if (entity.horizontalCollision) {
                entity.collidedSoftly =
                        entityAccess.zefiroptimizations$hasCollidedSoftly(vec3d);
            } else {
                entity.collidedSoftly = false;
            }
            entityAccess.zefiroptimizations$setOnGround(entity.groundCollision);

            BlockState blockState =
                    entity.getWorld().getBlockState(entity.getLandingPos());
            entityAccess.zefiroptimizations$fall(
                    vec3d.y,
                    entityAccess.zefiroptimizations$isOnGround(),
                    blockState,
                    entity.getLandingPos()
            );

            if (entity.horizontalCollision) {
                velocity = new Vec3d(
                        !MathHelper.approximatelyEquals(movement.x, vec3d.x) ? 0.0 : velocity.x,
                        velocity.y,
                        !MathHelper.approximatelyEquals(movement.z, vec3d.z) ? 0.0 : velocity.z
                );
            }

            if (movement.y != vec3d.y) {
                blockState.getBlock().onEntityLand(entity.getWorld(), entity);
            }

            if (entityAccess.zefiroptimizations$isOnGround() && !entity.isRemoved()) {
                blockState.getBlock().onSteppedOn(
                        entity.getWorld(),
                        entity.getLandingPos(), blockState, entity
                );
            }

            Entity.MoveEffect moveEffect = entityAccess.zefiroptimizations$getMoveEffect();
            if (moveEffect.hasAny() && !entity.hasVehicle()) {
                double e = vec3d.x;
                double f = vec3d.y;
                double g = vec3d.z;
                entity.speed += (float) (vec3d.length() * 0.6);
                BlockPos blockPos = entity.getSteppingPos();
                BlockState blockState2 = entity.getWorld().getBlockState(blockPos);
                if (entityAccess.zefiroptimizations$canClimb(blockState2)) {
                    f = 0.0;
                }

                entity.horizontalSpeed += (float) vec3d.horizontalLength() * 0.6F;
                entity.distanceTraveled += (float) Math.sqrt(e * e + f * f + g * g) * 0.6F;
                if (entity.distanceTraveled > entityAccess.zefiroptimizations$calculateNextStepSoundDistance()
                        && !blockState2.isAir()) {
                    entityAccess.zefiroptimizations$addAirTravelEffects();
                    entityAccess.zefiroptimizations$playSwimSound();
                    if (moveEffect.emitsGameEvents()) {
                        entity.emitGameEvent(GameEvent.SWIM);
                    }
                } else if (blockState2.isAir()) {
                    entityAccess.zefiroptimizations$addAirTravelEffects();
                }
            }

            entityAccess.zefiroptimizations$tryCheckBlockCollision();
            float h = entityAccess.zefiroptimizations$getVelocityMultiplier();
            velocity = velocity.multiply((double) h, 1.0, (double) h);

            if (entityAccess.zefiroptimizations$getFireTicks() <= 0) {
                entityAccess.zefiroptimizations$setFireTicks(-entityAccess.zefiroptimizations$getBurningDuration());
            }

            if (entity.wasOnFire && (entity.inPowderSnow || entity.isWet())) {
                entityAccess.zefiroptimizations$playExtinguishSound();
            }

            if (entity.isOnFire() && (entity.inPowderSnow || entity.isWet())) {
                entityAccess.zefiroptimizations$setFireTicks(-entityAccess.zefiroptimizations$getBurningDuration());
            }
        }
    }


//    protected void move(MovementType movementType, Vec3d movement) {
//        if (entity.noClip) {
//            entity.setPosition(entity.getX() + movement.x, entity.getY() + movement.y, entity.getZ() + movement.z);
//        } else {
//            entity.wasOnFire = entity.isOnFire();
//            if (movementType == MovementType.PISTON) {
//                movement = this.adjustMovementForPiston(movement);
//                if (movement.equals(Vec3d.ZERO)) {
//                    return;
//                }
//            }
//
//            entity.getWorld().getProfiler().push("move");
//            if (entityAccess.zefiroptimizations$getMovementMultiplier().lengthSquared() > 1.0E-7) {
//                movement = movement.multiply(entityAccess.zefiroptimizations$getMovementMultiplier());
//                entityAccess.zefiroptimizations$setMovementMultiplier(Vec3d.ZERO);
//                entity.setVelocity(Vec3d.ZERO);
//            }
//
//            movement = entityAccess.zefiroptimizations$adjustMovementForSneaking(movement, movementType);
//            Vec3d vec3d = entityAccess.zefiroptimizations$adjustMovementForCollisions(movement);
//            double d = vec3d.lengthSquared();
//            if (d > 1.0E-7) {
//                if (entity.fallDistance != 0.0F && d >= 1.0) {
//                    BlockHitResult blockHitResult = entity.getWorld()
//                            .raycast(
//                                    new RaycastContext(entity.getPos(), entity.getPos().add(vec3d), RaycastContext.ShapeType.FALLDAMAGE_RESETTING, RaycastContext.FluidHandling.WATER, entity)
//                            );
//                    if (blockHitResult.getType() != HitResult.Type.MISS) {
//                        entity.onLanding();
//                    }
//                }
//
//                this.setPosition(entity.getX() + vec3d.x, entity.getY() + vec3d.y, entity.getZ() + vec3d.z);
//            }
//
//            entity.getWorld().getProfiler().pop();
//            entity.getWorld().getProfiler().push("rest");
//            boolean bl = !MathHelper.approximatelyEquals(movement.x, vec3d.x);
//            boolean bl2 = !MathHelper.approximatelyEquals(movement.z, vec3d.z);
//            entity.horizontalCollision = bl || bl2;
//            entity.verticalCollision = movement.y != vec3d.y;
//            entity.groundCollision = entity.verticalCollision && movement.y < 0.0;
//            if (entity.horizontalCollision) {
//                entity.collidedSoftly = entityAccess.zefiroptimizations$hasCollidedSoftly(vec3d);
//            } else {
//                entity.collidedSoftly = false;
//            }
//
//            entity.setOnGround(entity.groundCollision, vec3d);
//            BlockPos blockPos = entity.getLandingPos();
//            BlockState blockState = entity.getWorld().getBlockState(blockPos);
//            entityAccess.zefiroptimizations$fall(vec3d.y, entity.isOnGround(), blockState, blockPos);
//            if (entity.isRemoved()) {
//                entity.getWorld().getProfiler().pop();
//            } else {
//                if (entity.horizontalCollision) {
//                    Vec3d vec3d2 = this.getVelocity();
//                    entity.setVelocity(bl ? 0.0 : vec3d2.x, vec3d2.y, bl2 ? 0.0 : vec3d2.z);
//                }
//
//                Block block = blockState.getBlock();
//                if (movement.y != vec3d.y) {
//                    block.onEntityLand(entity.getWorld(), entity);
//                }
//
//                if (entity.isOnGround()) {
//                    block.onSteppedOn(entity.getWorld(), blockPos, blockState, entity);
//                }
//
//                Entity.MoveEffect moveEffect = entityAccess.zefiroptimizations$getMoveEffect();
//                if (moveEffect.hasAny() && !entity.hasVehicle()) {
//                    double e = vec3d.x;
//                    double f = vec3d.y;
//                    double g = vec3d.z;
//                    entity.speed = entity.speed + (float)(vec3d.length() * 0.6);
//                    BlockPos blockPos2 = entity.getSteppingPos();
//                    BlockState blockState2 = entity.getWorld().getBlockState(blockPos2);
//                    boolean bl3 = entityAccess.zefiroptimizations$canClimb(blockState2);
//                    if (!bl3) {
//                        f = 0.0;
//                    }
//
//                    entity.horizontalSpeed = entity.horizontalSpeed + (float)vec3d.horizontalLength() * 0.6F;
//                    entity.distanceTraveled = entity.distanceTraveled + (float)Math.sqrt(e * e + f * f + g * g) * 0.6F;
//                    if (entity.distanceTraveled > entityAccess.zefiroptimizations$getNextStepSoundDistance() && !blockState2.isAir()) {
//                        boolean bl4 = blockPos2.equals(blockPos);
//                        boolean bl5 = this.stepOnBlock(blockPos, blockState, moveEffect.playsSounds(), bl4, movement);
//                        if (!bl4) {
//                            bl5 |= this.stepOnBlock(blockPos2, blockState2, false, moveEffect.emitsGameEvents(), movement);
//                        }
//
//                        if (bl5) {
//                            entityAccess.zefiroptimizations$setNextStepSoundDistance(entityAccess.zefiroptimizations$calculateNextStepSoundDistance());
//                        } else if (entity.isTouchingWater()) {
//                            entityAccess.zefiroptimizations$setNextStepSoundDistance(entityAccess.zefiroptimizations$calculateNextStepSoundDistance());
//                            if (moveEffect.playsSounds()) {
//                                entityAccess.zefiroptimizations$playSwimSound();
//                            }
//
//                            if (moveEffect.emitsGameEvents()) {
//                                entity.emitGameEvent(GameEvent.SWIM);
//                            }
//                        }
//                    } else if (blockState2.isAir()) {
//                        entityAccess.zefiroptimizations$addAirTravelEffects();
//                    }
//                }
//
//                entityAccess.zefiroptimizations$tryCheckBlockCollision();
//                float h = entityAccess.zefiroptimizations$getVelocityMultiplier();
//                this.setVelocity(entity.getVelocity().multiply((double)h, 1.0, (double)h));
//                if (entity.getWorld()
//                        .getStatesInBoxIfLoaded(entity.getBoundingBox().contract(1.0E-6))
//                        .noneMatch(state -> state.isIn(BlockTags.FIRE) || state.isOf(Blocks.LAVA))) {
//                    if (entityAccess.zefiroptimizations$getFireTicks() <= 0) {
//                        entity.setFireTicks(-entityAccess.zefiroptimizations$getBurningDuration());
//                    }
//
//                    if (entity.wasOnFire && (entity.inPowderSnow || entity.isWet())) {
//                        entityAccess.zefiroptimizations$playExtinguishSound();
//                    }
//                }
//
//                if (entity.isOnFire() && (entity.inPowderSnow || entity.isWet())) {
//                    entity.setFireTicks(-entityAccess.zefiroptimizations$getBurningDuration());
//                }
//
//                entity.getWorld().getProfiler().pop();
//            }
//        }
//    }

    protected boolean stepOnBlock(BlockPos pos, BlockState state, boolean playSound, boolean emitEvent, Vec3d movement) {
        if (state.isAir()) {
            return false;
        } else {
            boolean bl = entityAccess.zefiroptimizations$canClimb(state);
            if ((entity.isOnGround() || bl || entity.isInSneakingPose() && movement.y == 0.0 || entity.isOnRail()) && !entity.isSwimming()) {
                if (playSound) {
                    entityAccess.zefiroptimizations$playStepSounds(pos, state);
                }

                if (emitEvent) {
                    entity.getWorld().emitGameEvent(GameEvent.STEP, entity.getPos(), GameEvent.Emitter.of(entity, state));
                }

                return true;
            } else {
                return false;
            }
        }
    }

    protected Vec3d adjustMovementForPiston(Vec3d movement) {
        if (movement.lengthSquared() <= 1.0E-7) {
            return movement;
        } else {
            long l = entity.getWorld().getTime();
            if (l != entityAccess.zefiroptimizations$getPistonMovementTick()) {
                entityAccess.zefiroptimizations$setPistonMovementDelta(new double[]{0.0, 0.0, 0.0});
                entityAccess.zefiroptimizations$setPistonMovementTick(l);
            }

            if (movement.x != 0.0) {
                double d = this.calculatePistonMovementFactor(Direction.Axis.X, movement.x);
                return Math.abs(d) <= 9.999999747378752E-6 ? Vec3d.ZERO : new Vec3d(d, 0.0, 0.0);
            } else if (movement.y != 0.0) {
                double d = this.calculatePistonMovementFactor(Direction.Axis.Y, movement.y);
                return Math.abs(d) <= 9.999999747378752E-6 ? Vec3d.ZERO : new Vec3d(0.0, d, 0.0);
            } else if (movement.z != 0.0) {
                double d = this.calculatePistonMovementFactor(Direction.Axis.Z, movement.z);
                return Math.abs(d) <= 9.999999747378752E-6 ? Vec3d.ZERO : new Vec3d(0.0, 0.0, d);
            } else {
                return Vec3d.ZERO;
            }
        }
    }

    private double calculatePistonMovementFactor(Direction.Axis axis, double offsetFactor) {
        int i = axis.ordinal();
        double d = MathHelper.clamp(
                offsetFactor + entityAccess.zefiroptimizations$getPistonMovementDelta()[i], -0.51, 0.51
        );
        offsetFactor = d - entityAccess.zefiroptimizations$getPistonMovementDelta()[i];
        entityAccess.zefiroptimizations$getPistonMovementDelta()[i] = d;
        return offsetFactor;
    }

    private Vec3d adjustMovementForCollisions(Vec3d movement) {
        Box box = entityAccess.zefiroptimizations$getBoundingBox();
        return adjustMovementForCollisions(
                entity, movement, box, entity.getWorld()
        );
    }

    private static Vec3d adjustMovementForCollisions(@Nullable Entity entity, Vec3d movement, Box entityBoundingBox, World world) {
        if (movement.lengthSquared() == 0.0) {
            return movement;
        } else {
            double d = movement.x;
            double e = movement.y;
            double f = movement.z;
            if (e != 0.0) {
                e = VoxelShapes.calculateMaxOffset(
                        Direction.Axis.Y, entityBoundingBox, world.getBlockCollisions(entity, entityBoundingBox.stretch(movement)), e
                );
                if (e != 0.0) {
                    entityBoundingBox = entityBoundingBox.offset(0.0, e, 0.0);
                }
            }

            boolean bl = Math.abs(d) < Math.abs(f);
            if (bl && f != 0.0) {
                f = VoxelShapes.calculateMaxOffset(
                        Direction.Axis.Z, entityBoundingBox, world.getBlockCollisions(entity, entityBoundingBox.stretch(movement)), f
                );
                if (f != 0.0) {
                    entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, f);
                }
            }

            if (d != 0.0) {
                d = VoxelShapes.calculateMaxOffset(
                        Direction.Axis.X, entityBoundingBox, world.getBlockCollisions(entity, entityBoundingBox.stretch(movement)), d
                );
                if (!bl && d != 0.0) {
                    entityBoundingBox = entityBoundingBox.offset(d,0.0, 0.0);
                }
            }

            if (!bl && f != 0.0) {
                f = VoxelShapes.calculateMaxOffset(
                        Direction.Axis.Z, entityBoundingBox, world.getBlockCollisions(entity, entityBoundingBox.stretch(movement)), f
                );
            }

            return new Vec3d(d, e, f);
        }
    }

    @VisibleForTesting
    public void jump() {
        float f = entityAccess.zefiroptimizations$getJumpVelocity();
        if (!(f <= 1.0E-5F)) {
            Vec3d vec3d = this.getVelocity();
            velocity = new Vec3d(vec3d.x, (double) f, vec3d.z);
            if (entity.isSprinting()) {
                float g = entityAccess.zefiroptimizations$getYaw() * (float) (Math.PI / 180.0);
                this.addVelocityInternal(new Vec3d((double) (-MathHelper.sin(g)) * 0.2, 0.0,
                        (double) MathHelper.cos(g) * 0.2));
            }

            entity.velocityDirty = true;
        }
    }

    public void addVelocityInternal(Vec3d velocity) {
        this.velocity = this.velocity.add(velocity);
    }
}
