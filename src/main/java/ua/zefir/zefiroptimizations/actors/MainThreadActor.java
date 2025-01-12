package ua.zefir.zefiroptimizations.actors;


import akka.actor.AbstractActor;
import net.minecraft.entity.LivingEntity;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.mixin.LivingEntityAccessor;
import ua.zefir.zefiroptimizations.mixin.MobEntityAccessor;

public class MainThreadActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
//                .match(ZefirsActorMessages.TickNewAiAndContinue.class, msg -> {
//                    ZefirOptimizations.SERVER.execute(() -> {
//                        msg.entity().getWorld().getProfiler().push("newAi");
//                        ((LivingEntityAccessor) msg.entity()).invokeTickNewAi();
//                        msg.entity().getWorld().getProfiler().pop();
////                        msg.requestingActor().tell(new ZefirsActorMessages.ContinueTickMovement(), getSelf());
//                    });
//                })
                .match(ZefirsActorMessages.ApplyDamage.class, msg -> {
                    ZefirOptimizations.SERVER.execute(() -> {
                        msg.entity().damage(msg.source(), msg.amount());
                    });
                })
                .match(ZefirsActorMessages.LootItemEntity.class, msg -> {
                    ZefirOptimizations.SERVER.execute(() -> {
                        ((MobEntityAccessor) msg.entity()).invokeLoot(msg.itemEntity());
                    });
                })
                .match(ZefirsActorMessages.FindCollisionsForMovement.class, msg -> {
                    ZefirOptimizations.SERVER.execute(() -> {
                        getSender().tell(42, getSelf());
                    });
                })
//                .match(ZefirsActorMessages.ApplyPositionAndRotationDiff.class, this::applyPositionAndRotationDiff)
                .build();
    }

//    private void applyPositionAndRotationDiff(ZefirsActorMessages.ApplyPositionAndRotationDiff msg) {
//        ZefirOptimizations.SERVER.execute(() -> {
//            LivingEntity entity = msg.entity();
//            EntityActor.EntitySnapshot snapshot = msg.snapshot();
//
//            if(ZefirOptimizations.firstTimeIterating) {
//                System.out.println("Entity Pos before setting: " + entity.getPos());
//                System.out.println("Snapshot Pos before setting: " + snapshot.getPos());
//            }
//
//            LivingEntityAccessor entityToSetAccess = (LivingEntityAccessor) entity;
//            LivingEntityAccessor entityToGetAccess = (LivingEntityAccessor) snapshot;
//
//            double diffX = snapshot.getX() - entity.getX();
//            double diffY = snapshot.getY() - entity.getY();
//            double diffZ = snapshot.getZ() - entity.getZ();
//
////            entity.setPos(entity.getX() + diffX, entity.getY() + diffY, entity.getZ() + diffZ);
//
//            entity.refreshPositionAndAngles(entity.getX() + diffX, entity.getY() + diffY, entity.getZ() + diffZ, snapshot.getYaw(), snapshot.getPitch());
//            entity.setPos(entity.getX() + diffX, entity.getY() + diffY, entity.getZ() + diffZ);
//
////            if(ZefirOptimizations.firstTimeIterating) {
////                System.out.println("diffX: " + (entity.getX() + diffX));
////                System.out.println("diffY: " + (entity.getY() + diffY));
////                System.out.println("diffZ: " + (entity.getZ() + diffZ));
////                System.out.println("Entity Pos setting 1: " + entity.getPos());
////                entity.setPos(entity.getX() + diffX, entity.getY() + diffY, entity.getZ() + diffZ);
////                System.out.println("Entity Pos setting 2: " + entity.getPos());
////                Vec3d pos = new Vec3d(entity.getX() + diffX, entity.getY() + diffY, entity.getZ() + diffZ);
////                entityToSetAccess.setPos(pos);
////                System.out.println("Entity Pos setting 3: " + entity.getPos());
////            }
//
////            double diffServerX = entityToGetAccess.getServerX() - entityToSetAccess.getServerX();
////            double diffServerY = entityToGetAccess.getServerY() - entityToSetAccess.getServerY();
////            double diffServerZ = entityToGetAccess.getServerZ() - entityToSetAccess.getServerZ();
////
////            entityToSetAccess.setServerX(entityToSetAccess.getServerX() + diffServerX);
////            entityToSetAccess.setServerY(entityToSetAccess.getServerY() + diffServerY);
////            entityToSetAccess.setServerZ(entityToSetAccess.getServerZ() + diffServerZ);
//
////            entityToSetAccess.setServerYaw(entityToGetAccess.getServerYaw());
////            entityToSetAccess.setServerPitch(entityToGetAccess.getServerPitch());
////            entityToSetAccess.setServerHeadYaw(entityToGetAccess.getServerHeadYaw());
//
////            entityToSetAccess.setPrevX(entityToGetAccess.getPrevX());
////            entityToSetAccess.setPrevY(entityToGetAccess.getPrevY());
////            entityToSetAccess.setPrevZ(entityToGetAccess.getPrevZ());
//
//
////            int diffBlockPosX = entityToSetAccess.getBlockPos().getX() + (entityToGetAccess.getBlockPos().getX() - entityToSetAccess.getBlockPos().getX());
////            int diffBlockPosY = entityToSetAccess.getBlockPos().getY() + ((entityToGetAccess.getBlockPos().getY() - entityToSetAccess.getBlockPos().getY()));
////            int diffBlockPosZ = entityToSetAccess.getBlockPos().getZ() + ((entityToGetAccess.getBlockPos().getZ() - entityToSetAccess.getBlockPos().getZ()));
////
////            BlockPos diffBlockPos = new BlockPos(diffBlockPosX, diffBlockPosY, diffBlockPosZ);
////
////            entityToSetAccess.setBlockPos(diffBlockPos);
////
////            int diffChunkPosX = entityToSetAccess.getChunkPos().x + (entityToGetAccess.getChunkPos().x - entityToSetAccess.getChunkPos().x);
////            int diffChunkPosZ = entityToSetAccess.getChunkPos().z + (entityToGetAccess.getChunkPos().z - entityToSetAccess.getChunkPos().z);
////
////            ChunkPos diffChunkPos = new ChunkPos(diffChunkPosX, diffChunkPosZ);
////
////            entityToSetAccess.setChunkPos(diffChunkPos);
//
//
////            setEntityValues(msg.entity(), msg.snapshot());
//
//            if(ZefirOptimizations.firstTimeIterating) {
//                System.out.println("Entity Pos after setting: " + entity.getPos());
//            }
//            ZefirOptimizations.firstTimeIterating = false;
//        });
//    }


    public void setEntityValues(LivingEntity entityToSet, LivingEntity entityToGet, LivingEntity snapshot){
        LivingEntityAccessor entityToSetAccess = (LivingEntityAccessor) entityToSet;
        LivingEntityAccessor entityToGetAccess = (LivingEntityAccessor) entityToGet;

        entityToSetAccess.setOnGround(snapshot.isOnGround());
        entityToSetAccess.setForceUpdateSupportingBlockPos(entityToGetAccess.isForceUpdateSupportingBlockPos());
//        entityToSetAccess.setBodyYaw(entityToGetAccess.getBodyYaw());
//        entityToSetAccess.setPrevBodyYaw(entityToGetAccess.getPrevBodyYaw());
//        entityToSetAccess.setHeadYaw(entityToGetAccess.getHeadYaw());
//        entityToSetAccess.setPrevHeadYaw(entityToGetAccess.getPrevHeadYaw());
        entityToSetAccess.setLastAttackedTicks(entityToGetAccess.getLastAttackedTicks());
        entityToSetAccess.setHandSwingProgress(entityToGetAccess.getHandSwingProgress());
        entityToSetAccess.setLastHandSwingProgress(entityToGetAccess.getLastHandSwingProgress());
        entityToSetAccess.setHandSwingTicks(entityToGetAccess.getHandSwingTicks());
        entityToSetAccess.setHandSwinging(entityToGetAccess.getHandSwinging());
        entityToSetAccess.setSyncedBodyArmorStack(entityToGetAccess.getSyncedBodyArmorStack());
        entityToSetAccess.setNoDrag(entityToGetAccess.getNoDrag());
        entityToSetAccess.setPreferredHand(entityToGetAccess.getPreferredHand());
        entityToSetAccess.setStuckArrowTimer(entityToGetAccess.getStuckArrowTimer());
        entityToSetAccess.setStuckStingerTimer(entityToGetAccess.getStuckStingerTimer());
        entityToSetAccess.setHurtTime(entityToGetAccess.getHurtTime());
        entityToSetAccess.setMaxHurtTime(entityToGetAccess.getMaxHurtTime());
        entityToSetAccess.setDeathTime(entityToGetAccess.getDeathTime());
        entityToSetAccess.setAttackingPlayer(entityToGetAccess.getAttackingPlayer());
        entityToSetAccess.setPlayerHitTimer(entityToGetAccess.getPlayerHitTimer());
        entityToSetAccess.setDead(entityToGetAccess.getDead());
        entityToSetAccess.setDespawnCounter(entityToGetAccess.getDespawnCounter());
        entityToSetAccess.setPrevStepBobbingAmount(entityToGetAccess.getPrevStepBobbingAmount());
        entityToSetAccess.setStepBobbingAmount(entityToGetAccess.getStepBobbingAmount());
        entityToSetAccess.setLookDirection(entityToGetAccess.getLookDirection());
        entityToSetAccess.setPrevLookDirection(entityToGetAccess.getPrevLookDirection());
        entityToSetAccess.setField_6215(entityToGetAccess.getField_6215());
        entityToSetAccess.setScoreAmount(entityToGetAccess.getScoreAmount());
        entityToSetAccess.setLastDamageTaken(entityToGetAccess.getLastDamageTaken());
        entityToSetAccess.setJumping(entityToGetAccess.getJumping());
        entityToSetAccess.setSidewaysSpeed(entityToGetAccess.getSidewaysSpeed());
        entityToSetAccess.setUpwardSpeed(entityToGetAccess.getUpwardSpeed());
        entityToSetAccess.setForwardSpeed(entityToGetAccess.getForwardSpeed());
        entityToSetAccess.setBodyTrackingIncrements(entityToGetAccess.getBodyTrackingIncrements());
        entityToSetAccess.setHeadTrackingIncrements(entityToGetAccess.getHeadTrackingIncrements());
        entityToSetAccess.setEffectsChanged(entityToGetAccess.getEffectsChanged());
        entityToSetAccess.setAttacker(entityToGetAccess.getAttacker());
        entityToSetAccess.setLastAttackedTime(entityToGetAccess.getLastAttackedTime());
        entityToSetAccess.setAttacking(entityToGetAccess.getAttacking());
        entityToSetAccess.setLastAttackTime(entityToGetAccess.getLastAttackTime());
        entityToSetAccess.setMovementSpeed(entityToGetAccess.getMovementSpeed());
        entityToSetAccess.setJumpingCooldown(entityToGetAccess.getJumpingCooldown());
        entityToSetAccess.setAbsorptionAmount(entityToGetAccess.getAbsorptionAmount());
        entityToSetAccess.setActiveItemStack(entityToGetAccess.getActiveItemStack());
        entityToSetAccess.setItemUseTimeLeft(entityToGetAccess.getItemUseTimeLeft());
        entityToSetAccess.setFallFlyingTicks(entityToGetAccess.getFallFlyingTicks());
        entityToSetAccess.setLastBlockPos(entityToGetAccess.getLastBlockPos());
        entityToSetAccess.setClimbingPos(entityToGetAccess.getClimbingPos());
        entityToSetAccess.setLastDamageSource(entityToGetAccess.getLastDamageSource());
        entityToSetAccess.setLastDamageTime(entityToGetAccess.getLastDamageTime());
        entityToSetAccess.setRiptideTicks(entityToGetAccess.getRiptideTicks());
        entityToSetAccess.setRiptideAttackDamage(entityToGetAccess.getRiptideAttackDamage());
        entityToSetAccess.setRiptideStack(entityToGetAccess.getRiptideStack());
//        entityToSetAccess.setLeaningPitch(entityToGetAccess.getLeaningPitch());
//        entityToSetAccess.setLastLeaningPitch(entityToGetAccess.getLastLeaningPitch());
//        entityToSetAccess.setBrain(entityToGetAccess.getBrain()); // Brain is likely mutable and shouldn't be directly copied.
        entityToSetAccess.setExperienceDroppingDisabled(entityToGetAccess.getExperienceDroppingDisabled());
        entityToSetAccess.setPrevScale(entityToGetAccess.getPrevScale());
//        entityToSetAccess.setId(entityToGetAccess.getId()); // Setting ID might be problematic.
        entityToSetAccess.setIntersectionChecked(entityToGetAccess.isIntersectionChecked());
        entityToSetAccess.setPassengerList(entityToGetAccess.getPassengerList()); // Passenger list is likely mutable. Handle with care.
        entityToSetAccess.setRidingCooldown(entityToGetAccess.getRidingCooldown());
        entityToSetAccess.setVehicle(entityToGetAccess.getVehicle());
        entityToSetAccess.setVelocity(snapshot.getVelocity());
//        entityToSetAccess.setYaw(entityToGetAccess.getYaw());
//        entityToSetAccess.setPitch(entityToGetAccess.getPitch());
//        entityToSetAccess.setPrevYaw(entityToGetAccess.getPrevYaw());
//        entityToSetAccess.setPrevPitch(entityToGetAccess.getPrevPitch());
        entityToSetAccess.setBoundingBox(snapshot.getBoundingBox());
        entityToSetAccess.setHorizontalCollision(entityToGetAccess.isHorizontalCollision());
        entityToSetAccess.setVerticalCollision(entityToGetAccess.isVerticalCollision());
        entityToSetAccess.setGroundCollision(entityToGetAccess.isGroundCollision());
        entityToSetAccess.setCollidedSoftly(entityToGetAccess.hasCollidedSoftly());

        entityToSetAccess.setVelocityModified(entityToGetAccess.isVelocityModified());
        entityToSetAccess.setMovementMultiplier(entityToGetAccess.getMovementMultiplier());
        entityToSetAccess.setPrevHorizontalSpeed(entityToGetAccess.getPrevHorizontalSpeed());
        entityToSetAccess.setHorizontalSpeed(entityToGetAccess.getHorizontalSpeed());
        entityToSetAccess.setDistanceTraveled(entityToGetAccess.getDistanceTraveled());
        entityToSetAccess.setSpeed(entityToGetAccess.getSpeed());
        entityToSetAccess.setFallDistance(entityToGetAccess.getFallDistance());
        entityToSetAccess.setNextStepSoundDistance(entityToGetAccess.getNextStepSoundDistance());
        entityToSetAccess.setLastRenderX(entityToGetAccess.getLastRenderX());
        entityToSetAccess.setLastRenderY(entityToGetAccess.getLastRenderY());
        entityToSetAccess.setLastRenderZ(entityToGetAccess.getLastRenderZ());
        entityToSetAccess.setNoClip(entityToGetAccess.isNoClip());
        entityToSet.age = entityToGetAccess.getAge();
        entityToSetAccess.setFireTicks(snapshot.getFireTicks());
        entityToSetAccess.setTouchingWater(snapshot.isTouchingWater());
        entityToSetAccess.setFluidHeight(entityToGetAccess.getFluidHeight());
        entityToSetAccess.setSubmergedInWater(entityToGetAccess.isSubmergedInWater());
        entityToSetAccess.setTimeUntilRegen(entityToGetAccess.getTimeUntilRegen());
        entityToSetAccess.setFirstUpdate(entityToGetAccess.isFirstUpdate());
        entityToSetAccess.setIgnoresCameraFrustum(entityToGetAccess.ignoresCameraFrustum());
        entityToSetAccess.setVelocityDirty(entityToGetAccess.isVelocityDirty());
        entityToSetAccess.setPortalCooldown(entityToGetAccess.getPortalCooldown());
        entityToSetAccess.setInvulnerable(entityToGetAccess.isInvulnerable());
        entityToSetAccess.setGlowing(entityToGetAccess.isGlowing());
        entityToSetAccess.setPistonMovementTick(entityToGetAccess.getPistonMovementTick());
        entityToSetAccess.setDimensions(entityToGetAccess.getDimensions());
        entityToSetAccess.setStandingEyeHeight(entityToGetAccess.getStandingEyeHeight());
        entityToSetAccess.setInPowderSnow(entityToGetAccess.isInPowderSnow());
        entityToSetAccess.setWasInPowderSnow(entityToGetAccess.wasInPowderSnow());
        entityToSetAccess.setWasOnFire(entityToGetAccess.wasOnFire());
        entityToSetAccess.setSupportingBlockPos(entityToGetAccess.getSupportingBlockPos());
        entityToSetAccess.setLastChimeAge(entityToGetAccess.getLastChimeAge());
        entityToSetAccess.setLastChimeIntensity(entityToGetAccess.getLastChimeIntensity());
        entityToSetAccess.setHasVisualFire(entityToGetAccess.hasVisualFire());
        entityToSetAccess.setStateAtPos(entityToGetAccess.getStateAtPos());
    }
}
