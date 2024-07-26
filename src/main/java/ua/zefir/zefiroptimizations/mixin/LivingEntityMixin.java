package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.Flutterer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    protected float lookDirection;
    @Shadow
    protected float stepBobbingAmount;
    @Shadow
    protected float prevScale;
    @Shadow
    protected boolean jumping;
    @Shadow
    private int jumpingCooldown;
    @Shadow
    protected int fallFlyingTicks;
    @Shadow
    protected int bodyTrackingIncrements;
    @Shadow
    protected int headTrackingIncrements;
    @Shadow
    protected int riptideTicks;
    @Shadow
    protected double serverX;
    @Shadow
    protected double serverY;
    @Shadow
    protected double serverZ;
    @Shadow
    protected double serverYaw;
    @Shadow
    protected double serverPitch;
    @Shadow
    protected double serverHeadYaw;
    @Shadow
    protected abstract boolean isSleepingInBed();
    @Shadow
    protected abstract boolean isImmobile();
    @Shadow
    protected abstract boolean shouldSwimInFluids();
    @Shadow
    protected abstract float getBaseMovementSpeedMultiplier();
    @Shadow
    protected abstract float turnHead(float bodyRotation, float headRotation);
    @Shadow
    protected abstract void tickActiveItemStack();
    @Shadow
    protected abstract void updateLeaningPitch();
    @Shadow
    protected abstract void tickNewAi();
    @Shadow
    protected abstract void addPowderSnowSlowIfNeeded();
    @Shadow
    protected abstract void removePowderSnowSlow();
    @Shadow
    protected abstract void tickCramming();
    @Shadow
    protected abstract void tickFallFlying();
    @Shadow
    protected abstract SoundEvent getFallSound(int distance);
    @Shadow
    protected abstract void tickRiptide(Box a, Box b);
    @Shadow
    protected abstract void swimUpward(TagKey<Fluid> fluid);
    @Shadow
    protected abstract void travelControlled(PlayerEntity controllingPlayer, Vec3d movementInput);
    @Shadow
    protected abstract void lerpHeadYaw(int headTrackingIncrements, double serverHeadYaw);

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V", shift = At.Shift.AFTER), cancellable = true)
    private void tick(CallbackInfo ci){
//        if(isOptimizeVillagers) {
        this.cutomTick();
        ci.cancel();
//        }
    }

    @Inject(method = "tickMovement", at = @At(value = "HEAD"), cancellable = true)
    private void tickMovement(CallbackInfo ci){
//        if(isOptimizeVillagers) {
        this.customTickMovement();
        ci.cancel();
//        }
    }

    @Inject(method = "travel", at = @At(value = "HEAD"), cancellable = true)
    private void travel(Vec3d movementInput, CallbackInfo ci){
//        if(isOptimizeVillagers) {
        this.customTravel(movementInput);
        ci.cancel();
//        }
    }

    @Unique
    public void customTickMovement() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (jumpingCooldown > 0) {
            jumpingCooldown--;
        }

        if (self.isLogicalSideForUpdatingMovement()) {
            bodyTrackingIncrements = 0;
            self.updateTrackedPosition(self.getX(), self.getY(), self.getZ());
        }

        if (bodyTrackingIncrements > 0) {
            self.lerpPosAndRotation(bodyTrackingIncrements, serverX, serverY, serverZ, serverYaw, serverPitch);
            bodyTrackingIncrements--;
        } else if (!self.canMoveVoluntarily()) {
            self.setVelocity(self.getVelocity().multiply(0.98));
        }

        if (headTrackingIncrements > 0) {
            lerpHeadYaw(headTrackingIncrements, serverHeadYaw);
            headTrackingIncrements--;
        }

        Vec3d velocity = self.getVelocity();
        double d = Math.abs(velocity.x) < 0.003 ? 0.0 : velocity.x;
        double e = Math.abs(velocity.y) < 0.003 ? 0.0 : velocity.y;
        double f = Math.abs(velocity.z) < 0.003 ? 0.0 : velocity.z;
        self.setVelocity(d, e, f);

        World world = self.getWorld();
        Profiler profiler = world.getProfiler();

        profiler.push("ai");
        if (isImmobile()) {
            jumping = false;
            self.sidewaysSpeed = 0.0F;
            self.forwardSpeed = 0.0F;
        } else if (self.canMoveVoluntarily()) {
            profiler.push("newAi");
            tickNewAi();
            profiler.pop();
        }
        profiler.pop();

        profiler.push("jump");
        if (jumping && shouldSwimInFluids()) {
            double fluidHeight = self.isInLava() ? self.getFluidHeight(FluidTags.LAVA) : self.getFluidHeight(FluidTags.WATER);
            boolean touchingWater = self.isTouchingWater() && fluidHeight > 0.0;
            double swimHeight = self.getSwimHeight();

            if ((!touchingWater || (self.isOnGround() && fluidHeight <= swimHeight)) && jumpingCooldown == 0) {
                self.jump();
                jumpingCooldown = 10;
            } else if (self.isInLava() && !self.isOnGround() || (touchingWater && fluidHeight > swimHeight)) {
                swimUpward(self.isInLava() ? FluidTags.LAVA : FluidTags.WATER);
            }
        } else {
            jumpingCooldown = 0;
        }
        profiler.pop();

        profiler.push("travel");
        self.sidewaysSpeed *= 0.98F;
        self.forwardSpeed *= 0.98F;
        tickFallFlying();

        Vec3d travelVec = new Vec3d(self.sidewaysSpeed, self.upwardSpeed, self.forwardSpeed);
        if (self.getControllingPassenger() instanceof PlayerEntity player && self.isAlive()) {
            travelControlled(player, travelVec);
        } else {
            self.travel(travelVec);
        }
        profiler.pop();

        profiler.push("freezing");
        if (!world.isClient && !self.isDead()) {
            int frozenTicks = self.getFrozenTicks();
            if (self.inPowderSnow && self.canFreeze()) {
                self.setFrozenTicks(Math.min(self.getMinFreezeDamageTicks(), frozenTicks + 1));
            } else {
                self.setFrozenTicks(Math.max(0, frozenTicks - 2));
            }
        }
        removePowderSnowSlow();
        addPowderSnowSlowIfNeeded();

        if (!world.isClient && self.age % 40 == 0 && self.isFrozen() && self.canFreeze()) {
            self.damage(self.getDamageSources().freeze(), 1.0F);
        }
        profiler.pop();

        profiler.push("push");
        if (riptideTicks > 0) {
            riptideTicks--;
            tickRiptide(self.getBoundingBox(), self.getBoundingBox());
        }
        tickCramming();
        profiler.pop();

        if (!world.isClient && self.hurtByWater() && self.isWet()) {
            self.damage(self.getDamageSources().drown(), 1.0F);
        }
    }

    @Unique
    public void customTravel(Vec3d movementInput) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (!self.isLogicalSideForUpdatingMovement()) {
            return;
        }

        double gravity = self.getFinalGravity();
        boolean isFalling = self.getVelocity().y <= 0.0;
        if (isFalling && self.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            gravity = Math.min(gravity, 0.01);
        }

        FluidState fluidState = self.getWorld().getFluidState(self.getBlockPos());

        if (self.isTouchingWater() && shouldSwimInFluids() && !self.canWalkOnFluid(fluidState)) {
            handleWaterMovement(self, movementInput, gravity, isFalling);
        } else if (self.isInLava() && shouldSwimInFluids() && !self.canWalkOnFluid(fluidState)) {
            handleLavaMovement(self, movementInput, gravity, isFalling);
        } else if (self.isFallFlying()) {
            handleFallFlying(self, movementInput, gravity);
        } else {
            handleDefaultMovement(self, movementInput, gravity);
        }

        self.updateLimbs(this instanceof Flutterer);
    }

    @Unique
    private void handleWaterMovement(LivingEntity self, Vec3d movementInput, double gravity, boolean isFalling) {
        double initialY = self.getY();
        float baseSpeed = self.isSprinting() ? 0.9F : getBaseMovementSpeedMultiplier();
        float velocityMultiplier = 0.02F;
        float waterEfficiency = (float) self.getAttributeValue(EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY);
        if (!self.isOnGround()) {
            waterEfficiency *= 0.5F;
        }

        if (waterEfficiency > 0.0F) {
            baseSpeed += (0.54600006F - baseSpeed) * waterEfficiency;
            velocityMultiplier += (self.getMovementSpeed() - velocityMultiplier) * waterEfficiency;
        }

        if (self.hasStatusEffect(StatusEffects.DOLPHINS_GRACE)) {
            baseSpeed = 0.96F;
        }

        self.updateVelocity(velocityMultiplier, movementInput);
        self.move(MovementType.SELF, self.getVelocity());
        Vec3d velocity = self.getVelocity();

        if (self.horizontalCollision && self.isClimbing()) {
            velocity = new Vec3d(velocity.x, 0.2, velocity.z);
        }

        self.setVelocity(velocity.multiply(baseSpeed, 0.8F, baseSpeed));
        Vec3d newVelocity = self.applyFluidMovingSpeed(gravity, isFalling, self.getVelocity());
        self.setVelocity(newVelocity);

        if (self.horizontalCollision && self.doesNotCollide(newVelocity.x, newVelocity.y + 0.6F - self.getY() + initialY, newVelocity.z)) {
            self.setVelocity(newVelocity.x, 0.3F, newVelocity.z);
        }
    }

    @Unique
    private void handleLavaMovement(LivingEntity self, Vec3d movementInput, double gravity, boolean isFalling) {
        double initialY = self.getY();
        self.updateVelocity(0.02F, movementInput);
        self.move(MovementType.SELF, self.getVelocity());

        if (self.getFluidHeight(FluidTags.LAVA) <= self.getSwimHeight()) {
            self.setVelocity(self.getVelocity().multiply(0.5, 0.8F, 0.5));
            Vec3d newVelocity = self.applyFluidMovingSpeed(gravity, isFalling, self.getVelocity());
            self.setVelocity(newVelocity);
        } else {
            self.setVelocity(self.getVelocity().multiply(0.5));
        }

        if (gravity != 0.0) {
            self.setVelocity(self.getVelocity().add(0.0, -gravity / 4.0, 0.0));
        }

        Vec3d velocity = self.getVelocity();
        if (self.horizontalCollision && self.doesNotCollide(velocity.x, velocity.y + 0.6F - self.getY() + initialY, velocity.z)) {
            self.setVelocity(velocity.x, 0.3F, velocity.z);
        }
    }

    @Unique
    private void handleFallFlying(LivingEntity self, Vec3d movementInput, double gravity) {
        self.limitFallDistance();
        Vec3d velocity = self.getVelocity();
        Vec3d rotationVector = self.getRotationVector();
        float pitchRadians = self.getPitch() * (float) (Math.PI / 180.0);
        double horizontalLength = Math.sqrt(rotationVector.x * rotationVector.x + rotationVector.z * rotationVector.z);
        double velocityHorizontalLength = velocity.horizontalLength();
        double rotationVectorLength = rotationVector.length();
        double cosinePitch = Math.cos(pitchRadians);
        cosinePitch = cosinePitch * cosinePitch * Math.min(1.0, rotationVectorLength / 0.4);
        velocity = self.getVelocity().add(0.0, gravity * (-1.0 + cosinePitch * 0.75), 0.0);

        if (velocity.y < 0.0 && horizontalLength > 0.0) {
            double negativeVelocityY = velocity.y * -0.1 * cosinePitch;
            velocity = velocity.add(rotationVector.x * negativeVelocityY / horizontalLength, negativeVelocityY, rotationVector.z * negativeVelocityY / horizontalLength);
        }

        if (pitchRadians < 0.0F && horizontalLength > 0.0) {
            double negativeSinPitch = velocityHorizontalLength * (double) (-MathHelper.sin(pitchRadians)) * 0.04;
            velocity = velocity.add(-rotationVector.x * negativeSinPitch / horizontalLength, negativeSinPitch * 3.2, -rotationVector.z * negativeSinPitch / horizontalLength);
        }

        if (horizontalLength > 0.0) {
            velocity = velocity.add((rotationVector.x / horizontalLength * velocityHorizontalLength - velocity.x) * 0.1, 0.0, (rotationVector.z / horizontalLength * velocityHorizontalLength - velocity.z) * 0.1);
        }

        self.setVelocity(velocity.multiply(0.99F, 0.98F, 0.99F));
        self.move(MovementType.SELF, self.getVelocity());

        if (self.horizontalCollision && !self.getWorld().isClient) {
            double finalHorizontalLength = self.getVelocity().horizontalLength();
            double difference = velocityHorizontalLength - finalHorizontalLength;
            float damage = (float) (difference * 10.0 - 3.0);
            if (damage > 0.0F) {
                self.playSound(getFallSound((int) damage), 1.0F, 1.0F);
                self.damage(self.getDamageSources().flyIntoWall(), damage);
            }
        }

        if (self.isOnGround() && !self.getWorld().isClient) {
            self.setFlag(Entity.FALL_FLYING_FLAG_INDEX, false);
        }
    }

    private void handleDefaultMovement(LivingEntity self, Vec3d movementInput, double gravity) {
        BlockPos blockPos = self.getVelocityAffectingPos();
        float blockSlipperiness = self.getWorld().getBlockState(blockPos).getBlock().getSlipperiness();
        float slipperiness = self.isOnGround() ? blockSlipperiness * 0.91F : 0.91F;
        Vec3d velocity = self.applyMovementInput(movementInput, blockSlipperiness);
        double yVelocity = velocity.y;

        if (self.hasStatusEffect(StatusEffects.LEVITATION)) {
            yVelocity += (0.05 * (double) (self.getStatusEffect(StatusEffects.LEVITATION).getAmplifier() + 1) - velocity.y) * 0.2;
        } else if (!self.getWorld().isClient || self.getWorld().isChunkLoaded(blockPos)) {
            yVelocity -= gravity;
        } else if (self.getY() > (double) self.getWorld().getBottomY()) {
            yVelocity = -0.1;
        } else {
            yVelocity = 0.0;
        }

        if (self.hasNoDrag()) {
            self.setVelocity(velocity.x, yVelocity, velocity.z);
        } else {
            self.setVelocity(velocity.x * (double) slipperiness, this instanceof Flutterer ? yVelocity * (double) slipperiness : yVelocity * 0.98F, velocity.z * (double) slipperiness);
        }
    }

    @Unique
    public void cutomTick() {
        LivingEntity self = (LivingEntity) (Object) this;
        this.tickActiveItemStack();
        this.updateLeaningPitch();

        if (!self.getWorld().isClient) {
            handleArrowsAndStingers();
            handleEquipmentChanges();
            handleSleeping();
        }

        if (!self.isRemoved()) {
            self.tickMovement();
        }

        updateMovement();
        updateBodyYaw();
        updateStepBobbingAmount();
        updateHeadTurn();
        normalizeAngles();
        this.lookDirection += updateHeadTurn();

        handleFallFlying();
        handleSleepingPitch();
        updateAttributesIfNeeded();
    }

    @Unique
    private void handleArrowsAndStingers() {
        LivingEntity self = (LivingEntity) (Object) this;
        int i = self.getStuckArrowCount();
        if (i > 0) {
            updateTimer(i, self.stuckArrowTimer, self::setStuckArrowCount);
        }

        int j = self.getStingerCount();
        if (j > 0) {
            updateTimer(j, self.stuckStingerTimer, self::setStingerCount);
        }
    }

    @Unique
    private void updateTimer(int count, int timer, Consumer<Integer> setCount) {
        if (timer <= 0) {
            timer = 20 * (30 - count);
        }
        timer--;
        if (timer <= 0) {
            setCount.accept(count - 1);
        }
    }

    @Unique
    private void handleEquipmentChanges() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.age % 20 == 0) {
            self.getDamageTracker().update();
        }
    }

    @Unique
    private void handleSleeping() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isSleeping() && !this.isSleepingInBed()) {
            self.wakeUp();
        }
    }

    @Unique
    private void updateMovement() {
        LivingEntity self = (LivingEntity) (Object) this;
        double d = self.getX() - self.prevX;
        double e = self.getZ() - self.prevZ;
        float f = (float)(d * d + e * e);
        float g = self.bodyYaw;

        if (f > 0.0025000002F) {
            updateYaw(d, e, f, g);
        }

        if (self.handSwingProgress > 0.0F) {
            g = self.getYaw();
        }
    }

    @Unique
    private void updateYaw(double d, double e, float f, float g) {
        LivingEntity self = (LivingEntity) (Object) this;
        float h = (float)Math.sqrt(f) * 3.0F;
        float l = (float) MathHelper.atan2(e, d) * (180.0F / (float)Math.PI) - 90.0F;
        float m = MathHelper.abs(MathHelper.wrapDegrees(self.getYaw()) - l);

        if (95.0F < m && m < 265.0F) {
            g = l - 180.0F;
        } else {
            g = l;
        }

        if (!self.isOnGround()) {
            this.stepBobbingAmount = this.stepBobbingAmount + (1.0F - this.stepBobbingAmount) * 0.3F;
        }
    }

    @Unique
    private void updateBodyYaw() {
        LivingEntity self = (LivingEntity) (Object) this;
        while (self.getYaw() - self.prevYaw < -180.0F) {
            self.prevYaw -= 360.0F;
        }
        while (self.getYaw() - self.prevYaw >= 180.0F) {
            self.prevYaw += 360.0F;
        }
        while (self.bodyYaw - self.prevBodyYaw < -180.0F) {
            self.prevBodyYaw -= 360.0F;
        }
        while (self.bodyYaw - self.prevBodyYaw >= 180.0F) {
            self.prevBodyYaw += 360.0F;
        }
    }

    @Unique
    private void updateStepBobbingAmount() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.isOnGround()) {
            this.stepBobbingAmount = this.stepBobbingAmount + (0.0F - this.stepBobbingAmount) * 0.3F;
        }
    }

    @Unique
    private float updateHeadTurn() {
        LivingEntity self = (LivingEntity) (Object) this;
        float h = 0.0F;
        self.getWorld().getProfiler().push("headTurn");
        h = this.turnHead(self.bodyYaw, h);
        self.getWorld().getProfiler().pop();
        return h;
    }

    @Unique
    private void normalizeAngles() {
        LivingEntity self = (LivingEntity) (Object) this;
        normalizeAngle(self::getYaw, self.prevYaw);
        normalizeAngle(self::getBodyYaw, self.prevBodyYaw);
        normalizeAngle(self::getPitch, self.prevPitch);
        normalizeAngle(self::getHeadYaw, self.prevHeadYaw);
    }

    @Unique
    private void normalizeAngle(Supplier<Float> angle, float prevAngle) {
        while (angle.get() - prevAngle < -180.0F) {
            prevAngle -= 360.0F;
        }
        while (angle.get() - prevAngle >= 180.0F) {
            prevAngle += 360.0F;
        }
    }

    @Unique
    private void handleFallFlying() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isFallFlying()) {
            this.fallFlyingTicks++;
        } else {
            this.fallFlyingTicks = 0;
        }
    }

    @Unique
    private void handleSleepingPitch() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isSleeping()) {
            self.setPitch(0.0F);
        }
    }

    @Unique
    private void updateAttributesIfNeeded() {
        LivingEntity self = (LivingEntity) (Object) this;
        float l = self.getScale();
        if (l != this.prevScale) {
            this.prevScale = l;
            self.calculateDimensions();
        }
    }

}
