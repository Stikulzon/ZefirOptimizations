package ua.zefir.zefiroptimizations.actors;

import akka.actor.AbstractActor;
import akka.actor.Props;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
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
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Getter
@Setter
public class EntityActor extends AbstractActor {
    protected final LivingEntity entity;
    protected final IAsyncLivingEntityAccess entityAccess;
    protected final ThreadLocalRandom random = ThreadLocalRandom.current();

    public EntityActor(LivingEntity entity) {
        this.entity = entity;
        this.entityAccess = (IAsyncLivingEntityAccess) entity;
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

    protected void handleAsyncTick(EntityActorMessages.AsyncTick msg) {
            if (!entity.isRemoved()) {
                this.tickMobEntityMovement();
            }
    }

    public void tickMobEntityMovement() {
        tickMovement();
    }

    protected void tickMovement() {
        if (entityAccess.zefiroptimizations$getJumpingCooldown() > 0) {
            entityAccess.zefiroptimizations$setJumpingCooldown(entityAccess.zefiroptimizations$getJumpingCooldown() - 1);
        }

        if (entity.isLogicalSideForUpdatingMovement()) {
            entityAccess.zefiroptimizations$setBodyTrackingIncrements(0);
            entity.updateTrackedPosition(entity.getX(), entity.getY(), entity.getZ());
        }

        if (entityAccess.zefiroptimizations$getBodyTrackingIncrements() > 0) {
            this.lerpPosAndRotation(entityAccess.zefiroptimizations$getBodyTrackingIncrements(), entityAccess.zefiroptimizations$getServerX(), entityAccess.zefiroptimizations$getServerY(), entityAccess.zefiroptimizations$getServerZ(), entityAccess.zefiroptimizations$getServerYaw(), entityAccess.zefiroptimizations$getServerPitch());
            entityAccess.zefiroptimizations$setBodyTrackingIncrements(entityAccess.zefiroptimizations$getBodyTrackingIncrements() - 1);
        } else if (!entity.canMoveVoluntarily()) {
            entity.setVelocity(entity.getVelocity().multiply(0.98));
        }

        if (entityAccess.zefiroptimizations$getHeadTrackingIncrements() > 0) {
            this.lerpHeadYaw(entityAccess.zefiroptimizations$getHeadTrackingIncrements(), entityAccess.zefiroptimizations$getServerHeadYaw());
            entityAccess.zefiroptimizations$setHeadTrackingIncrements(entityAccess.zefiroptimizations$getHeadTrackingIncrements() - 1);
        }

        Vec3d vec3d = entity.getVelocity();
        double d = vec3d.x;
        double e = vec3d.y;
        double f = vec3d.z;
        if (Math.abs(vec3d.x) < 0.003) {
            d = 0.0;
        }

        if (Math.abs(vec3d.y) < 0.003) {
            e = 0.0;
        }

        if (Math.abs(vec3d.z) < 0.003) {
            f = 0.0;
        }

        entity.setVelocity(d, e, f);
        entity.getWorld().getProfiler().push("ai");
        if (entityAccess.zefiroptimizations$isImmobile()) {
            entityAccess.zefiroptimizations$setJumping(false);
            entity.sidewaysSpeed = 0.0F;
            entity.forwardSpeed = 0.0F;
        } else if (entity.canMoveVoluntarily()) {
            entity.getWorld().getProfiler().push("newAi");
//            entityAccess.zefiroptimizations$tickNewAi();
            ZefirOptimizations.getAsyncTickManager().tell(new EntityActorMessages.MainThreadCallback(result -> {
                // This code runs on the MAIN SERVER THREAD! Be very careful!
                // Do not block this thread or you will freeze the server.
                entityAccess.zefiroptimizations$tickNewAi();
                ZefirOptimizations.LOGGER.info("Result from actor: " + result);
            }), getSelf());
            entity.getWorld().getProfiler().pop();
        }

        entity.getWorld().getProfiler().pop();
        entity.getWorld().getProfiler().push("jump");
        if (entityAccess.zefiroptimizations$isJumping() && entityAccess.zefiroptimizations$shouldSwimInFluids()) {
            double g;
            if (entity.isInLava()) {
                g = entity.getFluidHeight(FluidTags.LAVA);
            } else {
                g = entity.getFluidHeight(FluidTags.WATER);
            }

            boolean bl = entity.isTouchingWater() && g > 0.0;
            double h = entity.getSwimHeight();
            if (!bl || entity.isOnGround() && !(g > h)) {
                if (!entity.isInLava() || entity.isOnGround() && !(g > h)) {
                    if ((entity.isOnGround() || bl && g <= h) && entityAccess.zefiroptimizations$getJumpingCooldown() == 0) {
                        entity.jump();
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

        entity.getWorld().getProfiler().pop();
        entity.getWorld().getProfiler().push("travel");
        entity.sidewaysSpeed *= 0.98F;
        entity.forwardSpeed *= 0.98F;
        this.tickFallFlying();
        Box box = entity.getBoundingBox();
        Vec3d vec3d2 = new Vec3d(entity.sidewaysSpeed, entity.upwardSpeed, entity.forwardSpeed);
        if (entity.hasStatusEffect(StatusEffects.SLOW_FALLING) || entity.hasStatusEffect(StatusEffects.LEVITATION)) {
            entity.onLanding();
        }

        label104: {
            if (entity.getControllingPassenger() instanceof PlayerEntity playerEntity && entity.isAlive()) {
                this.travelControlled(playerEntity, vec3d2);
                break label104;
            }

            this.travel(vec3d2);
        }

        entity.getWorld().getProfiler().pop();
        entity.getWorld().getProfiler().push("freezing");
        if (!entity.getWorld().isClient && !entity.isDead()) {
            int i = entity.getFrozenTicks();
            if (entity.inPowderSnow && entity.canFreeze()) {
                entity.setFrozenTicks(Math.min(entity.getMinFreezeDamageTicks(), i + 1));
            } else {
                entity.setFrozenTicks(Math.max(0, i - 2));
            }
        }

        entityAccess.zefiroptimizations$removePowderSnowSlow();
        entityAccess.zefiroptimizations$addPowderSnowSlowIfNeeded();
        if (!entity.getWorld().isClient && entity.age % 40 == 0 && entity.isFrozen() && entity.canFreeze()) {
            entity.damage(entity.getDamageSources().freeze(), 1.0F);
        }

        entity.getWorld().getProfiler().pop();
        entity.getWorld().getProfiler().push("push");
        if (entityAccess.zefiroptimizations$getRiptideTicks() > 0) {
            entityAccess.zefiroptimizations$setRiptideTicks(entityAccess.zefiroptimizations$getRiptideTicks() - 1);
            this.tickRiptide(box, entity.getBoundingBox());
        }

//        entityAccess.zefiroptimizations$tickCramming();
        this.tickCramming();
        entity.getWorld().getProfiler().pop();
        if (!entity.getWorld().isClient && entity.hurtByWater() && entity.isWet()) {
            entity.damage(entity.getDamageSources().drown(), 1.0F);
        }
    }

    protected void swimUpward(TagKey<Fluid> fluid) {
        entity.setVelocity(entity.getVelocity().add(0.0, 0.04F, 0.0));
    }

    protected void tickCramming() {
        if (entity.getWorld().isClient()) {
            entity.getWorld()
                    .getEntitiesByType(EntityType.PLAYER, entity.getBoundingBox(), EntityPredicates.canBePushedBy(entity))
                    .forEach(entityAccess::zefiroptimizations$pushAway);
        } else {
            List<Entity> list = entity.getWorld().getOtherEntities(entity, entity.getBoundingBox(), EntityPredicates.canBePushedBy(entity));

            if (!list.isEmpty()) {
                int maxEntityCramming = entity.getWorld().getGameRules().getInt(GameRules.MAX_ENTITY_CRAMMING);
                if (maxEntityCramming > 0 && list.size() > maxEntityCramming - 1 && random.nextInt(4) == 0) {
                    int nonVehicleEntities = 0;
                    for (Entity entity : list) {
                        if (!entity.hasVehicle()) {
                            nonVehicleEntities++;
                        }
                    }

                    if (nonVehicleEntities > maxEntityCramming - 1) {
                        entity.damage(entity.getDamageSources().cramming(), 6.0F);
                    }
                }

                // Create a new list to store entities to be pushed
                List<Entity> entitiesToPush = new ArrayList<>(list);

                // Push entities away after the loop
                for (Entity entity2 : entitiesToPush) {
                    entityAccess.zefiroptimizations$pushAway(entity2);
                }
            }
        }
    }


    protected void tickRiptide(Box a, Box b) {
        Box box = a.union(b);
        List<Entity> list = entity.getWorld().getOtherEntities(entity, box);
        if (!list.isEmpty()) {
            for (Entity entity : list) {
                if (entity instanceof LivingEntity) {
                    entityAccess.zefiroptimizations$attackLivingEntity((LivingEntity)entity);
                    entityAccess.zefiroptimizations$setRiptideTicks(0);
                    entity.setVelocity(entity.getVelocity().multiply(-0.2));
                    break;
                }
            }
        } else if (entity.horizontalCollision) {
            entityAccess.zefiroptimizations$setRiptideTicks(0);
        }

        if (!entity.getWorld().isClient && entityAccess.zefiroptimizations$getRiptideTicks() <= 0) {
            entityAccess.zefiroptimizations$setLivingFlag(entityAccess.zefiroptimizations$USING_RIPTIDE_FLAG(), false);
            entityAccess.zefiroptimizations$setRiptideAttackDamage(0.0F);
            entityAccess.zefiroptimizations$setRiptideStack(null);
        }
    }

    private void travelControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
        Vec3d vec3d = entityAccess.zefiroptimizations$getControlledMovementInput(controllingPlayer, movementInput);
        entityAccess.zefiroptimizations$tickControlled(controllingPlayer, vec3d);
        if (entity.isLogicalSideForUpdatingMovement()) {
            entity.setMovementSpeed(entityAccess.zefiroptimizations$getSaddledSpeed(controllingPlayer));
            this.travel(vec3d);
        } else {
            entity.updateLimbs(false);
            entity.setVelocity(Vec3d.ZERO);
            entityAccess.zefiroptimizations$tryCheckBlockCollision();
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

    protected void lerpPosAndRotation(int step, double x, double y, double z, double yaw, double pitch) {
        double d = 1.0 / (double)step;
        double e = MathHelper.lerp(d, entity.getX(), x);
        double f = MathHelper.lerp(d, entity.getY(), y);
        double g = MathHelper.lerp(d, entity.getZ(), z);
        float h = (float) MathHelper.lerpAngleDegrees(d, entityAccess.zefiroptimizations$getYaw(), yaw);
        float i = (float) MathHelper.lerp(d, entityAccess.zefiroptimizations$getPitch(), pitch);
        entity.setPosition(e, f, g);
        this.setRotation(h, i);
    }

    public void setRotation(float yaw, float pitch) {
        entityAccess.zefiroptimizations$setYaw(yaw % 360.0F);
        entityAccess.zefiroptimizations$setPitch(pitch % 360.0F);
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

    public void travel(Vec3d movementInput) {
        if (entity.isLogicalSideForUpdatingMovement()) {
            double d = entity.getFinalGravity();
            boolean bl = entity.getVelocity().y <= 0.0;
            if (bl && entity.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
                d = Math.min(d, 0.01);
            }

            FluidState fluidState = entity.getWorld().getFluidState(entity.getBlockPos());
            if (entity.isTouchingWater() && entityAccess.zefiroptimizations$shouldSwimInFluids() && !entity.canWalkOnFluid(fluidState)) {
                double e = entity.getY();
                float f = entity.isSprinting() ? 0.9F : entityAccess.zefiroptimizations$getBaseMovementSpeedMultiplier();
                float g = 0.02F;
                float h = (float)entity.getAttributeValue(EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY);
                if (!entity.isOnGround()) {
                    h *= 0.5F;
                }

                if (h > 0.0F) {
                    f += (0.54600006F - f) * h;
                    g += (entity.getMovementSpeed() - g) * h;
                }

                if (entity.hasStatusEffect(StatusEffects.DOLPHINS_GRACE)) {
                    f = 0.96F;
                }

                entity.updateVelocity(g, movementInput);
                this.move(MovementType.SELF, entity.getVelocity());
                Vec3d vec3d = entity.getVelocity();
                if (entity.horizontalCollision && entity.isClimbing()) {
                    vec3d = new Vec3d(vec3d.x, 0.2, vec3d.z);
                }

                entity.setVelocity(vec3d.multiply(f, 0.8F, f));
                Vec3d vec3d2 = entity.applyFluidMovingSpeed(d, bl, entity.getVelocity());
                entity.setVelocity(vec3d2);
                if (entity.horizontalCollision && entity.doesNotCollide(vec3d2.x, vec3d2.y + 0.6F - entity.getY() + e, vec3d2.z)) {
                    entity.setVelocity(vec3d2.x, 0.3F, vec3d2.z);
                }
            } else if (entity.isInLava() && entityAccess.zefiroptimizations$shouldSwimInFluids() && !entity.canWalkOnFluid(fluidState)) {
                double ex = entity.getY();
                entity.updateVelocity(0.02F, movementInput);
                this.move(MovementType.SELF, entity.getVelocity());
                if (entity.getFluidHeight(FluidTags.LAVA) <= entity.getSwimHeight()) {
                    entity.setVelocity(entity.getVelocity().multiply(0.5, 0.8F, 0.5));
                    Vec3d vec3d3 = entity.applyFluidMovingSpeed(d, bl, entity.getVelocity());
                    entity.setVelocity(vec3d3);
                } else {
                    entity.setVelocity(entity.getVelocity().multiply(0.5));
                }

                if (d != 0.0) {
                    entity.setVelocity(entity.getVelocity().add(0.0, -d / 4.0, 0.0));
                }

                Vec3d vec3d3 = entity.getVelocity();
                if (entity.horizontalCollision && entity.doesNotCollide(vec3d3.x, vec3d3.y + 0.6F - entity.getY() + ex, vec3d3.z)) {
                    entity.setVelocity(vec3d3.x, 0.3F, vec3d3.z);
                }
            } else if (entity.isFallFlying()) {
                entity.limitFallDistance();
                Vec3d vec3d4 = entity.getVelocity();
                Vec3d vec3d5 = entity.getRotationVector();
                float fx = entity.getPitch() * (float) (Math.PI / 180.0);
                double i = Math.sqrt(vec3d5.x * vec3d5.x + vec3d5.z * vec3d5.z);
                double j = vec3d4.horizontalLength();
                double k = vec3d5.length();
                double l = Math.cos((double)fx);
                l = l * l * Math.min(1.0, k / 0.4);
                vec3d4 = entity.getVelocity().add(0.0, d * (-1.0 + l * 0.75), 0.0);
                if (vec3d4.y < 0.0 && i > 0.0) {
                    double m = vec3d4.y * -0.1 * l;
                    vec3d4 = vec3d4.add(vec3d5.x * m / i, m, vec3d5.z * m / i);
                }

                if (fx < 0.0F && i > 0.0) {
                    double m = j * (double)(-MathHelper.sin(fx)) * 0.04;
                    vec3d4 = vec3d4.add(-vec3d5.x * m / i, m * 3.2, -vec3d5.z * m / i);
                }

                if (i > 0.0) {
                    vec3d4 = vec3d4.add((vec3d5.x / i * j - vec3d4.x) * 0.1, 0.0, (vec3d5.z / i * j - vec3d4.z) * 0.1);
                }

                entity.setVelocity(vec3d4.multiply(0.99F, 0.98F, 0.99F));
                this.move(MovementType.SELF, entity.getVelocity());
                if (entity.horizontalCollision && !entity.getWorld().isClient) {
                    double m = entity.getVelocity().horizontalLength();
                    double n = j - m;
                    float o = (float)(n * 10.0 - 3.0);
                    if (o > 0.0F) {
                        entity.playSound(entityAccess.zefiroptimizations$getFallSound((int)o), 1.0F, 1.0F);
                        entity.damage(entity.getDamageSources().flyIntoWall(), o);
                    }
                }

                if (entity.isOnGround() && !entity.getWorld().isClient) {
                    entityAccess.zefiroptimizations$setFlag(entityAccess.zefiroptimizations$FALL_FLYING_FLAG_INDEX(), false);
                }
            } else {
                BlockPos blockPos = entity.getVelocityAffectingPos();
//                float p = entity.getWorld().getBlockState(blockPos).getBlock().getSlipperiness();
                float p = 0.6F;
//                System.out.println("p: " + p);
                float fxx = entity.isOnGround() ? p * 0.91F : 0.91F;
                Vec3d vec3d6 = this.applyMovementInput(movementInput, p);
                double q = vec3d6.y;
                if (entity.hasStatusEffect(StatusEffects.LEVITATION)) {
                    q += (0.05 * (double)(entity.getStatusEffect(StatusEffects.LEVITATION).getAmplifier() + 1) - vec3d6.y) * 0.2;
                } else if (!entity.getWorld().isClient || entity.getWorld().isChunkLoaded(blockPos)) {
                    q -= d;
                } else if (entity.getY() > (double)entity.getWorld().getBottomY()) {
                    q = -0.1;
                } else {
                    q = 0.0;
                }

                if (entity.hasNoDrag()) {
                    entity.setVelocity(vec3d6.x, q, vec3d6.z);
                } else {
                    entity.setVelocity(vec3d6.x * (double)fxx, this instanceof Flutterer ? q * (double)fxx : q * 0.98F, vec3d6.z * (double)fxx);
                }
            }
        }

        entity.updateLimbs(this instanceof Flutterer);
    }

    public Vec3d applyMovementInput(Vec3d movementInput, float slipperiness) {
        entity.updateVelocity(this.getMovementSpeed(slipperiness), movementInput);
        entity.setVelocity(this.applyClimbingSpeed(entity.getVelocity()));
        this.move(MovementType.SELF, entity.getVelocity());
        Vec3d vec3d = entity.getVelocity();
        if ((entity.horizontalCollision || entityAccess.zefiroptimizations$isJumping())
                && (entity.isClimbing() || entity.getBlockStateAtPos().isOf(Blocks.POWDER_SNOW) && PowderSnowBlock.canWalkOnPowderSnow(this.getEntity()))) {
            vec3d = new Vec3d(vec3d.x, 0.2, vec3d.z);
        }

        return vec3d;
    }

    private float getMovementSpeed(float slipperiness) {
        return entity.isOnGround() ? entity.getMovementSpeed() * (0.21600002F / (slipperiness * slipperiness * slipperiness)) : this.getOffGroundSpeed();
    }

    private Vec3d applyClimbingSpeed(Vec3d motion) {
        if (entity.isClimbing()) {
            entity.onLanding();
            float f = 0.15F;
            double d = MathHelper.clamp(motion.x, -0.15F, 0.15F);
            double e = MathHelper.clamp(motion.z, -0.15F, 0.15F);
            double g = Math.max(motion.y, -0.15F);
            if (g < 0.0 && !entity.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING) && entity.isHoldingOntoLadder() && entity instanceof PlayerEntity) {
                g = 0.0;
            }

            motion = new Vec3d(d, g, e);
        }

        return motion;
    }

    protected float getOffGroundSpeed() {
        return entity.getControllingPassenger() instanceof PlayerEntity ? entity.getMovementSpeed() * 0.1F : 0.02F;
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


    protected void move(MovementType movementType, Vec3d movement) {
        if (entity.noClip) {
            entity.setPosition(entity.getX() + movement.x, entity.getY() + movement.y, entity.getZ() + movement.z);
        } else {
            entity.wasOnFire = entity.isOnFire();
            if (movementType == MovementType.PISTON) {
                movement = this.adjustMovementForPiston(movement);
                if (movement.equals(Vec3d.ZERO)) {
                    return;
                }
            }

            entity.getWorld().getProfiler().push("move");
            if (entityAccess.zefiroptimizations$getMovementMultiplier().lengthSquared() > 1.0E-7) {
                movement = movement.multiply(entityAccess.zefiroptimizations$getMovementMultiplier());
                entityAccess.zefiroptimizations$setMovementMultiplier(Vec3d.ZERO);
                entity.setVelocity(Vec3d.ZERO);
            }

            movement = entityAccess.zefiroptimizations$adjustMovementForSneaking(movement, movementType);
            Vec3d vec3d = this.adjustMovementForCollisions(movement);
            double d = vec3d.lengthSquared();
            if (d > 1.0E-7) {
                if (entity.fallDistance != 0.0F && d >= 1.0) {
                    BlockHitResult blockHitResult = entity.getWorld()
                            .raycast(
                                    new RaycastContext(entity.getPos(), entity.getPos().add(vec3d), RaycastContext.ShapeType.FALLDAMAGE_RESETTING, RaycastContext.FluidHandling.WATER, entity)
                            );
                    if (blockHitResult.getType() != HitResult.Type.MISS) {
                        entity.onLanding();
                    }
                }

                entity.setPosition(entity.getX() + vec3d.x, entity.getY() + vec3d.y, entity.getZ() + vec3d.z);
            }

            entity.getWorld().getProfiler().pop();
            entity.getWorld().getProfiler().push("rest");
            boolean bl = !MathHelper.approximatelyEquals(movement.x, vec3d.x);
            boolean bl2 = !MathHelper.approximatelyEquals(movement.z, vec3d.z);
            entity.horizontalCollision = bl || bl2;
            entity.verticalCollision = movement.y != vec3d.y;
            entity.groundCollision = entity.verticalCollision && movement.y < 0.0;
            if (entity.horizontalCollision) {
                entity.collidedSoftly = entityAccess.zefiroptimizations$hasCollidedSoftly(vec3d);
            } else {
                entity.collidedSoftly = false;
            }

            entity.setOnGround(entity.groundCollision, vec3d);
            BlockPos blockPos = entity.getLandingPos();
            BlockState blockState = entity.getWorld().getBlockState(blockPos);
            entityAccess.zefiroptimizations$fall(vec3d.y, entity.isOnGround(), blockState, blockPos);
            if (entity.isRemoved()) {
                entity.getWorld().getProfiler().pop();
            } else {
                if (entity.horizontalCollision) {
                    Vec3d vec3d2 = entity.getVelocity();
                    entity.setVelocity(bl ? 0.0 : vec3d2.x, vec3d2.y, bl2 ? 0.0 : vec3d2.z);
                }

                Block block = blockState.getBlock();
                if (movement.y != vec3d.y) {
                    block.onEntityLand(entity.getWorld(), entity);
                }

                if (entity.isOnGround()) {
                    block.onSteppedOn(entity.getWorld(), blockPos, blockState, entity);
                }

                Entity.MoveEffect moveEffect = entityAccess.zefiroptimizations$getMoveEffect();
                if (moveEffect.hasAny() && !entity.hasVehicle()) {
                    double e = vec3d.x;
                    double f = vec3d.y;
                    double g = vec3d.z;
                    entity.speed = entity.speed + (float)(vec3d.length() * 0.6);
                    BlockPos blockPos2 = entity.getSteppingPos();
                    BlockState blockState2 = entity.getWorld().getBlockState(blockPos2);
                    boolean bl3 = entityAccess.zefiroptimizations$canClimb(blockState2);
                    if (!bl3) {
                        f = 0.0;
                    }

                    entity.horizontalSpeed = entity.horizontalSpeed + (float)vec3d.horizontalLength() * 0.6F;
                    entity.distanceTraveled = entity.distanceTraveled + (float)Math.sqrt(e * e + f * f + g * g) * 0.6F;
                    if (entity.distanceTraveled > entityAccess.zefiroptimizations$getNextStepSoundDistance() && !blockState2.isAir()) {
                        boolean bl4 = blockPos2.equals(blockPos);
                        boolean bl5 = this.stepOnBlock(blockPos, blockState, moveEffect.playsSounds(), bl4, movement);
                        if (!bl4) {
                            bl5 |= this.stepOnBlock(blockPos2, blockState2, false, moveEffect.emitsGameEvents(), movement);
                        }

                        if (bl5) {
                            entityAccess.zefiroptimizations$setNextStepSoundDistance(entityAccess.zefiroptimizations$calculateNextStepSoundDistance());
                        } else if (entity.isTouchingWater()) {
                            entityAccess.zefiroptimizations$setNextStepSoundDistance(entityAccess.zefiroptimizations$calculateNextStepSoundDistance());
                            if (moveEffect.playsSounds()) {
                                entityAccess.zefiroptimizations$playSwimSound();
                            }

                            if (moveEffect.emitsGameEvents()) {
                                entity.emitGameEvent(GameEvent.SWIM);
                            }
                        }
                    } else if (blockState2.isAir()) {
                        entityAccess.zefiroptimizations$addAirTravelEffects();
                    }
                }

                entityAccess.zefiroptimizations$tryCheckBlockCollision();
                float h = entityAccess.zefiroptimizations$getVelocityMultiplier();
                entity.setVelocity(entity.getVelocity().multiply((double)h, 1.0, (double)h));
                if (entity.getWorld()
                        .getStatesInBoxIfLoaded(entity.getBoundingBox().contract(1.0E-6))
                        .noneMatch(state -> state.isIn(BlockTags.FIRE) || state.isOf(Blocks.LAVA))) {
                    if (entityAccess.zefiroptimizations$getFireTicks() <= 0) {
                        entity.setFireTicks(-entityAccess.zefiroptimizations$getBurningDuration());
                    }

                    if (entity.wasOnFire && (entity.inPowderSnow || entity.isWet())) {
                        entityAccess.zefiroptimizations$playExtinguishSound();
                    }
                }

                if (entity.isOnFire() && (entity.inPowderSnow || entity.isWet())) {
                    entity.setFireTicks(-entityAccess.zefiroptimizations$getBurningDuration());
                }

                entity.getWorld().getProfiler().pop();
            }
        }
    }

    private Vec3d adjustMovementForCollisions(Vec3d movement) {
        Box box = entity.getBoundingBox();
        List<VoxelShape> list = entity.getWorld().getEntityCollisions(entity, box.stretch(movement));
        Vec3d vec3d = movement.lengthSquared() == 0.0 ? movement : Entity.adjustMovementForCollisions(entity, movement, box, entity.getWorld(), list);
        boolean bl = movement.x != vec3d.x;
        boolean bl2 = movement.y != vec3d.y;
        boolean bl3 = movement.z != vec3d.z;
        boolean bl4 = bl2 && movement.y < 0.0;
        if (entity.getStepHeight() > 0.0F && (bl4 || entity.isOnGround()) && (bl || bl3)) {
            Box box2 = bl4 ? box.offset(0.0, vec3d.y, 0.0) : box;
            Box box3 = box2.stretch(movement.x, entity.getStepHeight(), movement.z);
            if (!bl4) {
                box3 = box3.stretch(0.0, -1.0E-5F, 0.0);
            }

            List<VoxelShape> list2 = findCollisionsForMovement(entity, entity.getWorld(), list, box3);
            float f = (float)vec3d.y;
            float[] fs = collectStepHeights(box2, list2, entity.getStepHeight(), f);

            for (float g : fs) {
                Vec3d vec3d2 = adjustMovementForCollisions(new Vec3d(movement.x, g, movement.z), box2, list2);
                if (vec3d2.horizontalLengthSquared() > vec3d.horizontalLengthSquared()) {
                    double d = box.minY - box2.minY;
                    return vec3d2.add(0.0, -d, 0.0);
                }
            }
        }

        return vec3d;
    }

    private static List<VoxelShape> findCollisionsForMovement(
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

    private static float[] collectStepHeights(Box collisionBox, List<VoxelShape> collisions, float f, float stepHeight) {
        FloatSet floatSet = new FloatArraySet(4);

        for (VoxelShape voxelShape : collisions) {
            for (double d : voxelShape.getPointPositions(Direction.Axis.Y)) {
                float g = (float)(d - collisionBox.minY);
                if (!(g < 0.0F) && g != stepHeight) {
                    if (g > f) {
                        break;
                    }

                    floatSet.add(g);
                }
            }
        }

        float[] fs = floatSet.toFloatArray();
        FloatArrays.unstableSort(fs);
        return fs;
    }

    private static Vec3d adjustMovementForCollisions(Vec3d movement, Box entityBoundingBox, List<VoxelShape> collisions) {
        if (collisions.isEmpty()) {
            return movement;
        } else {
            double d = movement.x;
            double e = movement.y;
            double f = movement.z;
            if (e != 0.0) {
                e = VoxelShapes.calculateMaxOffset(Direction.Axis.Y, entityBoundingBox, collisions, e);
                if (e != 0.0) {
                    entityBoundingBox = entityBoundingBox.offset(0.0, e, 0.0);
                }
            }

            boolean bl = Math.abs(d) < Math.abs(f);
            if (bl && f != 0.0) {
                f = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, entityBoundingBox, collisions, f);
                if (f != 0.0) {
                    entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, f);
                }
            }

            if (d != 0.0) {
                d = VoxelShapes.calculateMaxOffset(Direction.Axis.X, entityBoundingBox, collisions, d);
                if (!bl && d != 0.0) {
                    entityBoundingBox = entityBoundingBox.offset(d, 0.0, 0.0);
                }
            }

            if (!bl && f != 0.0) {
                f = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, entityBoundingBox, collisions, f);
            }

            return new Vec3d(d, e, f);
        }
    }

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
                return Math.abs(d) <= 1.0E-5F ? Vec3d.ZERO : new Vec3d(d, 0.0, 0.0);
            } else if (movement.y != 0.0) {
                double d = this.calculatePistonMovementFactor(Direction.Axis.Y, movement.y);
                return Math.abs(d) <= 1.0E-5F ? Vec3d.ZERO : new Vec3d(0.0, d, 0.0);
            } else if (movement.z != 0.0) {
                double d = this.calculatePistonMovementFactor(Direction.Axis.Z, movement.z);
                return Math.abs(d) <= 1.0E-5F ? Vec3d.ZERO : new Vec3d(0.0, 0.0, d);
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
}
