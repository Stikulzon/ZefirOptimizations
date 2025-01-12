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
import ua.zefir.zefiroptimizations.mixin.EntityAccessor;
import ua.zefir.zefiroptimizations.mixin.LivingEntityAccessor;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Getter
@Setter
public class EntityActor extends AbstractActor {
    protected final LivingEntity entity;
    protected final LivingEntityAccessor newEntityAccess;
    protected final ThreadLocalRandom random = ThreadLocalRandom.current();
    private boolean firstTimeIterating = true;

    public EntityActor(LivingEntity entity) {
        this.entity = entity;
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
                .match(ZefirsActorMessages.BaseTickSingleEntity.class, this::handleBaseTick)

                .match(ZefirsActorMessages.RequestIsRemoved.class, message -> {
                    getSender().tell(new ZefirsActorMessages.RequestIsRemoved(this.entity.isRemoved()), getSelf());
                })
                .build();
    }

    protected void handleAsyncTick(ZefirsActorMessages.AsyncTick msg) {
            if (!entity.isRemoved()) {
                entity.tick();
            }
    }

    protected void handleBaseTick(ZefirsActorMessages.BaseTickSingleEntity msg) {
            if (!entity.isRemoved()) {
                entity.baseTick();
            }
    }

    public void continueTickMovement(ZefirsActorMessages.ContinueTickMovement msg) {
        LivingEntityAccessor entityAccessor = (LivingEntityAccessor) entity;

        entity.getWorld().getProfiler().pop();
        entity.getWorld().getProfiler().push("jump");
        if (entityAccessor.isJumping() && entityAccessor.invokeShouldSwimInFluids()) {
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
                    if ((entity.isOnGround() || bl && g <= h) && entityAccessor.getJumpingCooldown() == 0) {
                        entity.jump();
                        entityAccessor.setJumpingCooldown(10);
                    }
                } else {
                    entityAccessor.invokeSwimUpward(FluidTags.LAVA);
                }
            } else {
                entityAccessor.invokeSwimUpward(FluidTags.WATER);
            }
        } else {
            entityAccessor.setJumpingCooldown(0);
        }

        entity.getWorld().getProfiler().pop();
        entity.getWorld().getProfiler().push("travel");
        entity.sidewaysSpeed *= 0.98F;
        entity.forwardSpeed *= 0.98F;
        entityAccessor.invokeTickFallFlying();
        Box box = entity.getBoundingBox();
        Vec3d vec3d2 = new Vec3d((double)entity.sidewaysSpeed, (double)entity.upwardSpeed, (double)entity.forwardSpeed);
        if (entity.hasStatusEffect(StatusEffects.SLOW_FALLING) || entity.hasStatusEffect(StatusEffects.LEVITATION)) {
            entity.onLanding();
        }

        label104: {
            if (entity.getControllingPassenger() instanceof PlayerEntity playerEntity && entity.isAlive()) {
                entityAccessor.invokeTravelControlled(playerEntity, vec3d2);
                break label104;
            }

            entity.travel(vec3d2);
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

        entityAccessor.invokeRemovePowderSnowSlow();
        entityAccessor.invokeAddPowderSnowSlowIfNeeded();
        if (!entity.getWorld().isClient && entity.age % 40 == 0 && entity.isFrozen() && entity.canFreeze()) {
            entity.damage(entity.getDamageSources().freeze(), 1.0F);
        }

        entity.getWorld().getProfiler().pop();
        entity.getWorld().getProfiler().push("push");
        if (entityAccessor.getRiptideTicks() > 0) {
            entityAccessor.setRiptideTicks( entityAccessor.getRiptideTicks() - 1 );
            this.tickRiptide(box, ((EntityAccessor) entity).invokeGetBoundingBox());
        }

        this.tickCramming();
        entity.getWorld().getProfiler().pop();
        if (!entity.getWorld().isClient && entity.hurtByWater() && entity.isWet()) {
            entity.damage(entity.getDamageSources().drown(), 1.0F);
        }
    }

//    public void tickMobEntityMovement() {
//        tickMovement();
//    }

//    protected void tickMovement() {
//        // Create a copy of the entity's relevant data
//        this.lastSnapshot = createEntitySnapshot();
//        LivingEntityAccessor newEntitySnapshotAccess = (LivingEntityAccessor) this.lastSnapshot;
//
//        if(firstTimeIterating) {
//            System.out.println("this.lastSnapshot.pos 1: " + this.lastSnapshot.pos);
//        }
//
//        if (this.lastSnapshot.jumpingCooldown > 0) {
//            this.lastSnapshot.jumpingCooldown = this.lastSnapshot.jumpingCooldown - 1;
//        }
//
//        if (this.lastSnapshot.isLogicalSideForUpdatingMovement()) {
//            this.lastSnapshot.bodyTrackingIncrements = 0;
//            this.lastSnapshot.updateTrackedPosition(this.lastSnapshot.getX(), this.lastSnapshot.getY(), this.lastSnapshot.getZ());
//        }
//
//        if (this.lastSnapshot.bodyTrackingIncrements > 0) {
//            newEntitySnapshotAccess.invokeLerpPosAndRotation(this.lastSnapshot.bodyTrackingIncrements, this.lastSnapshot.serverX, this.lastSnapshot.serverY, this.lastSnapshot.serverZ, this.lastSnapshot.serverYaw, this.lastSnapshot.serverPitch);
//            this.lastSnapshot.headTrackingIncrements = this.lastSnapshot.getBodyTrackingIncrements() - 1;
//        } else if (!this.lastSnapshot.canMoveVoluntarily()) {
//            this.lastSnapshot.setVelocity(this.lastSnapshot.velocity.multiply(0.98));
//        }
//
//        if (this.lastSnapshot.headTrackingIncrements > 0) {
//            newEntitySnapshotAccess.invokeLerpHeadYaw(this.lastSnapshot.headTrackingIncrements, this.lastSnapshot.serverHeadYaw);
//            this.lastSnapshot.headTrackingIncrements = this.lastSnapshot.headTrackingIncrements - 1;
//        }
//
//        Vec3d vec3d = this.lastSnapshot.velocity;
//        double d = vec3d.x;
//        double e = vec3d.y;
//        double f = vec3d.z;
//        if (Math.abs(vec3d.x) < 0.003) {
//            d = 0.0;
//        }
//
//        if (Math.abs(vec3d.y) < 0.003) {
//            e = 0.0;
//        }
//
//        if (Math.abs(vec3d.z) < 0.003) {
//            f = 0.0;
//        }
//
//        this.lastSnapshot.setVelocity(d, e, f);
//        this.lastSnapshot.getWorld().getProfiler().push("ai");
//        if(firstTimeIterating) {
//            System.out.println("this.lastSnapshot.pos 2: " + this.lastSnapshot.pos);
//        }
//        if (newEntitySnapshotAccess.invokeIsImmobile()) {
//            this.lastSnapshot.setJumping(false);
//            this.lastSnapshot.sidewaysSpeed = 0.0F;
//            this.lastSnapshot.forwardSpeed = 0.0F;
//        } else
//            if (this.lastSnapshot.canMoveVoluntarily()) {
//                if(firstTimeIterating) {
//                    System.out.println("Entity Pos1: " + this.entity.getPos());
//                }
//                ZefirOptimizations.getMainThreadActor().tell(
//                        new ZefirsActorMessages.ApplyPositionAndRotationDiff(entity, this.lastSnapshot),
//                        getSelf()
//                );
//                if(firstTimeIterating) {
//                    System.out.println("Entity Pos2: " + this.entity.getPos());
//                }
//                ZefirOptimizations.getMainThreadActor().tell(
//                        new ZefirsActorMessages.TickNewAiAndContinue(getSelf(), entity),
//                        getSelf()
//                );
//                if(firstTimeIterating) {
//                    System.out.println("Entity Pos3: " + this.entity.getPos());
//                }
//            return;
//        }
////        if(firstTimeIterating) {
////            System.out.println("this.lastSnapshot.pos 2: " + this.lastSnapshot.pos);
////        }
//        this.getSelf().tell(new ZefirsActorMessages.ContinueTickMovement(), getSelf());
//    }
//
//    protected void continueTickMovement(ZefirsActorMessages.ContinueTickMovement msg) {
//        if (entity.isRemoved()) {
//            return;
//        }
//
//        this.lastSnapshot = createEntitySnapshot();
//        if(firstTimeIterating) {
//            System.out.println("Entity Pos: " + this.entity.getPos());
//        }
//
//        if(firstTimeIterating) {
//            System.out.println("this.lastSnapshot.pos 3: " + this.lastSnapshot.pos);
//        }
//
//        LivingEntityAccessor newEntitySnapshotAccess = (LivingEntityAccessor) this.lastSnapshot;
////        IAsyncLivingEntityAccess oldEntitySnapshotAccess = (IAsyncLivingEntityAccess) this.lastSnapshot;
//
//        this.lastSnapshot.getWorld().getProfiler().pop();
//        this.lastSnapshot.getWorld().getProfiler().push("jump");
//        if (this.lastSnapshot.isJumping() && newEntitySnapshotAccess.invokeShouldSwimInFluids()) {
//            double g;
//            if (this.lastSnapshot.isInLava()) {
//                g = this.lastSnapshot.getFluidHeight(FluidTags.LAVA);
//            } else {
//                g = this.lastSnapshot.getFluidHeight(FluidTags.WATER);
//            }
//
//            boolean bl = this.lastSnapshot.isTouchingWater() && g > 0.0;
//            double h = this.lastSnapshot.getSwimHeight();
//            if (!bl || this.lastSnapshot.isOnGround() && !(g > h)) {
//                if (!this.lastSnapshot.isInLava() || this.lastSnapshot.isOnGround() && !(g > h)) {
//                    if ((this.lastSnapshot.isOnGround() || bl && g <= h) && this.lastSnapshot.getJumpingCooldown() == 0) {
//                        this.lastSnapshot.jump();
//                        this.lastSnapshot.setJumpingCooldown(10);
//                    }
//                } else {
//                    newEntitySnapshotAccess.invokeSwimUpward(FluidTags.LAVA);
//                }
//            } else {
//                newEntitySnapshotAccess.invokeSwimUpward(FluidTags.WATER);
//            }
//        } else {
//            this.lastSnapshot.setJumpingCooldown(0);
//        }
//        if(firstTimeIterating) {
//            System.out.println("this.lastSnapshot.pos 4: " + this.lastSnapshot.pos);
//        }
//
//        this.lastSnapshot.getWorld().getProfiler().pop();
//        this.lastSnapshot.getWorld().getProfiler().push("travel");
//        this.lastSnapshot.sidewaysSpeed *= 0.98F;
//        this.lastSnapshot.forwardSpeed *= 0.98F;
//        newEntitySnapshotAccess.invokeTickFallFlying();
//        Box box = this.lastSnapshot.getBoundingBox();
//        Vec3d vec3d2 = new Vec3d(this.lastSnapshot.sidewaysSpeed, this.lastSnapshot.upwardSpeed, this.lastSnapshot.forwardSpeed);
//        if (this.lastSnapshot.hasStatusEffect(StatusEffects.SLOW_FALLING) || this.lastSnapshot.hasStatusEffect(StatusEffects.LEVITATION)) {
//            this.lastSnapshot.onLanding();
//        }
//
//        label104: {
//            if (this.lastSnapshot.getControllingPassenger() instanceof PlayerEntity playerEntity && this.lastSnapshot.isAlive()) {
//                newEntitySnapshotAccess.invokeTravelControlled(playerEntity, vec3d2);
//                break label104;
//            }
//
//            this.lastSnapshot.travel(vec3d2);
//        }
//
//        this.lastSnapshot.getWorld().getProfiler().pop();
//        this.lastSnapshot.getWorld().getProfiler().push("freezing");
//        if (!this.lastSnapshot.getWorld().isClient && !this.lastSnapshot.isDead()) {
//            int i = this.lastSnapshot.getFrozenTicks();
//            if (this.lastSnapshot.inPowderSnow && this.lastSnapshot.canFreeze()) {
//                this.lastSnapshot.setFrozenTicks(Math.min(this.lastSnapshot.getMinFreezeDamageTicks(), i + 1));
//            } else {
//                this.lastSnapshot.setFrozenTicks(Math.max(0, i - 2));
//            }
//        }
//        if(firstTimeIterating) {
//            System.out.println("this.lastSnapshot.pos 5: " + this.lastSnapshot.pos);
//        }
//
//        newEntitySnapshotAccess.invokeRemovePowderSnowSlow();
//        newEntitySnapshotAccess.invokeAddPowderSnowSlowIfNeeded();
//        if (!this.lastSnapshot.getWorld().isClient && this.lastSnapshot.age % 40 == 0 && this.lastSnapshot.isFrozen() && this.lastSnapshot.canFreeze()) {
//            ZefirOptimizations.getMainThreadActor().tell(
//                    new ZefirsActorMessages.ApplyDamage(entity, this.lastSnapshot.getDamageSources().freeze(), 1.0F),
//                    getSelf()
//            );
//        }
//
//        this.lastSnapshot.getWorld().getProfiler().pop();
//        this.lastSnapshot.getWorld().getProfiler().push("push");
//        if (this.lastSnapshot.getRiptideTicks() > 0) {
//            this.lastSnapshot.setRiptideTicks(this.lastSnapshot.getRiptideTicks() - 1);
//            this.tickRiptide(box, this.lastSnapshot.getBoundingBox());
//        }
//        if(firstTimeIterating) {
//            System.out.println("this.lastSnapshot.pos 6: " + this.lastSnapshot.pos);
//        }
//
//        this.tickCramming();
//        this.lastSnapshot.getWorld().getProfiler().pop();
//        if (!this.lastSnapshot.getWorld().isClient && this.lastSnapshot.hurtByWater() && this.lastSnapshot.isWet()) {
//            ZefirOptimizations.getMainThreadActor().tell(
//                    new ZefirsActorMessages.ApplyDamage(entity, entity.getDamageSources().drown(), 1.0F),
//                    getSelf()
//            );
//        }
//        ZefirOptimizations.getMainThreadActor().tell(
//                new ZefirsActorMessages.ApplyPositionAndRotationDiff(entity, this.lastSnapshot),
//                getSelf()
//        );
//        if(firstTimeIterating) {
//            System.out.println("this.lastSnapshot.pos 7: " + this.lastSnapshot.pos);
//        }
//        firstTimeIterating = false;
//    }
//
    protected void tickCramming() {
        LivingEntityAccessor newEntitySnapshotAccess = (LivingEntityAccessor) this.entity;

        if (this.entity.getWorld().isClient()) {
            this.entity.getWorld()
                    .getEntitiesByType(EntityType.PLAYER, this.entity.getBoundingBox(), EntityPredicates.canBePushedBy(this.entity))
                    .forEach(newEntitySnapshotAccess::invokePushAway);
        } else {
            List<Entity> list = this.entity.getWorld().getOtherEntities(this.entity, this.entity.getBoundingBox(), EntityPredicates.canBePushedBy(this.entity));

            if (!list.isEmpty()) {
                int maxEntityCramming = this.entity.getWorld().getGameRules().getInt(GameRules.MAX_ENTITY_CRAMMING);
                if (maxEntityCramming > 0 && list.size() > maxEntityCramming - 1 && random.nextInt(4) == 0) {
                    int nonVehicleEntities = 0;
                    for (Entity entity : list) {
                        if (!entity.hasVehicle()) {
                            nonVehicleEntities++;
                        }
                    }

                    if (nonVehicleEntities > maxEntityCramming - 1) {
                        ZefirOptimizations.getMainThreadActor().tell(
                                new ZefirsActorMessages.ApplyDamage(this.entity, this.entity.getDamageSources().cramming(), 6.0F),
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
//
    protected void tickRiptide(Box a, Box b) {
        LivingEntityAccessor livingEntityAccessor = (LivingEntityAccessor) this.entity;

        Box box = a.union(b);
        List<Entity> list = this.entity.getWorld().getOtherEntities(this.entity, box);
        if (!list.isEmpty()) {
            for (Entity entity : list) {
                if (entity instanceof LivingEntity) {
                    livingEntityAccessor.invokeAttackLivingEntity((LivingEntity)entity);
                    livingEntityAccessor.setRiptideTicks(0);
                    entity.setVelocity(entity.getVelocity().multiply(-0.2));
                    break;
                }
            }
        } else if (this.entity.horizontalCollision) {
            livingEntityAccessor.setRiptideTicks(0);
        }

        if (!this.entity.getWorld().isClient && livingEntityAccessor.getRiptideTicks() <= 0) {
            livingEntityAccessor.invokeSetLivingFlag(LivingEntityAccessor.getUSING_RIPTIDE_FLAG(), false);
            livingEntityAccessor.setRiptideAttackDamage(0.0F);
            livingEntityAccessor.setRiptideStack(null);
        }
    }
}
