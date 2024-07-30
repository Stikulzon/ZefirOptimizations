package ua.zefir.zefiroptimizations.threading;

import akka.actor.AbstractActor;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.Optional;

public class MovementPredictionActor extends AbstractActor {

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(MovementPredictionRequest.class, request -> {
                    Entity entity = request.getEntity();
                    Vec3d movement = request.getMovement();
                    WorldSnapshot snapshot = request.getSnapshot();

                    // 1. Basic Movement
                    Vec3d predictedPosition = entity.getPos().add(movement);
                    Vec3d predictedVelocity = movement;

                    // 2. Collision Detection
                    Box predictedBoundingBox = entity.getBoundingBox().offset(movement);
                    boolean needsCollisionRecheck = false;

                    for (Direction direction : Direction.values()) {
                        Vec3d directionVec = Vec3d.of(direction.getVector());
                        double maxOffset = movement.getComponentAlongAxis(direction.getAxis());

                        if (maxOffset != 0.0) {
                            for (double offset = 0.0; offset <= Math.abs(maxOffset); offset += 0.25) {
                                Vec3d checkPos = predictedPosition.add(directionVec.multiply(offset * Math.signum(maxOffset)));
                                BlockPos blockPos = new BlockPos((int) checkPos.x, (int) checkPos.y, (int) checkPos.z); // Correct type for BlockPos
                                VoxelShape blockShape = snapshot.getBlockCollisionShape(blockPos);

                                if (!blockShape.isEmpty()) {
                                    // Corrected raycast usage and type
                                    BlockHitResult intersection = blockShape.raycast(entity.getPos(), checkPos, blockPos);
                                    if (intersection != null) {
                                        // Collision detected!
                                        needsCollisionRecheck = true;

                                        // Get the correct collision point from BlockHitResult
                                        Vec3d collisionPoint = intersection.getPos();
                                        double collisionOffset = collisionPoint.getComponentAlongAxis(direction.getAxis());

                                        // Corrected Collision Resolution (without lambdas):
                                        switch (direction.getAxis()) {
                                            case X:
                                                predictedPosition = new Vec3d(
                                                        predictedPosition.x - (offset - Math.abs(collisionOffset)) * Math.signum(maxOffset),
                                                        predictedPosition.y,
                                                        predictedPosition.z
                                                );
                                                predictedVelocity = new Vec3d(0.0, predictedVelocity.y, predictedVelocity.z);
                                                break;
                                            case Y:
                                                predictedPosition = new Vec3d(
                                                        predictedPosition.x,
                                                        predictedPosition.y - (offset - Math.abs(collisionOffset)) * Math.signum(maxOffset),
                                                        predictedPosition.z
                                                );
                                                predictedVelocity = new Vec3d(predictedVelocity.x, 0.0, predictedVelocity.z);
                                                break;
                                            case Z:
                                                predictedPosition = new Vec3d(
                                                        predictedPosition.x,
                                                        predictedPosition.y,
                                                        predictedPosition.z - (offset - Math.abs(collisionOffset)) * Math.signum(maxOffset)
                                                );
                                                predictedVelocity = new Vec3d(predictedVelocity.x, predictedVelocity.y, 0.0);
                                                break;
                                        }

                                        break;
                                    }
                                }
                            }
                        }
                    }

                    // 3. Create MovementPrediction
                    MovementPrediction prediction = new MovementPrediction(predictedPosition,
                            predictedVelocity,
                            needsCollisionRecheck);

                    // 4. Send prediction back
                    sender().tell(prediction, self());
                })
                .build();
    }
}
