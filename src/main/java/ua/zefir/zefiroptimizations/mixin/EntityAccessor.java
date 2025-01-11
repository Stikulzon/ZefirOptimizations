package ua.zefir.zefiroptimizations.mixin;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.math.ChunkPos;
import  net.minecraft.fluid.Fluid;
import net.minecraft.world.dimension.PortalManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;
import java.util.UUID;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("renderDistanceMultiplier")
    static double getRenderDistanceMultiplier() {
        throw new AssertionError();
    }

    @Accessor("id")
    int getId();

    @Accessor("intersectionChecked")
    boolean isIntersectionChecked();

    @Accessor("passengerList")
    ImmutableList<Entity> getPassengerList();

    @Accessor("ridingCooldown")
    int getRidingCooldown();

    @Nullable
    @Accessor("vehicle")
    Entity getVehicle();

//    @Accessor("world")
//    World getWorld();

    @Accessor("prevX")
    double getPrevX();

    @Accessor("prevY")
    double getPrevY();

    @Accessor("prevZ")
    double getPrevZ();

//    @Accessor("pos")
//    Vec3d getPos();

//    @Accessor("blockPos")
//    BlockPos getBlockPos();
//
//    @Accessor("chunkPos")
//    ChunkPos getChunkPos();
//
//    @Accessor("velocity")
//    Vec3d getVelocity();
//
//    @Accessor("yaw")
//    float getYaw();
//
//    @Accessor("pitch")
//    float getPitch();
//
    @Accessor("prevYaw")
    float getPrevYaw();

    @Accessor("prevPitch")
    float getPrevPitch();
//
//    @Accessor("boundingBox")
//    Box getBoundingBox();
//
//    @Accessor("onGround")
//    boolean isOnGround();

    @Accessor("horizontalCollision")
    boolean isHorizontalCollision();

    @Accessor("verticalCollision")
    boolean isVerticalCollision();

    @Accessor("groundCollision")
    boolean isGroundCollision();

    @Accessor("collidedSoftly")
    boolean hasCollidedSoftly();

    @Accessor("velocityModified")
    boolean isVelocityModified();
    
    @Accessor("movementMultiplier")
    Vec3d getMovementMultiplier();

    @Nullable
    @Accessor("removalReason")
    Entity.RemovalReason getRemovalReason();

    @Accessor("prevHorizontalSpeed")
    float getPrevHorizontalSpeed();

    @Accessor("horizontalSpeed")
    float getHorizontalSpeed();

    @Accessor("distanceTraveled")
    float getDistanceTraveled();

    @Accessor("speed")
    float getSpeed();

    @Accessor("fallDistance")
    float getFallDistance();

    
    @Accessor("nextStepSoundDistance")
    float getNextStepSoundDistance();

    @Accessor("lastRenderX")
    double getLastRenderX();

    @Accessor("lastRenderY")
    double getLastRenderY();

    @Accessor("lastRenderZ")
    double getLastRenderZ();

    @Accessor("noClip")
    boolean isNoClip();

    @Accessor("age")
    int getAge();

//    @Accessor("fireTicks")
//    int getFireTicks();

    @Accessor("fireTicks")
    void setFireTicks(int fireTicks);

//    @Accessor("touchingWater")
//    boolean isTouchingWater();

    @Accessor("touchingWater")
    void setTouchingWater(boolean touchingWater);

    @Accessor("fluidHeight")
    Object2DoubleMap<TagKey<Fluid>> getFluidHeight();

    @Accessor("fluidHeight")
    void setFluidHeight(Object2DoubleMap<TagKey<Fluid>> fluidHeight);

    
    @Accessor("submergedInWater")
    boolean isSubmergedInWater();

    @Accessor("submergedInWater")
    void setSubmergedInWater(boolean submergedInWater);

    @Accessor("timeUntilRegen")
    int getTimeUntilRegen();

    @Accessor("timeUntilRegen")
    void setTimeUntilRegen(int timeUntilRegen);

    @Accessor("firstUpdate")
    boolean isFirstUpdate();

    @Accessor("firstUpdate")
    void setFirstUpdate(boolean firstUpdate);

    @Accessor("velocityDirty")
    void setVelocityDirty(boolean velocityDirty);

    @Accessor("portalCooldown")
    void setPortalCooldown(int portalCooldown);

    @Accessor("invulnerable")
    void setInvulnerable(boolean invulnerable);

    @Accessor("glowing")
    void setGlowing(boolean glowing);

    @Accessor("pistonMovementTick")
    void setPistonMovementTick(long pistonMovementTick);

    // Dimensions is a Vec3d.  Ensure your EntityAccess implements a setDimensions if it's a custom type.  If it's Vec3d directly:
    @Accessor("dimensions")
    void setDimensions(EntityDimensions dimensions);

    @Accessor("standingEyeHeight")
    void setStandingEyeHeight(float standingEyeHeight);


    @Accessor("inPowderSnow")
    void setInPowderSnow(boolean inPowderSnow);

    @Accessor("wasInPowderSnow")
    void setWasInPowderSnow(boolean wasInPowderSnow);

    @Accessor("wasOnFire")
    void setWasOnFire(boolean wasOnFire);

    @Accessor("supportingBlockPos")
    void setSupportingBlockPos(Optional<BlockPos> supportingBlockPos);

    @Accessor("lastChimeAge")
    void setLastChimeAge(int lastChimeAge);

    @Accessor("lastChimeIntensity")
    void setLastChimeIntensity(float lastChimeIntensity);

    @Accessor("hasVisualFire") // Or getHasVisualFire depending on the accessor method
    void setHasVisualFire(boolean hasVisualFire);

    @Accessor("ignoreCameraFrustum")
    boolean ignoresCameraFrustum();

    @Accessor("ignoreCameraFrustum")
    void setIgnoresCameraFrustum(boolean ignoresCameraFrustum);

    @Accessor("velocityDirty")
    boolean isVelocityDirty();

    @Nullable
    @Accessor("portalManager")
    PortalManager getPortalManager();

    @Accessor("portalCooldown")
    int getPortalCooldown();

    @Accessor("invulnerable")
    boolean isInvulnerable();

    @Accessor("uuid")
    UUID getUuid();

    @Accessor("uuidString")
    String getUuidString();

    @Accessor("glowing")
    boolean isGlowing();

    
    @Accessor("pistonMovementTick")
    long getPistonMovementTick();

    @Accessor("dimensions")
    EntityDimensions getDimensions();

    @Accessor("standingEyeHeight")
    float getStandingEyeHeight();

    @Accessor("inPowderSnow")
    boolean isInPowderSnow();

    @Accessor("wasInPowderSnow")
    boolean wasInPowderSnow();

    @Accessor("wasOnFire")
    boolean wasOnFire();

    @Accessor("supportingBlockPos")
    Optional<BlockPos> getSupportingBlockPos();

    @Accessor("forceUpdateSupportingBlockPos")
    boolean isForceUpdateSupportingBlockPos();

    @Accessor("forceUpdateSupportingBlockPos")
    void setForceUpdateSupportingBlockPos(boolean forceUpdateSupportingBlockPos);

    @Accessor("lastChimeIntensity")
    float getLastChimeIntensity();

    @Accessor("lastChimeAge")
    int getLastChimeAge();

    @Accessor("hasVisualFire")
    boolean hasVisualFire();

    
    @Nullable
    @Accessor("stateAtPos")
    BlockState getStateAtPos();

    @Accessor("dataTracker")
    DataTracker getDataTracker();

    @Accessor("stateAtPos")
    void setStateAtPos(BlockState stateAtPos);

    @Accessor("renderDistanceMultiplier")
    static void setRenderDistanceMultiplier(double value) {
        throw new AssertionError();
    }

    @Accessor("id")
    void setId(int id);

    @Accessor("intersectionChecked")
    void setIntersectionChecked(boolean intersectionChecked);

    @Accessor("passengerList")
    void setPassengerList(ImmutableList<Entity> passengerList); // Be cautious modifying!

    @Accessor("ridingCooldown")
    void setRidingCooldown(int ridingCooldown);

    @Accessor("vehicle")
    void setVehicle(@Nullable Entity vehicle);

    @Accessor("world")
    void setWorld(World world);

    @Accessor("prevX")
    void setPrevX(double prevX);

    @Accessor("prevY")
    void setPrevY(double prevY);

    @Accessor("prevZ")
    void setPrevZ(double prevZ);

    @Accessor("pos")
    void setPos(Vec3d pos); // Use with caution!

    @Accessor("blockPos")
    void setBlockPos(BlockPos blockPos); // Use with caution!

    @Accessor("chunkPos")
    void setChunkPos(ChunkPos chunkPos); // Use with caution!

    @Accessor("velocity")
    void setVelocity(Vec3d velocity);

    @Accessor("yaw")
    void setYaw(float yaw);

    @Accessor("pitch")
    void setPitch(float pitch);

    @Accessor("prevYaw")
    void setPrevYaw(float prevYaw);

    @Accessor("prevPitch")
    void setPrevPitch(float prevPitch);

    @Accessor("boundingBox")
    void setBoundingBox(Box boundingBox);

    @Accessor("onGround")
    void setOnGround(boolean onGround);

    @Accessor("horizontalCollision")
    void setHorizontalCollision(boolean horizontalCollision);

    @Accessor("verticalCollision")
    void setVerticalCollision(boolean verticalCollision);

    @Accessor("groundCollision")
    void setGroundCollision(boolean groundCollision);

    @Accessor("collidedSoftly")
    void setCollidedSoftly(boolean collidedSoftly);

    @Accessor("velocityModified")
    void setVelocityModified(boolean velocityModified);

    @Accessor("movementMultiplier")
    void setMovementMultiplier(Vec3d movementMultiplier);

    @Accessor("removalReason")
    void setRemovalReason(@Nullable Entity.RemovalReason removalReason);

    @Accessor("prevHorizontalSpeed")
    void setPrevHorizontalSpeed(float prevHorizontalSpeed);

    @Accessor("horizontalSpeed")
    void setHorizontalSpeed(float horizontalSpeed);

    @Accessor("distanceTraveled")
    void setDistanceTraveled(float distanceTraveled);

    @Accessor("speed")
    void setSpeed(float speed);

    @Accessor("fallDistance")
    void setFallDistance(float fallDistance);

    @Accessor("nextStepSoundDistance")
    void setNextStepSoundDistance(float nextStepSoundDistance);

    @Accessor("lastRenderX")
    void setLastRenderX(double lastRenderX);

    @Accessor("lastRenderY")
    void setLastRenderY(double lastRenderY);

    @Accessor("lastRenderZ")
    void setLastRenderZ(double lastRenderZ);

    @Accessor("noClip")
    void setNoClip(boolean noClip);

    @Invoker("lerpPosAndRotation")
    void invokeLerpPosAndRotation(int step, double x, double y, double z, double yaw, double pitch);

    @Invoker("canClimb")
    boolean invokeCanClimb(BlockState state);

    @Invoker("getMoveEffect")
    Entity.MoveEffect invokeGetMoveEffect();

    @Invoker("adjustMovementForCollisions")
    Vec3d invokeAdjustMovementForCollisions(Vec3d movement);

    @Invoker("adjustMovementForSneaking")
    Vec3d invokeAdjustMovementForSneaking(Vec3d movement, MovementType type);

    @Invoker("adjustMovementForPiston")
    Vec3d invokeAdjustMovementForPiston(Vec3d movement);

    @Invoker("hasCollidedSoftly")
    boolean invokeHasCollidedSoftly(Vec3d adjustedMovement) ;
}
