package ua.zefir.zefiroptimizations.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.dimension.PortalManager;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.mixin.LivingEntityAccessor;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Getter
@Setter
public class EntityActor extends AbstractActor {
    protected final LivingEntity entity;
    protected final IAsyncLivingEntityAccess entityAccess;
    protected final LivingEntityAccessor newEntityAccess;
    protected final ThreadLocalRandom random = ThreadLocalRandom.current();
    private boolean firstTimeIterating = true;
    private EntitySnapshot lastSnapshot;

    public EntityActor(LivingEntity entity) {
        this.entity = entity;
        this.entityAccess = (IAsyncLivingEntityAccess) entity;
        this.newEntityAccess = (LivingEntityAccessor) entity;
    }

    public static Props props(LivingEntity entity) {
        return Props.create(EntityActor.class, entity);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ZefirsActorMessages.AsyncTick.class, this::handleAsyncTick)
                .match(ZefirsActorMessages.ContinueTickMovement.class, this::continueTickMovement)
                .match(ZefirsActorMessages.InvokeMove.class, this::move)
                .build();
    }

    protected void handleAsyncTick(ZefirsActorMessages.AsyncTick msg) {
            if (!entity.isRemoved()) {
                this.tickMobEntityMovement();
//                this.lastSnapshot = createEntitySnapshot();
//                lastSnapshot.tickMovement();
//                ZefirOptimizations.getMainThreadActor().tell(
//                        new ZefirsActorMessages.ApplyPositionAndRotationDiff(entity, this.lastSnapshot),
//                        getSelf()
//                );
            }
    }

    public void tickMobEntityMovement() {
        tickMovement();
    }

    protected void tickMovement() {
        // Create a copy of the entity's relevant data
        this.lastSnapshot = createEntitySnapshot();
        LivingEntityAccessor newEntitySnapshotAccess = (LivingEntityAccessor) this.lastSnapshot;

        if(firstTimeIterating) {
            System.out.println("this.lastSnapshot.pos 1: " + this.lastSnapshot.pos);
        }

        if (this.lastSnapshot.jumpingCooldown > 0) {
            this.lastSnapshot.jumpingCooldown = this.lastSnapshot.jumpingCooldown - 1;
        }

        if (this.lastSnapshot.isLogicalSideForUpdatingMovement()) {
            this.lastSnapshot.bodyTrackingIncrements = 0;
            this.lastSnapshot.updateTrackedPosition(this.lastSnapshot.getX(), this.lastSnapshot.getY(), this.lastSnapshot.getZ());
        }

        if (this.lastSnapshot.bodyTrackingIncrements > 0) {
            newEntitySnapshotAccess.invokeLerpPosAndRotation(this.lastSnapshot.bodyTrackingIncrements, this.lastSnapshot.serverX, this.lastSnapshot.serverY, this.lastSnapshot.serverZ, this.lastSnapshot.serverYaw, this.lastSnapshot.serverPitch);
            this.lastSnapshot.headTrackingIncrements = this.lastSnapshot.getBodyTrackingIncrements() - 1;
        } else if (!this.lastSnapshot.canMoveVoluntarily()) {
            this.lastSnapshot.setVelocity(this.lastSnapshot.velocity.multiply(0.98));
        }

        if (this.lastSnapshot.headTrackingIncrements > 0) {
            newEntitySnapshotAccess.invokeLerpHeadYaw(this.lastSnapshot.headTrackingIncrements, this.lastSnapshot.serverHeadYaw);
            this.lastSnapshot.headTrackingIncrements = this.lastSnapshot.headTrackingIncrements - 1;
        }

        Vec3d vec3d = this.lastSnapshot.velocity;
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

        this.lastSnapshot.setVelocity(d, e, f);
        this.lastSnapshot.getWorld().getProfiler().push("ai");
        if(firstTimeIterating) {
            System.out.println("this.lastSnapshot.pos 2: " + this.lastSnapshot.pos);
        }
        if (newEntitySnapshotAccess.invokeIsImmobile()) {
            this.lastSnapshot.setJumping(false);
            this.lastSnapshot.sidewaysSpeed = 0.0F;
            this.lastSnapshot.forwardSpeed = 0.0F;
        } else
            if (this.lastSnapshot.canMoveVoluntarily()) {
                if(firstTimeIterating) {
                    System.out.println("Entity Pos1: " + this.entity.getPos());
                }
                ZefirOptimizations.getMainThreadActor().tell(
                        new ZefirsActorMessages.ApplyPositionAndRotationDiff(entity, this.lastSnapshot),
                        getSelf()
                );
                if(firstTimeIterating) {
                    System.out.println("Entity Pos2: " + this.entity.getPos());
                }
                ZefirOptimizations.getMainThreadActor().tell(
                        new ZefirsActorMessages.TickNewAiAndContinue(getSelf(), entity),
                        getSelf()
                );
                if(firstTimeIterating) {
                    System.out.println("Entity Pos3: " + this.entity.getPos());
                }
            return;
        }
//        if(firstTimeIterating) {
//            System.out.println("this.lastSnapshot.pos 2: " + this.lastSnapshot.pos);
//        }
        this.getSelf().tell(new ZefirsActorMessages.ContinueTickMovement(), getSelf());
    }

    protected void continueTickMovement(ZefirsActorMessages.ContinueTickMovement msg) {
        if (entity.isRemoved()) {
            return;
        }

        this.lastSnapshot = createEntitySnapshot();
        if(firstTimeIterating) {
            System.out.println("Entity Pos: " + this.entity.getPos());
        }

        if(firstTimeIterating) {
            System.out.println("this.lastSnapshot.pos 3: " + this.lastSnapshot.pos);
        }

        LivingEntityAccessor newEntitySnapshotAccess = (LivingEntityAccessor) this.lastSnapshot;
//        IAsyncLivingEntityAccess oldEntitySnapshotAccess = (IAsyncLivingEntityAccess) this.lastSnapshot;

        this.lastSnapshot.getWorld().getProfiler().pop();
        this.lastSnapshot.getWorld().getProfiler().push("jump");
        if (this.lastSnapshot.isJumping() && newEntitySnapshotAccess.invokeShouldSwimInFluids()) {
            double g;
            if (this.lastSnapshot.isInLava()) {
                g = this.lastSnapshot.getFluidHeight(FluidTags.LAVA);
            } else {
                g = this.lastSnapshot.getFluidHeight(FluidTags.WATER);
            }

            boolean bl = this.lastSnapshot.isTouchingWater() && g > 0.0;
            double h = this.lastSnapshot.getSwimHeight();
            if (!bl || this.lastSnapshot.isOnGround() && !(g > h)) {
                if (!this.lastSnapshot.isInLava() || this.lastSnapshot.isOnGround() && !(g > h)) {
                    if ((this.lastSnapshot.isOnGround() || bl && g <= h) && this.lastSnapshot.getJumpingCooldown() == 0) {
                        this.lastSnapshot.jump();
                        this.lastSnapshot.setJumpingCooldown(10);
                    }
                } else {
                    newEntitySnapshotAccess.invokeSwimUpward(FluidTags.LAVA);
                }
            } else {
                newEntitySnapshotAccess.invokeSwimUpward(FluidTags.WATER);
            }
        } else {
            this.lastSnapshot.setJumpingCooldown(0);
        }
        if(firstTimeIterating) {
            System.out.println("this.lastSnapshot.pos 4: " + this.lastSnapshot.pos);
        }

        this.lastSnapshot.getWorld().getProfiler().pop();
        this.lastSnapshot.getWorld().getProfiler().push("travel");
        this.lastSnapshot.sidewaysSpeed *= 0.98F;
        this.lastSnapshot.forwardSpeed *= 0.98F;
        newEntitySnapshotAccess.invokeTickFallFlying();
        Box box = this.lastSnapshot.getBoundingBox();
        Vec3d vec3d2 = new Vec3d(this.lastSnapshot.sidewaysSpeed, this.lastSnapshot.upwardSpeed, this.lastSnapshot.forwardSpeed);
        if (this.lastSnapshot.hasStatusEffect(StatusEffects.SLOW_FALLING) || this.lastSnapshot.hasStatusEffect(StatusEffects.LEVITATION)) {
            this.lastSnapshot.onLanding();
        }

        label104: {
            if (this.lastSnapshot.getControllingPassenger() instanceof PlayerEntity playerEntity && this.lastSnapshot.isAlive()) {
                newEntitySnapshotAccess.invokeTravelControlled(playerEntity, vec3d2);
                break label104;
            }

            this.lastSnapshot.travel(vec3d2);
        }

        this.lastSnapshot.getWorld().getProfiler().pop();
        this.lastSnapshot.getWorld().getProfiler().push("freezing");
        if (!this.lastSnapshot.getWorld().isClient && !this.lastSnapshot.isDead()) {
            int i = this.lastSnapshot.getFrozenTicks();
            if (this.lastSnapshot.inPowderSnow && this.lastSnapshot.canFreeze()) {
                this.lastSnapshot.setFrozenTicks(Math.min(this.lastSnapshot.getMinFreezeDamageTicks(), i + 1));
            } else {
                this.lastSnapshot.setFrozenTicks(Math.max(0, i - 2));
            }
        }
        if(firstTimeIterating) {
            System.out.println("this.lastSnapshot.pos 5: " + this.lastSnapshot.pos);
        }

        newEntitySnapshotAccess.invokeRemovePowderSnowSlow();
        newEntitySnapshotAccess.invokeAddPowderSnowSlowIfNeeded();
        if (!this.lastSnapshot.getWorld().isClient && this.lastSnapshot.age % 40 == 0 && this.lastSnapshot.isFrozen() && this.lastSnapshot.canFreeze()) {
//            entity.damage(entity.getDamageSources().freeze(), 1.0F);
            ZefirOptimizations.getMainThreadActor().tell(
                    new ZefirsActorMessages.ApplyDamage(entity, this.lastSnapshot.getDamageSources().freeze(), 1.0F),
                    getSelf()
            );
        }

        this.lastSnapshot.getWorld().getProfiler().pop();
        this.lastSnapshot.getWorld().getProfiler().push("push");
        if (this.lastSnapshot.getRiptideTicks() > 0) {
            this.lastSnapshot.setRiptideTicks(this.lastSnapshot.getRiptideTicks() - 1);
            this.tickRiptide(box, this.lastSnapshot.getBoundingBox());
        }
        if(firstTimeIterating) {
            System.out.println("this.lastSnapshot.pos 6: " + this.lastSnapshot.pos);
        }

        this.tickCramming();
        this.lastSnapshot.getWorld().getProfiler().pop();
        if (!this.lastSnapshot.getWorld().isClient && this.lastSnapshot.hurtByWater() && this.lastSnapshot.isWet()) {
            ZefirOptimizations.getMainThreadActor().tell(
                    new ZefirsActorMessages.ApplyDamage(entity, entity.getDamageSources().drown(), 1.0F),
                    getSelf()
            );
        }
        ZefirOptimizations.getMainThreadActor().tell(
                new ZefirsActorMessages.ApplyPositionAndRotationDiff(entity, this.lastSnapshot),
                getSelf()
        );
        if(firstTimeIterating) {
            System.out.println("this.lastSnapshot.pos 7: " + this.lastSnapshot.pos);
        }
        firstTimeIterating = false;
    }

    private EntitySnapshot createEntitySnapshot() {
        return new EntitySnapshot(
                this.getSelf(),
                entity.getPos(),
                entity.isOnGround(),
                newEntityAccess.isForceUpdateSupportingBlockPos(),
                newEntityAccess.getBodyYaw(),
                newEntityAccess.getPrevBodyYaw(),
                newEntityAccess.getHeadYaw(),
                newEntityAccess.getPrevHeadYaw(),
                newEntityAccess.getLastAttackedTicks(),
                newEntityAccess.getHandSwingProgress(),
                newEntityAccess.getLastHandSwingProgress(),
                newEntityAccess.getHandSwingTicks(),
                newEntityAccess.getHandSwinging(),
                newEntityAccess.getSyncedBodyArmorStack(),
                newEntityAccess.getNoDrag(),
                newEntityAccess.getPreferredHand(),
                newEntityAccess.getStuckArrowTimer(),
                newEntityAccess.getStuckStingerTimer(),
                newEntityAccess.getHurtTime(),
                newEntityAccess.getMaxHurtTime(),
                newEntityAccess.getDeathTime(),
                newEntityAccess.getAttackingPlayer(),
                newEntityAccess.getPlayerHitTimer(),
                newEntityAccess.getDead(),
                newEntityAccess.getDespawnCounter(),
                newEntityAccess.getPrevStepBobbingAmount(),
                newEntityAccess.getStepBobbingAmount(),
                newEntityAccess.getLookDirection(),
                newEntityAccess.getPrevLookDirection(),
                newEntityAccess.getField_6215(),
                newEntityAccess.getScoreAmount(),
                newEntityAccess.getLastDamageTaken(),
                newEntityAccess.getJumping(),
                newEntityAccess.getSidewaysSpeed(),
                newEntityAccess.getUpwardSpeed(),
                newEntityAccess.getForwardSpeed(),
                newEntityAccess.getBodyTrackingIncrements(),
                newEntityAccess.getServerX(),
                newEntityAccess.getServerY(),
                newEntityAccess.getServerZ(),
                newEntityAccess.getServerYaw(),
                newEntityAccess.getServerPitch(),
                newEntityAccess.getServerHeadYaw(),
                newEntityAccess.getHeadTrackingIncrements(),
                newEntityAccess.getEffectsChanged(),
                newEntityAccess.getAttacker(),
                newEntityAccess.getLastAttackedTime(),
                newEntityAccess.getAttacking(),
                newEntityAccess.getLastAttackTime(),
                newEntityAccess.getMovementSpeed(),
                newEntityAccess.getJumpingCooldown(),
                newEntityAccess.getAbsorptionAmount(),
                newEntityAccess.getActiveItemStack(),
                newEntityAccess.getItemUseTimeLeft(),
                newEntityAccess.getFallFlyingTicks(),
                newEntityAccess.getLastBlockPos(),
                newEntityAccess.getClimbingPos(),
                newEntityAccess.getLastDamageSource(),
                newEntityAccess.getLastDamageTime(),
                newEntityAccess.getRiptideTicks(),
                newEntityAccess.getRiptideAttackDamage(),
                newEntityAccess.getRiptideStack(),
                newEntityAccess.getLeaningPitch(),
                newEntityAccess.getLastLeaningPitch(),
                newEntityAccess.getBrain(),
                newEntityAccess.getExperienceDroppingDisabled(),
                // locationBasedEnchantmentEffects is missing from accessors and constructor. Add it if needed. Example: ((LivingEntity)(Object)newEntityAccess).getEnchantments()
//                null,
                newEntityAccess.getPrevScale(),
                newEntityAccess.getId(),
                newEntityAccess.isIntersectionChecked(),
                newEntityAccess.getPassengerList(),
                newEntityAccess.getRidingCooldown(),
                newEntityAccess.getVehicle(),
                entity.getWorld(),
                newEntityAccess.getPrevX(),
                newEntityAccess.getPrevY(),
                newEntityAccess.getPrevZ(),
                entity.getBlockPos(),
                entity.getChunkPos(),
                entity.getVelocity(),
                entity.getYaw(),
                entity.getPitch(),
                newEntityAccess.getPrevYaw(),
                newEntityAccess.getPrevPitch(),
                entity.getBoundingBox(),
                newEntityAccess.isHorizontalCollision(),
                newEntityAccess.isVerticalCollision(),
                newEntityAccess.isGroundCollision(),
                newEntityAccess.hasCollidedSoftly(),
                newEntityAccess.isVelocityModified(),
                newEntityAccess.getMovementMultiplier(),
                newEntityAccess.getRemovalReason(),
                newEntityAccess.getPrevHorizontalSpeed(),
                newEntityAccess.getHorizontalSpeed(),
                newEntityAccess.getDistanceTraveled(),
                newEntityAccess.getSpeed(),
                newEntityAccess.getFallDistance(),
                newEntityAccess.getNextStepSoundDistance(),
                newEntityAccess.getLastRenderX(),
                newEntityAccess.getLastRenderY(),
                newEntityAccess.getLastRenderZ(),
                newEntityAccess.isNoClip(),
                newEntityAccess.getAge(),
                entity.getFireTicks(),
                entity.isTouchingWater(),
                newEntityAccess.getFluidHeight(),
                newEntityAccess.isSubmergedInWater(),
                newEntityAccess.getTimeUntilRegen(),
                newEntityAccess.isFirstUpdate(),
                newEntityAccess.ignoresCameraFrustum(),
                newEntityAccess.isVelocityDirty(),
                newEntityAccess.getPortalManager(),
                newEntityAccess.getPortalCooldown(),
                newEntityAccess.isInvulnerable(),
                newEntityAccess.getUuid(),
                newEntityAccess.getUuidString(),
                newEntityAccess.isGlowing(),
                newEntityAccess.getPistonMovementTick(),
                newEntityAccess.getDimensions(),
                newEntityAccess.getStandingEyeHeight(),
                newEntityAccess.isInPowderSnow(),
                newEntityAccess.wasInPowderSnow(),
                newEntityAccess.wasOnFire(),
                newEntityAccess.getSupportingBlockPos(),
                newEntityAccess.getLastChimeAge(),
                newEntityAccess.getLastChimeIntensity(),
                newEntityAccess.hasVisualFire(),
                newEntityAccess.getStateAtPos(),
                newEntityAccess.getDataTracker()
        );
    }

//    protected void invokeSwimUpward(TagKey<Fluid> fluid) {
//        this.lastSnapshot.setVelocity(entity.getVelocity().add(0.0, 0.04F, 0.0));
//    }

    protected void tickCramming() {
        LivingEntityAccessor newEntitySnapshotAccess = (LivingEntityAccessor) this.lastSnapshot;

        if (this.lastSnapshot.getWorld().isClient()) {
            this.lastSnapshot.getWorld()
                    .getEntitiesByType(EntityType.PLAYER, this.lastSnapshot.getBoundingBox(), EntityPredicates.canBePushedBy(this.lastSnapshot))
                    .forEach(newEntitySnapshotAccess::invokePushAway);
        } else {
            List<Entity> list = this.lastSnapshot.getWorld().getOtherEntities(this.lastSnapshot, this.lastSnapshot.getBoundingBox(), EntityPredicates.canBePushedBy(this.lastSnapshot));

            if (!list.isEmpty()) {
                int maxEntityCramming = this.lastSnapshot.getWorld().getGameRules().getInt(GameRules.MAX_ENTITY_CRAMMING);
                if (maxEntityCramming > 0 && list.size() > maxEntityCramming - 1 && random.nextInt(4) == 0) {
                    int nonVehicleEntities = 0;
                    for (Entity entity : list) {
                        if (!entity.hasVehicle()) {
                            nonVehicleEntities++;
                        }
                    }

                    if (nonVehicleEntities > maxEntityCramming - 1) {
                        ZefirOptimizations.getMainThreadActor().tell(
                                new ZefirsActorMessages.ApplyDamage(this.lastSnapshot, this.lastSnapshot.getDamageSources().cramming(), 6.0F),
                                getSelf()
                        );
                    }
                }

                // Create a new list to store entities to be pushed
                List<Entity> entitiesToPush = new ArrayList<>(list);

                // Push entities away after the loop
                for (Entity entity2 : entitiesToPush) {
                    newEntitySnapshotAccess.invokePushAway(entity2);
                }
            }
        }
    }

    protected void tickRiptide(Box a, Box b) {
        LivingEntityAccessor newEntitySnapshotAccess = (LivingEntityAccessor) this.lastSnapshot;

        Box box = a.union(b);
        List<Entity> list = this.lastSnapshot.getWorld().getOtherEntities(this.lastSnapshot, box);
        if (!list.isEmpty()) {
            for (Entity entity : list) {
                if (entity instanceof LivingEntity) {
                    newEntitySnapshotAccess.invokeAttackLivingEntity((LivingEntity)entity);
                    this.lastSnapshot.setRiptideTicks(0);
                    entity.setVelocity(entity.getVelocity().multiply(-0.2));
                    break;
                }
            }
        } else if (this.lastSnapshot.horizontalCollision) {
            this.lastSnapshot.setRiptideTicks(0);
        }

        if (!this.lastSnapshot.getWorld().isClient && this.lastSnapshot.getRiptideTicks() <= 0) {
            newEntitySnapshotAccess.invokeSetLivingFlag(newEntitySnapshotAccess.getUSING_RIPTIDE_FLAG(), false);
            this.lastSnapshot.setRiptideAttackDamage(0.0F);
            this.lastSnapshot.setRiptideStack(null);
        }
    }

//    private void invokeTravelControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
//        Vec3d vec3d = this.lastSnapshot.invokeGetControlledMovementInput(controllingPlayer, movementInput);
//        this.lastSnapshot.tickControlled(controllingPlayer, vec3d);
//        if (this.lastSnapshot.isLogicalSideForUpdatingMovement()) {
//            this.lastSnapshot.setMovementSpeed(this.lastSnapshot.getSaddledSpeed(controllingPlayer));
//            this.lastSnapshot.travel(vec3d);
//        } else {
//            this.lastSnapshot.updateLimbs(false);
//            this.lastSnapshot.setVelocity(Vec3d.ZERO);
//            this.lastSnapshot.tryCheckBlockCollision();
//        }
//    }

//    private void invokeLerpHeadYaw(int headTrackingIncrements, double serverHeadYaw) {
//        entityAccess.zefiroptimizations$setYaw(
//                (float) MathHelper.lerpAngleDegrees(
//                        1.0 / (double) headTrackingIncrements,
//                        (double) entityAccess.zefiroptimizations$getYaw(), serverHeadYaw
//                )
//        );
//    }

//    protected void invokeLerpPosAndRotation(int step, double x, double y, double z, double yaw, double pitch) {
//        double d = 1.0 / (double)step;
//        double e = MathHelper.lerp(d, entity.getX(), x);
//        double f = MathHelper.lerp(d, entity.getY(), y);
//        double g = MathHelper.lerp(d, entity.getZ(), z);
//        float h = (float) MathHelper.lerpAngleDegrees(d, entityAccess.zefiroptimizations$getYaw(), yaw);
//        float i = (float) MathHelper.lerp(d, entityAccess.zefiroptimizations$getPitch(), pitch);
//        entity.setPosition(e, f, g);
//        this.setRotation(h, i);
//    }
//
//    public void setRotation(float yaw, float pitch) {
//        entityAccess.zefiroptimizations$setYaw(yaw % 360.0F);
//        entityAccess.zefiroptimizations$setPitch(pitch % 360.0F);
//    }

//    private void invokeTickFallFlying() {
//        boolean bl = entityAccess.zefiroptimizations$getFlag(
//                entityAccess.zefiroptimizations$FALL_FLYING_FLAG_INDEX()
//        );
//        if (bl && !entityAccess.zefiroptimizations$isOnGround() && !entity.hasVehicle()
//                && !entity.hasStatusEffect(StatusEffects.LEVITATION)) {
//            ItemStack itemStack = entity.getEquippedStack(EquipmentSlot.CHEST);
//            if (itemStack.isOf(Items.ELYTRA) && ElytraItem.isUsable(itemStack)) {
//                bl = true;
//                int i = entityAccess.zefiroptimizations$getFallFlyingTicks() + 1;
//                if (!entity.getWorld().isClient && i % 10 == 0) {
//                    int j = i / 10;
//                    if (j % 2 == 0) {
//                        itemStack.damage(1, entity, EquipmentSlot.CHEST);
//                    }
//
//                    entity.emitGameEvent(GameEvent.ELYTRA_GLIDE);
//                }
//            } else {
//                bl = false;
//            }
//        } else {
//            bl = false;
//        }
//
//        if (!entity.getWorld().isClient) {
//            entityAccess.zefiroptimizations$setFlag(
//                    entityAccess.zefiroptimizations$FALL_FLYING_FLAG_INDEX(), bl
//            );
//        }
//    }

//    public void travel(Vec3d movementInput) {
//        if (this.lastSnapshot.isLogicalSideForUpdatingMovement()) {
//            double d = this.lastSnapshot.getFinalGravity();
//            boolean bl = this.lastSnapshot.getVelocity().y <= 0.0;
//            if (bl && this.lastSnapshot.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
//                d = Math.min(d, 0.01);
//            }
//
//            FluidState fluidState = this.lastSnapshot.getWorld().getFluidState(this.lastSnapshot.getBlockPos());
//            if (this.lastSnapshot.isTouchingWater() && this.lastSnapshot.invokeShouldSwimInFluids() && !this.lastSnapshot.canWalkOnFluid(fluidState)) {
//                double e = this.lastSnapshot.getY();
//                float f = this.lastSnapshot.isSprinting() ? 0.9F : this.lastSnapshot.getBaseMovementSpeedMultiplier();
//                float g = 0.02F;
//                float h = (float)this.lastSnapshot.getAttributeValue(EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY);
//                if (!this.lastSnapshot.isOnGround()) {
//                    h *= 0.5F;
//                }
//
//                if (h > 0.0F) {
//                    f += (0.54600006F - f) * h;
//                    g += (this.lastSnapshot.getMovementSpeed() - g) * h;
//                }
//
//                if (this.lastSnapshot.hasStatusEffect(StatusEffects.DOLPHINS_GRACE)) {
//                    f = 0.96F;
//                }
//
//                this.lastSnapshot.updateVelocity(g, movementInput);
//                this.move(MovementType.SELF, this.lastSnapshot.getVelocity());
//                Vec3d vec3d = this.lastSnapshot.getVelocity();
//                if (this.lastSnapshot.horizontalCollision && this.lastSnapshot.isClimbing()) {
//                    vec3d = new Vec3d(vec3d.x, 0.2, vec3d.z);
//                }
//
//                this.lastSnapshot.setVelocity(vec3d.multiply(f, 0.8F, f));
//                Vec3d vec3d2 = this.lastSnapshot.applyFluidMovingSpeed(d, bl, this.lastSnapshot.getVelocity());
//                this.lastSnapshot.setVelocity(vec3d2);
//                if (this.lastSnapshot.horizontalCollision && this.lastSnapshot.doesNotCollide(vec3d2.x, vec3d2.y + 0.6F - this.lastSnapshot.getY() + e, vec3d2.z)) {
//                    this.lastSnapshot.setVelocity(vec3d2.x, 0.3F, vec3d2.z);
//                }
//            } else if (this.lastSnapshot.isInLava() && this.lastSnapshot.invokeShouldSwimInFluids() && !this.lastSnapshot.canWalkOnFluid(fluidState)) {
//                double ex = this.lastSnapshot.getY();
//                this.lastSnapshot.updateVelocity(0.02F, movementInput);
//                this.move(MovementType.SELF, this.lastSnapshot.getVelocity());
//                if (this.lastSnapshot.getFluidHeight(FluidTags.LAVA) <= this.lastSnapshot.getSwimHeight()) {
//                    this.lastSnapshot.setVelocity(this.lastSnapshot.getVelocity().multiply(0.5, 0.8F, 0.5));
//                    Vec3d vec3d3 = this.lastSnapshot.applyFluidMovingSpeed(d, bl, this.lastSnapshot.getVelocity());
//                    this.lastSnapshot.setVelocity(vec3d3);
//                } else {
//                    this.lastSnapshot.setVelocity(this.lastSnapshot.getVelocity().multiply(0.5));
//                }
//
//                if (d != 0.0) {
//                    this.lastSnapshot.setVelocity(this.lastSnapshot.getVelocity().add(0.0, -d / 4.0, 0.0));
//                }
//
//                Vec3d vec3d3 = this.lastSnapshot.getVelocity();
//                if (this.lastSnapshot.horizontalCollision && this.lastSnapshot.doesNotCollide(vec3d3.x, vec3d3.y + 0.6F - this.lastSnapshot.getY() + ex, vec3d3.z)) {
//                    this.lastSnapshot.setVelocity(vec3d3.x, 0.3F, vec3d3.z);
//                }
//            } else if (this.lastSnapshot.isFallFlying()) {
//                this.lastSnapshot.limitFallDistance();
//                Vec3d vec3d4 = this.lastSnapshot.getVelocity();
//                Vec3d vec3d5 = this.lastSnapshot.getRotationVector();
//                float fx = this.lastSnapshot.getPitch() * (float) (Math.PI / 180.0);
//                double i = Math.sqrt(vec3d5.x * vec3d5.x + vec3d5.z * vec3d5.z);
//                double j = vec3d4.horizontalLength();
//                double k = vec3d5.length();
//                double l = Math.cos((double)fx);
//                l = l * l * Math.min(1.0, k / 0.4);
//                vec3d4 = this.lastSnapshot.getVelocity().add(0.0, d * (-1.0 + l * 0.75), 0.0);
//                if (vec3d4.y < 0.0 && i > 0.0) {
//                    double m = vec3d4.y * -0.1 * l;
//                    vec3d4 = vec3d4.add(vec3d5.x * m / i, m, vec3d5.z * m / i);
//                }
//
//                if (fx < 0.0F && i > 0.0) {
//                    double m = j * (double)(-MathHelper.sin(fx)) * 0.04;
//                    vec3d4 = vec3d4.add(-vec3d5.x * m / i, m * 3.2, -vec3d5.z * m / i);
//                }
//
//                if (i > 0.0) {
//                    vec3d4 = vec3d4.add((vec3d5.x / i * j - vec3d4.x) * 0.1, 0.0, (vec3d5.z / i * j - vec3d4.z) * 0.1);
//                }
//
//                this.lastSnapshot.setVelocity(vec3d4.multiply(0.99F, 0.98F, 0.99F));
//                this.move(MovementType.SELF, this.lastSnapshot.getVelocity());
//                if (this.lastSnapshot.horizontalCollision && !this.lastSnapshot.getWorld().isClient) {
//                    double m = this.lastSnapshot.getVelocity().horizontalLength();
//                    double n = j - m;
//                    float o = (float)(n * 10.0 - 3.0);
//                    if (o > 0.0F) {
//                        this.lastSnapshot.playSound(this.lastSnapshot.getFallSound((int)o), 1.0F, 1.0F);
//                        ZefirOptimizations.getMainThreadActor().tell(
//                                new ZefirsActorMessages.
//                                        ApplyDamage(this.lastSnapshot, this.lastSnapshot.getDamageSources().flyIntoWall(), o),
//                                getSelf()
//                        );
//                    }
//                }
//
//                if (this.lastSnapshot.isOnGround() && !this.lastSnapshot.getWorld().isClient) {
//                    this.lastSnapshot.setFlag(this.lastSnapshot.FALL_FLYING_FLAG_INDEX(), false);
//                }
//            } else {
//                BlockPos blockPos = this.lastSnapshot.getVelocityAffectingPos();
////                float p = this.lastSnapshot.getWorld().getBlockState(blockPos).getBlock().getSlipperiness();
//                float p = 0.6F;
////                System.out.println("p: " + p);
//                float fxx = this.lastSnapshot.isOnGround() ? p * 0.91F : 0.91F;
//                Vec3d vec3d6 = this.applyMovementInput(movementInput, p);
//                double q = vec3d6.y;
//                if (this.lastSnapshot.hasStatusEffect(StatusEffects.LEVITATION)) {
//                    q += (0.05 * (double)(this.lastSnapshot.getStatusEffect(StatusEffects.LEVITATION).getAmplifier() + 1) - vec3d6.y) * 0.2;
//                } else if (!this.lastSnapshot.getWorld().isClient || this.lastSnapshot.getWorld().isChunkLoaded(blockPos)) {
//                    q -= d;
//                } else if (this.lastSnapshot.getY() > (double)this.lastSnapshot.getWorld().getBottomY()) {
//                    q = -0.1;
//                } else {
//                    q = 0.0;
//                }
//
//                if (this.lastSnapshot.hasNoDrag()) {
//                    this.lastSnapshot.setVelocity(vec3d6.x, q, vec3d6.z);
//                } else {
//                    this.lastSnapshot.setVelocity(vec3d6.x * (double)fxx, this instanceof Flutterer ? q * (double)fxx : q * 0.98F, vec3d6.z * (double)fxx);
//                }
//            }
//        }
//
//        this.lastSnapshot.updateLimbs(this instanceof Flutterer);
//    }

//    public Vec3d applyMovementInput(Vec3d movementInput, float slipperiness) {
//        this.lastSnapshot.updateVelocity(this.getMovementSpeed(slipperiness), movementInput);
//        this.lastSnapshot.setVelocity(this.applyClimbingSpeed(this.lastSnapshot.getVelocity()));
//        this.move(MovementType.SELF, this.lastSnapshot.getVelocity());
//        Vec3d vec3d = this.lastSnapshot.getVelocity();
//        if ((this.lastSnapshot.horizontalCollision || this.lastSnapshot.isJumping())
//                && (this.lastSnapshot.isClimbing() || this.lastSnapshot.getBlockStateAtPos().isOf(Blocks.POWDER_SNOW) && PowderSnowBlock.canWalkOnPowderSnow(this.lastSnapshot))) {
//            vec3d = new Vec3d(vec3d.x, 0.2, vec3d.z);
//        }
//
//        return vec3d;
//    }

//    private float getMovementSpeed(float slipperiness) {
//        return this.lastSnapshot.isOnGround() ? this.lastSnapshot.getMovementSpeed() * (0.21600002F / (slipperiness * slipperiness * slipperiness)) : this.getOffGroundSpeed();
//    }
//
//    private Vec3d applyClimbingSpeed(Vec3d motion) {
//        if (entity.isClimbing()) {
//            entity.onLanding();
//            float f = 0.15F;
//            double d = MathHelper.clamp(motion.x, -0.15F, 0.15F);
//            double e = MathHelper.clamp(motion.z, -0.15F, 0.15F);
//            double g = Math.max(motion.y, -0.15F);
//            if (g < 0.0 && !entity.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING) && entity.isHoldingOntoLadder() && entity instanceof PlayerEntity) {
//                g = 0.0;
//            }
//
//            motion = new Vec3d(d, g, e);
//        }
//
//        return motion;
//    }

//    protected float getOffGroundSpeed() {
//        return entity.getControllingPassenger() instanceof PlayerEntity ? entity.getMovementSpeed() * 0.1F : 0.02F;
//    }
//
//
//    private static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
//        double d = movementInput.lengthSquared();
//        if (d < 1.0E-7) {
//            return Vec3d.ZERO;
//        } else {
//            Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply((double) speed);
//            float f = MathHelper.sin(yaw * (float) (Math.PI / 180.0));
//            float g = MathHelper.cos(yaw * (float) (Math.PI / 180.0));
//            return new Vec3d(vec3d.x * (double) g - vec3d.z * (double) f, vec3d.y,
//                    vec3d.z * (double) g + vec3d.x * (double) f);
//        }
//    }


    protected void move(ZefirsActorMessages.InvokeMove msg) {
        MovementType movementType = msg.movementType();
        Vec3d movement = msg.movement();
        LivingEntityAccessor newEntitySnapshotAccess = (LivingEntityAccessor) this.lastSnapshot;
        IAsyncLivingEntityAccess oldEntitySnapshotAccess = (IAsyncLivingEntityAccess) this.lastSnapshot;
        if (this.lastSnapshot.noClip) {
            this.lastSnapshot.setPosition(this.lastSnapshot.getX() + movement.x, this.lastSnapshot.getY() + movement.y, this.lastSnapshot.getZ() + movement.z);
        } else {
            this.lastSnapshot.wasOnFire = this.lastSnapshot.isOnFire();
            if (movementType == MovementType.PISTON) {
                movement = newEntitySnapshotAccess.invokeAdjustMovementForPiston(movement);
                if (movement.equals(Vec3d.ZERO)) {
                    return;
                }
            }

            this.lastSnapshot.getWorld().getProfiler().push("move");
            if (this.lastSnapshot.getMovementMultiplier().lengthSquared() > 1.0E-7) {
                movement = movement.multiply(this.lastSnapshot.getMovementMultiplier());
                this.lastSnapshot.setMovementMultiplier(Vec3d.ZERO);
                this.lastSnapshot.setVelocity(Vec3d.ZERO);
            }

            movement = newEntitySnapshotAccess.invokeAdjustMovementForSneaking(movement, movementType);
            Vec3d vec3d = newEntitySnapshotAccess.invokeAdjustMovementForCollisions(movement);
            double d = vec3d.lengthSquared();
            if (d > 1.0E-7) {
                if (this.lastSnapshot.fallDistance != 0.0F && d >= 1.0) {
                    BlockHitResult blockHitResult = this.lastSnapshot.getWorld()
                            .raycast(
                                    new RaycastContext(this.lastSnapshot.getPos(), this.lastSnapshot.getPos().add(vec3d), RaycastContext.ShapeType.FALLDAMAGE_RESETTING, RaycastContext.FluidHandling.WATER, this.lastSnapshot)
                            );
                    if (blockHitResult.getType() != HitResult.Type.MISS) {
                        this.lastSnapshot.onLanding();
                    }
                }

                this.lastSnapshot.setPosition(this.lastSnapshot.getX() + vec3d.x, this.lastSnapshot.getY() + vec3d.y, this.lastSnapshot.getZ() + vec3d.z);
            }

            this.lastSnapshot.getWorld().getProfiler().pop();
            this.lastSnapshot.getWorld().getProfiler().push("rest");
            boolean bl = !MathHelper.approximatelyEquals(movement.x, vec3d.x);
            boolean bl2 = !MathHelper.approximatelyEquals(movement.z, vec3d.z);
            this.entity.horizontalCollision = bl || bl2;
            this.lastSnapshot.horizontalCollision = entity.horizontalCollision;

            this.entity.verticalCollision = movement.y != vec3d.y;
            this.lastSnapshot.verticalCollision = entity.horizontalCollision;
            this.entity.groundCollision = this.entity.verticalCollision && movement.y < 0.0;
            this.lastSnapshot.groundCollision = this.entity.groundCollision;
            if (this.entity.horizontalCollision) {
                this.entity.collidedSoftly = newEntitySnapshotAccess.invokeHasCollidedSoftly(vec3d);
                this.lastSnapshot.collidedSoftly = this.entity.collidedSoftly;
            } else {
                this.entity.collidedSoftly = false;
                this.lastSnapshot.collidedSoftly = false;
            }

            this.lastSnapshot.setOnGround(this.lastSnapshot.groundCollision, vec3d);
            BlockPos blockPos = this.lastSnapshot.getLandingPos();
            BlockState blockState = this.lastSnapshot.getWorld().getBlockState(blockPos);
            newEntitySnapshotAccess.invokeFall(vec3d.y, this.lastSnapshot.isOnGround(), blockState, blockPos);
            if (this.lastSnapshot.isRemoved()) {
                this.lastSnapshot.getWorld().getProfiler().pop();
            } else {
                if (this.lastSnapshot.horizontalCollision) {
                    Vec3d vec3d2 = this.lastSnapshot.getVelocity();
                    this.lastSnapshot.setVelocity(bl ? 0.0 : vec3d2.x, vec3d2.y, bl2 ? 0.0 : vec3d2.z);
                }

                Block block = blockState.getBlock();
                if (movement.y != vec3d.y) {
                    block.onEntityLand(this.lastSnapshot.getWorld(), this.lastSnapshot);
                }

                if (this.lastSnapshot.isOnGround()) {
                    block.onSteppedOn(this.lastSnapshot.getWorld(), blockPos, blockState, this.lastSnapshot);
                }

                Entity.MoveEffect moveEffect = newEntitySnapshotAccess.invokeGetMoveEffect();
                if (moveEffect.hasAny() && !this.lastSnapshot.hasVehicle()) {
                    double e = vec3d.x;
                    double f = vec3d.y;
                    double g = vec3d.z;
                    this.lastSnapshot.speed = this.lastSnapshot.speed + (float)(vec3d.length() * 0.6);
                    BlockPos blockPos2 = this.lastSnapshot.getSteppingPos();
                    BlockState blockState2 = this.lastSnapshot.getWorld().getBlockState(blockPos2);
                    boolean bl3 = newEntitySnapshotAccess.invokeCanClimb(blockState2);
                    if (!bl3) {
                        f = 0.0;
                    }

                    this.lastSnapshot.horizontalSpeed = this.lastSnapshot.horizontalSpeed + (float)vec3d.horizontalLength() * 0.6F;
                    this.lastSnapshot.distanceTraveled = this.lastSnapshot.distanceTraveled + (float)Math.sqrt(e * e + f * f + g * g) * 0.6F;
                    if (entity.distanceTraveled > this.lastSnapshot.getNextStepSoundDistance() && !blockState2.isAir()) {
                        boolean bl4 = blockPos2.equals(blockPos);
                        boolean bl5 = this.stepOnBlock(blockPos, blockState, moveEffect.playsSounds(), bl4, movement);
                        if (!bl4) {
                            bl5 |= this.stepOnBlock(blockPos2, blockState2, false, moveEffect.emitsGameEvents(), movement);
                        }

                        if (bl5) {
                            oldEntitySnapshotAccess.zefiroptimizations$setNextStepSoundDistance(oldEntitySnapshotAccess.zefiroptimizations$calculateNextStepSoundDistance());
                        } else if (this.lastSnapshot.isTouchingWater()) {
                            oldEntitySnapshotAccess.zefiroptimizations$setNextStepSoundDistance(oldEntitySnapshotAccess.zefiroptimizations$calculateNextStepSoundDistance());
                            if (moveEffect.playsSounds()) {
                                oldEntitySnapshotAccess.zefiroptimizations$playSwimSound();
                            }

                            if (moveEffect.emitsGameEvents()) {
                                this.lastSnapshot.emitGameEvent(GameEvent.SWIM);
                            }
                        }
                    } else if (blockState2.isAir()) {
                        oldEntitySnapshotAccess.zefiroptimizations$addAirTravelEffects();
                    }
                }

                oldEntitySnapshotAccess.zefiroptimizations$tryCheckBlockCollision();
                float h = oldEntitySnapshotAccess.zefiroptimizations$getVelocityMultiplier();
                this.lastSnapshot.setVelocity(this.lastSnapshot.getVelocity().multiply((double)h, 1.0, (double)h));
                if (this.lastSnapshot.getWorld()
                        .getStatesInBoxIfLoaded(this.lastSnapshot.getBoundingBox().contract(1.0E-6))
                        .noneMatch(state -> state.isIn(BlockTags.FIRE) || state.isOf(Blocks.LAVA))) {
                    if (oldEntitySnapshotAccess.zefiroptimizations$getFireTicks() <= 0) {
                        this.lastSnapshot.setFireTicks(-oldEntitySnapshotAccess.zefiroptimizations$getBurningDuration());
                    }

                    if (this.lastSnapshot.wasOnFire && (this.lastSnapshot.inPowderSnow || this.lastSnapshot.isWet())) {
                        oldEntitySnapshotAccess.zefiroptimizations$playExtinguishSound();
                    }
                }

                if (this.lastSnapshot.isOnFire() && (this.lastSnapshot.inPowderSnow || this.lastSnapshot.isWet())) {
                    this.lastSnapshot.setFireTicks(-oldEntitySnapshotAccess.zefiroptimizations$getBurningDuration());
                }

                this.lastSnapshot.getWorld().getProfiler().pop();
            }
        }
    }

//    private Vec3d adjustMovementForCollisions(Vec3d movement) {
//        Box box = entity.getBoundingBox();
//        List<VoxelShape> list = entity.getWorld().getEntityCollisions(entity, box.stretch(movement));
//        Vec3d vec3d = movement.lengthSquared() == 0.0 ? movement : Entity.adjustMovementForCollisions(entity, movement, box, entity.getWorld(), list);
//        boolean bl = movement.x != vec3d.x;
//        boolean bl2 = movement.y != vec3d.y;
//        boolean bl3 = movement.z != vec3d.z;
//        boolean bl4 = bl2 && movement.y < 0.0;
//        if (entity.getStepHeight() > 0.0F && (bl4 || entity.isOnGround()) && (bl || bl3)) {
//            Box box2 = bl4 ? box.offset(0.0, vec3d.y, 0.0) : box;
//            Box box3 = box2.stretch(movement.x, entity.getStepHeight(), movement.z);
//            if (!bl4) {
//                box3 = box3.stretch(0.0, -1.0E-5F, 0.0);
//            }
//
//            List<VoxelShape> list2 = findCollisionsForMovement(entity, entity.getWorld(), list, box3);
//            float f = (float)vec3d.y;
//            float[] fs = collectStepHeights(box2, list2, entity.getStepHeight(), f);
//
//            for (float g : fs) {
//                Vec3d vec3d2 = adjustMovementForCollisions(new Vec3d(movement.x, g, movement.z), box2, list2);
//                if (vec3d2.horizontalLengthSquared() > vec3d.horizontalLengthSquared()) {
//                    double d = box.minY - box2.minY;
//                    return vec3d2.add(0.0, -d, 0.0);
//                }
//            }
//        }
//
//        return vec3d;
//    }

//    private static List<VoxelShape> findCollisionsForMovement(
//            @Nullable Entity entity, World world, List<VoxelShape> regularCollisions, Box movingEntityBoundingBox
//    ) {
//        ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(regularCollisions.size() + 1);
//        if (!regularCollisions.isEmpty()) {
//            builder.addAll(regularCollisions);
//        }
//
//        WorldBorder worldBorder = world.getWorldBorder();
//        boolean bl = entity != null && worldBorder.canCollide(entity, movingEntityBoundingBox);
//        if (bl) {
//            builder.add(worldBorder.asVoxelShape());
//        }
//
//        builder.addAll(world.getBlockCollisions(entity, movingEntityBoundingBox));
//        return builder.build();
//    }

//    private static float[] collectStepHeights(Box collisionBox, List<VoxelShape> collisions, float f, float stepHeight) {
//        FloatSet floatSet = new FloatArraySet(4);
//
//        for (VoxelShape voxelShape : collisions) {
//            for (double d : voxelShape.getPointPositions(Direction.Axis.Y)) {
//                float g = (float)(d - collisionBox.minY);
//                if (!(g < 0.0F) && g != stepHeight) {
//                    if (g > f) {
//                        break;
//                    }
//
//                    floatSet.add(g);
//                }
//            }
//        }
//
//        float[] fs = floatSet.toFloatArray();
//        FloatArrays.unstableSort(fs);
//        return fs;
//    }
//
//    private static Vec3d adjustMovementForCollisions(Vec3d movement, Box entityBoundingBox, List<VoxelShape> collisions) {
//        if (collisions.isEmpty()) {
//            return movement;
//        } else {
//            double d = movement.x;
//            double e = movement.y;
//            double f = movement.z;
//            if (e != 0.0) {
//                e = VoxelShapes.calculateMaxOffset(Direction.Axis.Y, entityBoundingBox, collisions, e);
//                if (e != 0.0) {
//                    entityBoundingBox = entityBoundingBox.offset(0.0, e, 0.0);
//                }
//            }
//
//            boolean bl = Math.abs(d) < Math.abs(f);
//            if (bl && f != 0.0) {
//                f = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, entityBoundingBox, collisions, f);
//                if (f != 0.0) {
//                    entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, f);
//                }
//            }
//
//            if (d != 0.0) {
//                d = VoxelShapes.calculateMaxOffset(Direction.Axis.X, entityBoundingBox, collisions, d);
//                if (!bl && d != 0.0) {
//                    entityBoundingBox = entityBoundingBox.offset(d, 0.0, 0.0);
//                }
//            }
//
//            if (!bl && f != 0.0) {
//                f = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, entityBoundingBox, collisions, f);
//            }
//
//            return new Vec3d(d, e, f);
//        }
//    }
//
    protected boolean stepOnBlock(BlockPos pos, BlockState state, boolean playSound, boolean emitEvent, Vec3d movement) {
        LivingEntityAccessor newEntitySnapshotAccess = (LivingEntityAccessor) this.lastSnapshot;
        IAsyncLivingEntityAccess oldEntitySnapshotAccess = (IAsyncLivingEntityAccess) this.lastSnapshot;
        if (state.isAir()) {
            return false;
        } else {
            boolean bl = oldEntitySnapshotAccess.zefiroptimizations$canClimb(state);
            if ((lastSnapshot.isOnGround() || bl || lastSnapshot.isInSneakingPose() && movement.y == 0.0 || lastSnapshot.isOnRail()) && !lastSnapshot.isSwimming()) {
                if (playSound) {
                    entityAccess.zefiroptimizations$playStepSounds(pos, state);
                }

                if (emitEvent) {
                    lastSnapshot.getWorld().emitGameEvent(GameEvent.STEP, lastSnapshot.getPos(), GameEvent.Emitter.of(lastSnapshot, state));
                }

                return true;
            } else {
                return false;
            }
        }
    }
//
//    protected Vec3d adjustMovementForPiston(Vec3d movement) {
//        if (movement.lengthSquared() <= 1.0E-7) {
//            return movement;
//        } else {
//            long l = entity.getWorld().getTime();
//            if (l != entityAccess.zefiroptimizations$getPistonMovementTick()) {
//                entityAccess.zefiroptimizations$setPistonMovementDelta(new double[]{0.0, 0.0, 0.0});
//                entityAccess.zefiroptimizations$setPistonMovementTick(l);
//            }
//
//            if (movement.x != 0.0) {
//                double d = this.calculatePistonMovementFactor(Direction.Axis.X, movement.x);
//                return Math.abs(d) <= 1.0E-5F ? Vec3d.ZERO : new Vec3d(d, 0.0, 0.0);
//            } else if (movement.y != 0.0) {
//                double d = this.calculatePistonMovementFactor(Direction.Axis.Y, movement.y);
//                return Math.abs(d) <= 1.0E-5F ? Vec3d.ZERO : new Vec3d(0.0, d, 0.0);
//            } else if (movement.z != 0.0) {
//                double d = this.calculatePistonMovementFactor(Direction.Axis.Z, movement.z);
//                return Math.abs(d) <= 1.0E-5F ? Vec3d.ZERO : new Vec3d(0.0, 0.0, d);
//            } else {
//                return Vec3d.ZERO;
//            }
//        }
//    }

//    private double calculatePistonMovementFactor(Direction.Axis axis, double offsetFactor) {
//        int i = axis.ordinal();
//        double d = MathHelper.clamp(
//                offsetFactor + entityAccess.zefiroptimizations$getPistonMovementDelta()[i], -0.51, 0.51
//        );
//        offsetFactor = d - entityAccess.zefiroptimizations$getPistonMovementDelta()[i];
//        entityAccess.zefiroptimizations$getPistonMovementDelta()[i] = d;
//        return offsetFactor;
//    }

    // EntitySnapshot class to hold the copied data
    public static class EntitySnapshot extends MobEntity {
        private static final Box NULL_BOX = new Box(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        private ItemStack syncedBodyArmorStack = ItemStack.EMPTY;
        public boolean handSwinging;
        private boolean noDrag = false;
        public Hand preferredHand;
        public int handSwingTicks;
        public int stuckArrowTimer;
        public int stuckStingerTimer;
        public int hurtTime;
        public int maxHurtTime;
        public int deathTime;
        public float lastHandSwingProgress;
        public float handSwingProgress;
        protected int lastAttackedTicks;
        public float bodyYaw;
        public float prevBodyYaw;
        public float headYaw;
        public float prevHeadYaw;
        @Nullable
        protected PlayerEntity attackingPlayer;
        protected int playerHitTimer;
        protected boolean dead;
        protected int despawnCounter;
        protected float prevStepBobbingAmount;
        protected float stepBobbingAmount;
        protected float lookDirection;
        protected float prevLookDirection;
        protected float field_6215;
        protected int scoreAmount;
        protected float lastDamageTaken;
        @Getter
        protected boolean jumping;
        public float sidewaysSpeed;
        public float upwardSpeed;
        public float forwardSpeed;
        @Getter
        protected int bodyTrackingIncrements;
        protected double serverX;
        protected double serverY;
        protected double serverZ;
        protected double serverYaw;
        protected double serverPitch;
        protected double serverHeadYaw;
        protected int headTrackingIncrements;
        private boolean effectsChanged = true;
        @Nullable
        private LivingEntity attacker;
        private int lastAttackedTime;
        @Nullable
        private LivingEntity attacking;
        private int lastAttackTime;
        private float movementSpeed;
        @Setter
        @Getter
        private int jumpingCooldown;
        @Setter(AccessLevel.NONE)
        private float absorptionAmount;
        protected ItemStack activeItemStack = ItemStack.EMPTY;
        protected int itemUseTimeLeft;
        protected int fallFlyingTicks;
        private BlockPos lastBlockPos;
        private Optional<BlockPos> climbingPos = Optional.empty();
        @Nullable
        private DamageSource lastDamageSource;
        private long lastDamageTime;
        @Setter
        @Getter
        protected int riptideTicks;
        @Setter
        protected float riptideAttackDamage;
        @Nullable
        @Setter
        protected ItemStack riptideStack;
        private float leaningPitch;
        private float lastLeaningPitch;
        protected Brain<?> brain;
        private boolean experienceDroppingDisabled;
        protected float prevScale = 1.0F;
        private int id;
        public boolean intersectionChecked;
        @Getter(AccessLevel.NONE)
        private ImmutableList<Entity> passengerList = ImmutableList.of();
        protected int ridingCooldown;
        @Nullable
        private Entity vehicle;
        private World world;
        public double prevX;
        public double prevY;
        public double prevZ;
        private Vec3d pos;
        private BlockPos blockPos;
        private ChunkPos chunkPos;
        private Vec3d velocity = Vec3d.ZERO;
        private float yaw;
        private float pitch;
        public float prevYaw;
        public float prevPitch;
        @Getter(AccessLevel.NONE)
        private Box boundingBox = NULL_BOX;
        private boolean onGround;
        public boolean horizontalCollision;
        public boolean verticalCollision;
        public boolean groundCollision;
        public boolean collidedSoftly;
        public boolean velocityModified;
        @Setter
        @Getter
        protected Vec3d movementMultiplier = Vec3d.ZERO;
        @Nullable
        private Entity.RemovalReason removalReason;
        public float prevHorizontalSpeed;
        public float horizontalSpeed;
        public float distanceTraveled;
        public float speed;
        public float fallDistance;
        @Getter
        private float nextStepSoundDistance = 1.0F;
        public double lastRenderX;
        public double lastRenderY;
        public double lastRenderZ;
        public boolean noClip;
        public int age;
        private int fireTicks = -this.getBurningDuration();
        protected boolean touchingWater;
        protected Object2DoubleMap<TagKey<Fluid>> fluidHeight = new Object2DoubleArrayMap<>(2);
        protected boolean submergedInWater;
        public int timeUntilRegen;
        protected boolean firstUpdate = true;
        public boolean ignoreCameraFrustum;
        public boolean velocityDirty;
        @Nullable
        public PortalManager portalManager;
        private int portalCooldown;
        private boolean invulnerable;
        protected UUID uuid = MathHelper.randomUuid(this.random);
        protected String uuidString = this.uuid.toString();
        private boolean glowing;
        private long pistonMovementTick;
        private EntityDimensions dimensions;
        @Getter(AccessLevel.NONE)
        private float standingEyeHeight;
        public boolean inPowderSnow;
        public boolean wasInPowderSnow;
        public boolean wasOnFire;
        public Optional<BlockPos> supportingBlockPos = Optional.empty();
        private boolean forceUpdateSupportingBlockPos = false;
        private float lastChimeIntensity;
        private int lastChimeAge;
        private boolean hasVisualFire;
        @Nullable
        private BlockState stateAtPos = null;
        protected final DataTracker dataTracker;
        protected final ActorRef entityActorRef;

        private boolean firstTimeIterating = false;

        public EntitySnapshot(ActorRef entityActorRef, Vec3d pos, boolean onGround, boolean forceUpdateSupportingBlockPos, float bodyYaw, float prevBodyYaw, float headYaw, float prevHeadYaw, int lastAttackedTicks, float handSwingProgress, float lastHandSwingProgress, int handSwingTicks, boolean handSwinging, ItemStack syncedBodyArmorStack, boolean noDrag, Hand preferredHand, int stuckArrowTimer, int stuckStingerTimer, int hurtTime, int maxHurtTime, int deathTime, @Nullable PlayerEntity attackingPlayer, int playerHitTimer, boolean dead, int despawnCounter, float prevStepBobbingAmount, float stepBobbingAmount, float lookDirection, float prevLookDirection, float field_6215, int scoreAmount, float lastDamageTaken, boolean jumping, float sidewaysSpeed, float upwardSpeed, float forwardSpeed, int bodyTrackingIncrements, double serverX, double serverY, double serverZ, double serverYaw, double serverPitch, double serverHeadYaw, int headTrackingIncrements, boolean effectsChanged, @Nullable LivingEntity attacker, int lastAttackedTime, @Nullable LivingEntity attacking, int lastAttackTime, float movementSpeed, int jumpingCooldown, float absorptionAmount, ItemStack activeItemStack, int itemUseTimeLeft, int fallFlyingTicks, BlockPos lastBlockPos, Optional<BlockPos> climbingPos, @Nullable DamageSource lastDamageSource, long lastDamageTime, int riptideTicks, float riptideAttackDamage, @Nullable ItemStack riptideStack, float leaningPitch, float lastLeaningPitch, Brain<?> brain, boolean experienceDroppingDisabled, float prevScale, int id, boolean intersectionChecked, ImmutableList<Entity> passengerList, int ridingCooldown, @Nullable Entity vehicle, World world, double prevX, double prevY, double prevZ, BlockPos blockPos, ChunkPos chunkPos, Vec3d velocity, float yaw, float pitch, float prevYaw, float prevPitch, Box boundingBox, boolean horizontalCollision, boolean verticalCollision, boolean groundCollision, boolean collidedSoftly, boolean velocityModified, Vec3d movementMultiplier, @Nullable Entity.RemovalReason removalReason, float prevHorizontalSpeed, float horizontalSpeed, float distanceTraveled, float speed, float fallDistance, float nextStepSoundDistance, double lastRenderX, double lastRenderY, double lastRenderZ, boolean noClip, int age, int fireTicks, boolean touchingWater, Object2DoubleMap<TagKey<Fluid>> fluidHeight, boolean submergedInWater, int timeUntilRegen, boolean firstUpdate, boolean ignoreCameraFrustum, boolean velocityDirty, @Nullable PortalManager portalManager, int portalCooldown, boolean invulnerable, UUID uuid, String uuidString, boolean glowing, long pistonMovementTick, EntityDimensions dimensions, float standingEyeHeight, boolean inPowderSnow, boolean wasInPowderSnow, boolean wasOnFire, Optional<BlockPos> supportingBlockPos, int lastChimeAge, float lastChimeIntensity, boolean hasVisualFire, @Nullable BlockState stateAtPos, DataTracker dataTracker) {
            super(EntityType.ALLAY, world);
            this.entityActorRef = entityActorRef;
            this.pos = pos;
                this.onGround = onGround;
                this.forceUpdateSupportingBlockPos = forceUpdateSupportingBlockPos;
                this.bodyYaw = bodyYaw;
                this.prevBodyYaw = prevBodyYaw;
                this.headYaw = headYaw;
                this.prevHeadYaw = prevHeadYaw;
                this.lastAttackedTicks = lastAttackedTicks;
                this.handSwingProgress = handSwingProgress;
                this.lastHandSwingProgress = lastHandSwingProgress;
                this.handSwingTicks = handSwingTicks;
                this.handSwinging = handSwinging;
                this.syncedBodyArmorStack = syncedBodyArmorStack;
                this.noDrag = noDrag;
                this.preferredHand = preferredHand;
                this.stuckArrowTimer = stuckArrowTimer;
                this.stuckStingerTimer = stuckStingerTimer;
                this.hurtTime = hurtTime;
                this.maxHurtTime = maxHurtTime;
                this.deathTime = deathTime;
                this.attackingPlayer = attackingPlayer;
                this.playerHitTimer = playerHitTimer;
                this.dead = dead;
                this.despawnCounter = despawnCounter;
                this.prevStepBobbingAmount = prevStepBobbingAmount;
                this.stepBobbingAmount = stepBobbingAmount;
                this.lookDirection = lookDirection;
                this.prevLookDirection = prevLookDirection;
                this.field_6215 = field_6215;
                this.scoreAmount = scoreAmount;
                this.lastDamageTaken = lastDamageTaken;
                this.jumping = jumping;
                this.sidewaysSpeed = sidewaysSpeed;
                this.upwardSpeed = upwardSpeed;
                this.forwardSpeed = forwardSpeed;
                this.bodyTrackingIncrements = bodyTrackingIncrements;
                this.serverX = serverX;
                this.serverY = serverY;
                this.serverZ = serverZ;
                this.serverYaw = serverYaw;
                this.serverPitch = serverPitch;
                this.serverHeadYaw = serverHeadYaw;
                this.headTrackingIncrements = headTrackingIncrements;
                this.effectsChanged = effectsChanged;
                this.attacker = attacker;
                this.lastAttackedTime = lastAttackedTime;
                this.attacking = attacking;
                this.lastAttackTime = lastAttackTime;
                this.movementSpeed = movementSpeed;
                this.jumpingCooldown = jumpingCooldown;
                this.absorptionAmount = absorptionAmount;
                this.activeItemStack = activeItemStack;
                this.itemUseTimeLeft = itemUseTimeLeft;
                this.fallFlyingTicks = fallFlyingTicks;
                this.lastBlockPos = lastBlockPos;
                this.climbingPos = climbingPos;
                this.lastDamageSource = lastDamageSource;
                this.lastDamageTime = lastDamageTime;
                this.riptideTicks = riptideTicks;
                this.riptideAttackDamage = riptideAttackDamage;
                this.riptideStack = riptideStack;
                this.leaningPitch = leaningPitch;
                this.lastLeaningPitch = lastLeaningPitch;
                this.brain = brain;
                this.experienceDroppingDisabled = experienceDroppingDisabled;
                this.prevScale = prevScale;
                this.id = id;
                this.intersectionChecked = intersectionChecked;
                this.passengerList = passengerList;
                this.ridingCooldown = ridingCooldown;
                this.vehicle = vehicle;
                this.world = world;
                this.prevX = prevX;
                this.prevY = prevY;
                this.prevZ = prevZ;
                this.blockPos = blockPos;
                this.chunkPos = chunkPos;
                this.velocity = velocity;
                this.yaw = yaw;
                this.pitch = pitch;
                this.prevYaw = prevYaw;
                this.prevPitch = prevPitch;
                this.boundingBox = boundingBox;
                this.horizontalCollision = horizontalCollision;
                this.verticalCollision = verticalCollision;
                this.groundCollision = groundCollision;
                this.collidedSoftly = collidedSoftly;
                this.velocityModified = velocityModified;
                this.movementMultiplier = movementMultiplier;
                this.removalReason = removalReason;
                this.prevHorizontalSpeed = prevHorizontalSpeed;
                this.horizontalSpeed = horizontalSpeed;
                this.distanceTraveled = distanceTraveled;
                this.speed = speed;
                this.fallDistance = fallDistance;
                this.nextStepSoundDistance = nextStepSoundDistance;
                this.lastRenderX = lastRenderX;
                this.lastRenderY = lastRenderY;
                this.lastRenderZ = lastRenderZ;
                this.noClip = noClip;
                this.age = age;
                this.fireTicks = fireTicks;
                this.touchingWater = touchingWater;
                this.fluidHeight = fluidHeight;
                this.submergedInWater = submergedInWater;
                this.timeUntilRegen = timeUntilRegen;
                this.firstUpdate = firstUpdate;
                this.ignoreCameraFrustum = ignoreCameraFrustum;
                this.velocityDirty = velocityDirty;
                this.portalManager = portalManager;
                this.portalCooldown = portalCooldown;
                this.invulnerable = invulnerable;
                this.uuid = uuid;
                this.uuidString = uuidString;
                this.glowing = glowing;
                this.pistonMovementTick = pistonMovementTick;
                this.dimensions = dimensions;
                this.standingEyeHeight = standingEyeHeight;
                this.inPowderSnow = inPowderSnow;
                this.wasInPowderSnow = wasInPowderSnow;
                this.wasOnFire = wasOnFire;
                this.supportingBlockPos = supportingBlockPos;
                this.lastChimeAge = lastChimeAge;
                this.lastChimeIntensity = lastChimeIntensity;
                this.hasVisualFire = hasVisualFire;
                this.stateAtPos = stateAtPos;
                this.dataTracker = dataTracker;
            }

        @Override
        public void tick() {}

        @Override
        public boolean isOnGround() {
            return this.onGround;
        }

//        @Override
//        public void move(MovementType movementType, Vec3d movement){
//            this.entityActorRef.tell(new ZefirsActorMessages.InvokeMove(movementType, movement), entityActorRef);
//        }
    }
}
