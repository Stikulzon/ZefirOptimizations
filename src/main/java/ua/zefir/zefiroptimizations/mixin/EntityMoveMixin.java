package ua.zefir.zefiroptimizations.mixin;

import akka.pattern.Patterns;
import akka.util.Timeout;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.threading.*;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static ua.zefir.zefiroptimizations.ZefirOptimizations.MOVEMENT_PREDICTION_ACTOR;
import static ua.zefir.zefiroptimizations.ZefirOptimizations.WORLD_SNAPSHOT_ACTOR;

@Mixin(Entity.class)
public abstract class EntityMoveMixin {

    @Shadow
    private World world;
    @Shadow
    private Vec3d pos;
    @Shadow
    public void setPos(double x, double y, double z) {}
    @Shadow
    public void setVelocity(Vec3d velocity) {}
    @Shadow
    private Vec3d adjustMovementForCollisions(Vec3d movement) { return null; }

    // Inject after noClip handling
    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isOnFire()Z", shift = At.Shift.BEFORE), cancellable = true)
    private void handleAsyncCollision(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self).noClip) {
            if (this.world instanceof ServerWorld) {
                try {
                    // 1. Request world snapshot
                    WorldSnapshot snapshot = (WorldSnapshot) Await.result(
                            Patterns.ask(WORLD_SNAPSHOT_ACTOR,
                                    new WorldSnapshotRequest(self, movement),
                                    Timeout.apply(Duration.create(50, TimeUnit.MILLISECONDS))),
                            Duration.create(100, TimeUnit.MILLISECONDS));

                    // 2. Predict movement
                    MovementPrediction prediction = (MovementPrediction) Await.result(
                            Patterns.ask(MOVEMENT_PREDICTION_ACTOR,
                                    new MovementPredictionRequest(self, movement, snapshot),
                                    Timeout.apply(Duration.create(50, TimeUnit.MILLISECONDS))),
                            Duration.create(100, TimeUnit.MILLISECONDS));

                    // 3. Apply predicted movement
                    this.setPos(prediction.getPosition().x, prediction.getPosition().y, prediction.getPosition().z);
                    this.setVelocity(prediction.getVelocity());

                    // 4. Schedule deviation check on main thread
                    ServerTickEvents.END_WORLD_TICK.register(world -> {
                        if (prediction.getNeedsCollisionRecheck()) {
                            Vec3d actualMovement = this.adjustMovementForCollisions(movement);
                            // Handle discrepancies
                            if (!Objects.equals(actualMovement, prediction.getVelocity())) {
                                this.setPos(this.pos.x + Objects.requireNonNull(actualMovement).x, this.pos.y + actualMovement.y, this.pos.z + actualMovement.z);
                                this.setVelocity(actualMovement);
                                ZefirOptimizations.LOGGER.debug("Collision deviation detected for entity {}", (self).getUuid());
                            }
                        }
                    });

                    ci.cancel(); // Prevent default movement

                } catch (Exception e) {
                    ZefirOptimizations.LOGGER.error("Error in asynchronous collision detection: ", e);
                    // Fallback to synchronous collision
                }
            }
        }
    }

    // Redirect call to setPosition to capture the final position
    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setPosition(DDD)V"))
    private void captureFinalPosition(Entity entity, double x, double y, double z) {
        // Do nothing, the position is already set by the async collision handling
    }
}
