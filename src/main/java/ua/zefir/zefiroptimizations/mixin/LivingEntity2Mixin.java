//package ua.zefir.zefiroptimizations.mixin;
//
//import akka.actor.ActorRef;
//import akka.actor.Props;
//import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//import net.minecraft.entity.EntityType;
//import net.minecraft.entity.LivingEntity;
//import net.minecraft.entity.effect.StatusEffects;
//import net.minecraft.entity.player.PlayerEntity;
//import net.minecraft.fluid.Fluid;
//import net.minecraft.registry.tag.FluidTags;
//import net.minecraft.registry.tag.TagKey;
//import net.minecraft.server.MinecraftServer;
//import net.minecraft.server.world.ServerWorld;
//import net.minecraft.sound.SoundEvent;
//import net.minecraft.util.math.BlockPos;
//import net.minecraft.util.math.Box;
//import net.minecraft.util.math.MathHelper;
//import net.minecraft.util.math.Vec3d;
//import net.minecraft.world.World;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.Shadow;
//import org.spongepowered.asm.mixin.Unique;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//import scala.concurrent.duration.Duration;
//import ua.zefir.zefiroptimizations.TickableEntity;
//import ua.zefir.zefiroptimizations.threading.AkkaServer;
//import ua.zefir.zefiroptimizations.threading.MovementActor;
//
//import java.util.concurrent.TimeUnit;
//
//@Mixin(LivingEntity.class)
//public abstract class LivingEntity2Mixin implements TickableEntity {
//    @Shadow
//    public float sidewaysSpeed;
//    @Shadow
//    public float upwardSpeed;
//    @Shadow
//    public float forwardSpeed;
//    @Shadow
//    protected int bodyTrackingIncrements;
//    @Shadow
//    protected double serverX;
//    @Shadow
//    protected double serverY;
//    @Shadow
//    protected double serverZ;
//    @Shadow
//    protected double serverYaw;
//    @Shadow
//    protected double serverPitch;
//    @Shadow
//    protected double serverHeadYaw;
//    @Shadow
//    protected int headTrackingIncrements;
//    @Shadow
//    private int jumpingCooldown;
//    @Shadow
//    protected boolean jumping;
//    @Shadow
//    protected int riptideTicks;
//    @Shadow
//    public float bodyYaw;
//    @Shadow
//    public float prevBodyYaw;
//    @Shadow
//    public float headYaw;
//    @Shadow
//    public float prevHeadYaw;
//    @Shadow
//    protected float prevStepBobbingAmount;
//    @Shadow
//    protected float stepBobbingAmount;
//    @Shadow
//    protected float lookDirection;
//    @Shadow
//    protected float prevLookDirection;
//    @Shadow
//    protected int fallFlyingTicks;
//    @Shadow
//    protected abstract SoundEvent getFallSound(int distance);
//    @Shadow
//    protected abstract boolean isSleepingInBed();
//    @Shadow
//    protected abstract boolean isImmobile();
//    @Shadow
//    protected abstract boolean shouldSwimInFluids();
//    @Shadow
//    protected abstract int getNextAirUnderwater(int air);
//    @Shadow
//    protected abstract int getNextAirOnLand(int air);
//    @Shadow
//    protected abstract float getBaseMovementSpeedMultiplier();
//    @Shadow
//    protected abstract float turnHead(float bodyRotation, float headRotation);
//    @Shadow
//    protected abstract void tickActiveItemStack();
//    @Shadow
//    protected abstract void updateLeaningPitch();
//    @Shadow
//    protected abstract void tickNewAi();
//    @Shadow
//    protected abstract void addPowderSnowSlowIfNeeded();
//    @Shadow
//    protected abstract void removePowderSnowSlow();
//    @Shadow
//    protected abstract void tickCramming();
//    @Shadow
//    protected abstract void tickFallFlying();
//    @Shadow
//    protected abstract void updatePostDeath();
//    @Shadow
//    protected abstract void tickStatusEffects();
//    @Shadow
//    public abstract void jump();
//    @Shadow
//    protected abstract void applyMovementEffects(ServerWorld world, BlockPos pos);
//    @Shadow
//    protected abstract void tickRiptide(Box a, Box b);
//    @Shadow
//    protected abstract void swimUpward(TagKey<Fluid> fluid);
//    @Shadow
//    protected abstract void travelControlled(PlayerEntity controllingPlayer, Vec3d movementInput);
//    @Shadow
//    protected abstract void lerpHeadYaw(int headTrackingIncrements, double serverHeadYaw);
//
//    @Unique
//    private ActorRef movementActor;
//
//    @Inject(method = "<init>", at = @At("RETURN"))
//    private void onInit(EntityType<? extends LivingEntity> entityType, World world, CallbackInfo ci) {
//        // Only create actor on server side
//        if (!world.isClient) {
//            MinecraftServer server = world.getServer();
//            this.movementActor = ((AkkaServer) server).getActorSystem().actorOf(Props.create(MovementActor.class, this));
//            ServerTickEvents.END_SERVER_TICK.register(server1 ->
//                    ((AkkaServer) server1).getActorSystem().scheduler().scheduleOnce(
//                            Duration.create(10, TimeUnit.MILLISECONDS), // Introduce a 10ms delay
//                            movementActor,
//                            MovementActor.TICK,
//                            ((AkkaServer) server1).getActorSystem().dispatcher(),
//                            null
//                    )
//            );
//        }
//    }
//
//    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
//    private void onTick(CallbackInfo ci) {
//        LivingEntity self = (LivingEntity) (Object) this;
//        // Do not call super.tick() if we are on the server
//        if (!self.getWorld().isClient) {
//            ci.cancel();
//        }
//    }
//
//    public void zefiroptimizations$tickMovement() {
//        LivingEntity self = (LivingEntity) (Object) this;
//        if (self instanceof LivingEntity livingEntity && livingEntity.isAlive() && !livingEntity.isRemoved()) {
//            this.tickMovementInternal();
//        }
//    }
//
//    @Unique
//    private void tickMovementInternal() {
//        LivingEntity self = (LivingEntity) (Object) this;
//        if (this.jumpingCooldown > 0) {
//            this.jumpingCooldown--;
//        }
//
//        if (self.isLogicalSideForUpdatingMovement()) {
//            this.bodyTrackingIncrements = 0;
//            self.updateTrackedPosition(self.getX(), self.getY(), self.getZ());
//        }
//
//        if (this.bodyTrackingIncrements > 0) {
//            self.lerpPosAndRotation(this.bodyTrackingIncrements, this.serverX, this.serverY, this.serverZ, this.serverYaw, this.serverPitch);
//            this.bodyTrackingIncrements--;
//        } else if (!self.canMoveVoluntarily()) {
//            self.setVelocity(self.getVelocity().multiply(0.98));
//        }
//
//        if (this.headTrackingIncrements > 0) {
//            this.lerpHeadYaw(this.headTrackingIncrements, this.serverHeadYaw);
//            this.headTrackingIncrements--;
//        }
//
//        Vec3d vec3d = self.getVelocity();
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
//        self.setVelocity(d, e, f);
//        self.getWorld().getProfiler().push("ai");
//        if (this.isImmobile()) {
//            this.jumping = false;
//            this.sidewaysSpeed = 0.0F;
//            this.forwardSpeed = 0.0F;
//        } else if (self.canMoveVoluntarily()) {
//            self.getWorld().getProfiler().push("newAi");
//            this.tickNewAi();
//            self.getWorld().getProfiler().pop();
//        }
//
//        self.getWorld().getProfiler().pop();
//        self.getWorld().getProfiler().push("jump");
//        if (this.jumping && this.shouldSwimInFluids()) {
//            double g;
//            if (self.isInLava()) {
//                g = self.getFluidHeight(FluidTags.LAVA);
//            } else {
//                g = self.getFluidHeight(FluidTags.WATER);
//            }
//
//            boolean bl = self.isTouchingWater() && g > 0.0;
//            double h = self.getSwimHeight();
//            if (!bl || self.isOnGround() && !(g > h)) {
//                if (!self.isInLava() || self.isOnGround() && !(g > h)) {
//                    if ((self.isOnGround() || bl && g <= h) && this.jumpingCooldown == 0) {
//                        this.jump();
//                        this.jumpingCooldown = 10;
//                    }
//                } else {
//                    this.swimUpward(FluidTags.LAVA);
//                }
//            } else {
//                this.swimUpward(FluidTags.WATER);
//            }
//        } else {
//            this.jumpingCooldown = 0;
//        }
//
//        self.getWorld().getProfiler().pop();
//        self.getWorld().getProfiler().push("travel");
//        this.sidewaysSpeed *= 0.98F;
//        this.forwardSpeed *= 0.98F;
//        this.tickFallFlying();
//        Box box = self.getBoundingBox();
//        Vec3d vec3d2 = new Vec3d(this.sidewaysSpeed, this.upwardSpeed, this.forwardSpeed);
//        if (self.hasStatusEffect(StatusEffects.SLOW_FALLING) || self.hasStatusEffect(StatusEffects.LEVITATION)) {
//            self.onLanding();
//        }
//
//        label104: {
//            if (self.getControllingPassenger() instanceof PlayerEntity playerEntity && self.isAlive()) {
//                this.travelControlled(playerEntity, vec3d2);
//                break label104;
//            }
//
//            self.travel(vec3d2);
//        }
//
//        self.getWorld().getProfiler().pop();
//        self.getWorld().getProfiler().push("freezing");
//        if (!self.getWorld().isClient && !self.isDead()) {
//            int i = self.getFrozenTicks();
//            if (self.inPowderSnow && self.canFreeze()) {
//                self.setFrozenTicks(Math.min(self.getMinFreezeDamageTicks(), i + 1));
//            } else {
//                self.setFrozenTicks(Math.max(0, i - 2));
//            }
//        }
//
//        this.removePowderSnowSlow();
//        this.addPowderSnowSlowIfNeeded();
//        if (!self.getWorld().isClient && self.age % 40 == 0 && self.isFrozen() && self.canFreeze()) {
//            self.damage(self.getDamageSources().freeze(), 1.0F);
//        }
//
//        self.getWorld().getProfiler().pop();
//        self.getWorld().getProfiler().push("push");
//        if (this.riptideTicks > 0) {
//            this.riptideTicks--;
//            this.tickRiptide(box, self.getBoundingBox());
//        }
//
//        this.tickCramming();
//        self.getWorld().getProfiler().pop();
//        if (!self.getWorld().isClient && self.hurtByWater() && self.isWet()) {
//            self.damage(self.getDamageSources().drown(), 1.0F);
//        }
//
//        double d1 = self.getX() - self.prevX;
//        double e1 = self.getZ() - self.prevZ;
//        float f1 = (float) (d1 * d1 + e1 * e1);
//        float g = this.bodyYaw;
//        float h = 0.0F;
//        this.prevStepBobbingAmount = this.stepBobbingAmount;
//        float k = 0.0F;
//        if (f1 > 0.0025000002F) {
//            k = 1.0F;
//            h = (float) Math.sqrt(f1) * 3.0F;
//            float l = (float) MathHelper.atan2(e, d) * (180.0F / (float) Math.PI) - 90.0F;
//            float m = MathHelper.abs(MathHelper.wrapDegrees(self.getYaw()) - l);
//            if (95.0F < m && m < 265.0F) {
//                g = l - 180.0F;
//            } else {
//                g = l;
//            }
//        }
//
//        if (self.handSwingProgress > 0.0F) {
//            g = self.getYaw();
//        }
//
//        if (!self.isOnGround()) {
//            k = 0.0F;
//        }
//
//        this.stepBobbingAmount = this.stepBobbingAmount + (k - this.stepBobbingAmount) * 0.3F;
//        self.getWorld().getProfiler().push("headTurn");
//        h = this.turnHead(g, h);
//        self.getWorld().getProfiler().pop();
//        self.getWorld().getProfiler().push("rangeChecks");
//
//        while (self.getYaw() - self.prevYaw < -180.0F) {
//            self.prevYaw -= 360.0F;
//        }
//
//        while (self.getYaw() - self.prevYaw >= 180.0F) {
//            self.prevYaw += 360.0F;
//        }
//
//        while (this.bodyYaw - this.prevBodyYaw < -180.0F) {
//            this.prevBodyYaw -= 360.0F;
//        }
//
//        while (this.bodyYaw - this.prevBodyYaw >= 180.0F) {
//            this.prevBodyYaw += 360.0F;
//        }
//
//        while (self.getPitch() - self.prevPitch < -180.0F) {
//            self.prevPitch -= 360.0F;
//        }
//
//        while (self.getPitch() - self.prevPitch >= 180.0F) {
//            self.prevPitch += 360.0F;
//        }
//
//        while (this.headYaw - this.prevHeadYaw < -180.0F) {
//            this.prevHeadYaw -= 360.0F;
//        }
//
//        while (this.headYaw - this.prevHeadYaw >= 180.0F) {
//            this.prevHeadYaw += 360.0F;
//        }
//
//        self.getWorld().getProfiler().pop();
//        this.lookDirection += h;
//        if (self.isFallFlying()) {
//            this.fallFlyingTicks++;
//        } else {
//            this.fallFlyingTicks = 0;
//        }
//
//        if (self.isSleeping()) {
//            self.setPitch(0.0F);
//        }
//    }
//}
