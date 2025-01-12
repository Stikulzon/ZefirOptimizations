package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor extends EntityAccessor  {
    @Accessor("syncedBodyArmorStack")
    ItemStack getSyncedBodyArmorStack();

    @Accessor("syncedBodyArmorStack")
    void setSyncedBodyArmorStack(ItemStack stack);

    @Accessor("handSwinging")
    boolean getHandSwinging();

    @Accessor("handSwinging")
    void setHandSwinging(boolean handSwinging);

    @Accessor("noDrag")
    boolean getNoDrag();

    @Accessor("noDrag")
    void setNoDrag(boolean noDrag);

    @Accessor("preferredHand")
    Hand getPreferredHand();

    @Accessor("preferredHand")
    void setPreferredHand(Hand preferredHand);

    @Accessor("handSwingTicks")
    int getHandSwingTicks();

    @Accessor("handSwingTicks")
    void setHandSwingTicks(int handSwingTicks);

    @Accessor("stuckArrowTimer")
    int getStuckArrowTimer();

    @Accessor("stuckArrowTimer")
    void setStuckArrowTimer(int stuckArrowTimer);

    @Accessor("stuckStingerTimer")
    int getStuckStingerTimer();

    @Accessor("stuckStingerTimer")
    void setStuckStingerTimer(int stuckStingerTimer);

    @Accessor("hurtTime")
    int getHurtTime();

    @Accessor("hurtTime")
    void setHurtTime(int hurtTime);

    @Accessor("maxHurtTime")
    int getMaxHurtTime();

    @Accessor("maxHurtTime")
    void setMaxHurtTime(int maxHurtTime);

    @Accessor("deathTime")
    int getDeathTime();

    @Accessor("deathTime")
    void setDeathTime(int deathTime);

    @Accessor("lastHandSwingProgress")
    float getLastHandSwingProgress();

    @Accessor("lastHandSwingProgress")
    void setLastHandSwingProgress(float lastHandSwingProgress);

    @Accessor("handSwingProgress")
    float getHandSwingProgress();

    @Accessor("handSwingProgress")
    void setHandSwingProgress(float handSwingProgress);

    @Accessor("lastAttackedTicks")
    int getLastAttackedTicks();

    @Accessor("lastAttackedTicks")
    void setLastAttackedTicks(int lastAttackedTicks);

    @Accessor("bodyYaw")
    float getBodyYaw();

    @Accessor("bodyYaw")
    void setBodyYaw(float bodyYaw);


    @Accessor("prevBodyYaw")
    float getPrevBodyYaw();

    @Accessor("prevBodyYaw")
    void setPrevBodyYaw(float prevBodyYaw);

    @Accessor("headYaw")
    float getHeadYaw();

    @Accessor("headYaw")
    void setHeadYaw(float headYaw);

    @Accessor("prevHeadYaw")
    float getPrevHeadYaw();

    @Accessor("prevHeadYaw")
    void setPrevHeadYaw(float prevHeadYaw);

    @Accessor("attackingPlayer")
    PlayerEntity getAttackingPlayer();

    @Accessor("attackingPlayer")
    void setAttackingPlayer(PlayerEntity attackingPlayer);


    @Accessor("playerHitTimer")
    int getPlayerHitTimer();

    @Accessor("playerHitTimer")
    void setPlayerHitTimer(int playerHitTimer);

    @Accessor("dead")
    boolean getDead();

    @Accessor("dead")
    void setDead(boolean dead);

    @Accessor("despawnCounter")
    int getDespawnCounter();

    @Accessor("despawnCounter")
    void setDespawnCounter(int despawnCounter);

    @Accessor("prevStepBobbingAmount")
    float getPrevStepBobbingAmount();

    @Accessor("prevStepBobbingAmount")
    void setPrevStepBobbingAmount(float prevStepBobbingAmount);

    @Accessor("stepBobbingAmount")
    float getStepBobbingAmount();

    @Accessor("stepBobbingAmount")
    void setStepBobbingAmount(float stepBobbingAmount);

    @Accessor("lookDirection")
    float getLookDirection();

    @Accessor("lookDirection")
    void setLookDirection(float lookDirection);

    @Accessor("prevLookDirection")
    float getPrevLookDirection();

    @Accessor("prevLookDirection")
    void setPrevLookDirection(float prevLookDirection);

    @Accessor("field_6215")
    float getField_6215();

    @Accessor("field_6215")
    void setField_6215(float field_6215);

    @Accessor("scoreAmount")
    int getScoreAmount();

    @Accessor("scoreAmount")
    void setScoreAmount(int scoreAmount);

    @Accessor("lastDamageTaken")
    float getLastDamageTaken();

    @Accessor("lastDamageTaken")
    void setLastDamageTaken(float lastDamageTaken);

    @Accessor("jumping")
    boolean getJumping();

    @Accessor("jumping")
    void setJumping(boolean jumping);


    @Accessor("sidewaysSpeed")
    float getSidewaysSpeed();

    @Accessor("sidewaysSpeed")
    void setSidewaysSpeed(float sidewaysSpeed);


    @Accessor("upwardSpeed")
    float getUpwardSpeed();

    @Accessor("upwardSpeed")
    void setUpwardSpeed(float upwardSpeed);

    @Accessor("forwardSpeed")
    float getForwardSpeed();

    @Accessor("forwardSpeed")
    void setForwardSpeed(float forwardSpeed);



    @Accessor("bodyTrackingIncrements")
    int getBodyTrackingIncrements();

    @Accessor("bodyTrackingIncrements")
    void setBodyTrackingIncrements(int bodyTrackingIncrements);


    @Accessor("serverX")
    double getServerX();

    @Accessor("serverX")
    void setServerX(double serverX);


    @Accessor("serverY")
    double getServerY();

    @Accessor("serverY")
    void setServerY(double serverY);

    @Accessor("serverZ")
    double getServerZ();

    @Accessor("serverZ")
    void setServerZ(double serverZ);


    @Accessor("serverYaw")
    double getServerYaw();

    @Accessor("serverYaw")
    void setServerYaw(double serverYaw);


    @Accessor("serverPitch")
    double getServerPitch();

    @Accessor("serverPitch")
    void setServerPitch(double serverPitch);

    @Accessor("serverHeadYaw")
    double getServerHeadYaw();

    @Accessor("serverHeadYaw")
    void setServerHeadYaw(double serverHeadYaw);

    @Accessor("headTrackingIncrements")
    int getHeadTrackingIncrements();

    @Accessor("headTrackingIncrements")
    void setHeadTrackingIncrements(int headTrackingIncrements);

    @Accessor("effectsChanged")
    boolean getEffectsChanged();

    @Accessor("effectsChanged")
    void setEffectsChanged(boolean effectsChanged);


    @Accessor("attacker")
    LivingEntity getAttacker();

    @Accessor("attacker")
    void setAttacker(LivingEntity attacker);

    @Accessor("lastAttackedTime")
    int getLastAttackedTime();

    @Accessor("lastAttackedTime")
    void setLastAttackedTime(int lastAttackedTime);


    @Accessor("attacking")
    LivingEntity getAttacking();

    @Accessor("attacking")
    void setAttacking(LivingEntity attacking);


    @Accessor("lastAttackTime")
    int getLastAttackTime();

    @Accessor("lastAttackTime")
    void setLastAttackTime(int lastAttackTime);


    @Accessor("movementSpeed")
    float getMovementSpeed();

    @Accessor("movementSpeed")
    void setMovementSpeed(float movementSpeed);

    @Accessor("jumpingCooldown")
    int getJumpingCooldown();

    @Accessor("jumpingCooldown")
    void setJumpingCooldown(int jumpingCooldown);

    @Accessor("absorptionAmount")
    float getAbsorptionAmount();

    @Accessor("absorptionAmount")
    void setAbsorptionAmount(float absorptionAmount);

    @Accessor("activeItemStack")
    ItemStack getActiveItemStack();

    @Accessor("activeItemStack")
    void setActiveItemStack(ItemStack activeItemStack);

    @Accessor("itemUseTimeLeft")
    int getItemUseTimeLeft();

    @Accessor("itemUseTimeLeft")
    void setItemUseTimeLeft(int itemUseTimeLeft);

    @Accessor("fallFlyingTicks")
    int getFallFlyingTicks();

    @Accessor("fallFlyingTicks")
    void setFallFlyingTicks(int fallFlyingTicks);

    @Accessor("lastBlockPos")
    BlockPos getLastBlockPos();

    @Accessor("lastBlockPos")
    void setLastBlockPos(BlockPos lastBlockPos);

    @Accessor("climbingPos")
    Optional<BlockPos> getClimbingPos();

    @Accessor("climbingPos")
    void setClimbingPos(Optional<BlockPos> climbingPos);

    @Accessor("lastDamageSource")
    DamageSource getLastDamageSource();

    @Accessor("lastDamageSource")
    void setLastDamageSource(DamageSource lastDamageSource);

    @Accessor("lastDamageTime")
    long getLastDamageTime();

    @Accessor("lastDamageTime")
    void setLastDamageTime(long lastDamageTime);

    @Accessor("riptideTicks")
    int getRiptideTicks();

    @Accessor("riptideTicks")
    void setRiptideTicks(int riptideTicks);

    @Accessor("riptideAttackDamage")
    float getRiptideAttackDamage();

    @Accessor("riptideAttackDamage")
    void setRiptideAttackDamage(float riptideAttackDamage);

    @Accessor("riptideStack")
    ItemStack getRiptideStack();

    @Accessor("riptideStack")
    void setRiptideStack(ItemStack riptideStack);

    @Accessor("leaningPitch")
    float getLeaningPitch();

    @Accessor("leaningPitch")
    void setLeaningPitch(float leaningPitch);

    @Accessor("lastLeaningPitch")
    float getLastLeaningPitch();

    @Accessor("lastLeaningPitch")
    void setLastLeaningPitch(float lastLeaningPitch);

    @Accessor("brain")
    Brain<?> getBrain();

    @Accessor("brain")
    void setBrain(Brain<?> brain);

    @Accessor("experienceDroppingDisabled")
    boolean getExperienceDroppingDisabled();

    @Accessor("experienceDroppingDisabled")
    void setExperienceDroppingDisabled(boolean experienceDroppingDisabled);

    @Accessor("prevScale")
    float getPrevScale();

    @Accessor("prevScale")
    void setPrevScale(float prevScale);

    @Accessor("jumping")
    boolean isJumping();

    @Accessor("USING_RIPTIDE_FLAG")
    static int getUSING_RIPTIDE_FLAG() {
        throw new AssertionError();
    }

    @Invoker("isImmobile")
    boolean invokeIsImmobile();

    @Invoker("removePowderSnowSlow")
    void invokeRemovePowderSnowSlow();

    @Invoker("tickCramming")
    void invokeTickCramming();

    @Invoker("addPowderSnowSlowIfNeeded")
    void invokeAddPowderSnowSlowIfNeeded();

    @Invoker("lerpHeadYaw")
    void invokeLerpHeadYaw(int headTrackingIncrements, double serverHeadYaw);

    @Invoker("pushAway")
    void invokePushAway(Entity entity);

    @Invoker("setLivingFlag")
    void invokeSetLivingFlag(int mask, boolean value);

    @Invoker("shouldSwimInFluids")
    boolean invokeShouldSwimInFluids();

    @Invoker("tickFallFlying")
    void invokeTickFallFlying();

    @Invoker("tickNewAi")
    void invokeTickNewAi();

    @Invoker("swimUpward")
    void invokeSwimUpward(TagKey<Fluid> fluid);

    @Invoker("attackLivingEntity")
    void invokeAttackLivingEntity(LivingEntity target);

    @Invoker("travelControlled")
    void invokeTravelControlled(PlayerEntity controllingPlayer, Vec3d movementInput);

    @Invoker("fall")
    void invokeFall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition);

    @Invoker("getControlledMovementInput")
    Vec3d invokeGetControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput);
}
