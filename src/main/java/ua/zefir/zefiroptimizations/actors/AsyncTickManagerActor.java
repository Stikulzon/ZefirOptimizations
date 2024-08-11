package ua.zefir.zefiroptimizations.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

import java.util.HashMap;
import java.util.Map;

public class AsyncTickManagerActor extends AbstractActor {
    private final Map<LivingEntity, ActorRef> entityActors = new HashMap<>();

    public static Props props() {
        return Props.create(AsyncTickManagerActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(EntityActorMessages.AsyncTick.class, this::handleAsyncTick)
                .match(EntityActorMessages.EntityCreated.class, this::handleEntityCreated)
                .match(EntityActorMessages.EntityRemoved.class, this::handleEntityRemoved)
                .match(EntityActorMessages.SyncPosition.class, this::handleSyncPosition)
                .match(EntityActorMessages.TickSingleEntity.class, this::handleAsyncSingleTick)
                .build();
    }

    private void handleAsyncTick(EntityActorMessages.AsyncTick msg) {
        ServerWorld world = ZefirOptimizations.SERVER.getOverworld();
//        System.out.println("GET");

        for (LivingEntity entity : world.getEntitiesByType(
                TypeFilter.instanceOf(LivingEntity.class),
                e -> e instanceof LivingEntity)) {

            ActorRef entityActor = entityActors.get(entity);

            if (entityActor != null) {
                entityActor.tell(msg, getSelf());
            }
        }
    }

    private void handleAsyncSingleTick(EntityActorMessages.TickSingleEntity msg) {
            ActorRef entityActor = entityActors.get(msg.entity());

            if (entityActor != null) {
                entityActor.tell(msg, getSelf());
            }
    }

    private void handleEntityCreated(EntityActorMessages.EntityCreated msg) {
        LivingEntity entity = msg.entity();
//        if (entity instanceof IAsyncTickingLivingEntity) {
            ActorRef entityActor = getContext().actorOf(EntityActor.props(entity), "entityActor_" + entity.getUuid());
            entityActors.put(entity, entityActor);
//        }
    }

    private void handleEntityRemoved(EntityActorMessages.EntityRemoved msg) {
        LivingEntity entity = msg.entity();
        ActorRef entityActor = entityActors.remove(entity);
        if (entityActor != null) {
            getContext().stop(entityActor);
        }
    }

    private void handleSyncPosition(EntityActorMessages.SyncPosition msg) {
//        LivingEntity entity = msg.entity();
//        Vec3d newPosition = msg.newPosition();
//
//        ServerWorld world = (ServerWorld) entity.getWorld();
//
//        // Collision detection and resolution
//        Box newBoundingBox = entity.getBoundingBox().offset(newPosition.subtract(entity.getPos()));
//        List<VoxelShape> collidingShapes = world.getEntityCollisions(entity, newBoundingBox);
//
//        if (collidingShapes.isEmpty()) {
//            // No collisions
//
//            // 1. Prepare relative changes for position AND rotation
//            double d = newPosition.x - entity.getX();
//            double e = newPosition.y - entity.getY();
//            double f = newPosition.z - entity.getZ();
//
//            // Calculate yaw/pitch changes
//            float yawChange = entity.getYaw() - entity.prevYaw;
//            float pitchChange = entity.getPitch() - entity.prevPitch;
//
//            // 2. Send position AND rotation update to clients
//            world.getChunkManager().sendToNearbyPlayers(entity, new EntityS2CPacket.RotateAndMoveRelative(
//                    entity.getId(),
//                    (short) (d * 4096),
//                    (short) (e * 4096),
//                    (short) (f * 4096),
//                    (byte) MathHelper.floor(yawChange * 256.0F / 360.0F),
//                    (byte) MathHelper.floor(pitchChange * 256.0F / 360.0F),
//                    entity.isOnGround()
//            ));
//
//            // 3. Update entity position ONLY AFTER notifying clients
//            entity.setPos(newPosition.x, newPosition.y, newPosition.z);
//
//            // 4. Update other relevant fields (MUST be done after setPos)
//            int i = MathHelper.floor(newPosition.x);
//            int j = MathHelper.floor(newPosition.y);
//            int k = MathHelper.floor(newPosition.z);
//            if (i != entity.getBlockPos().getX() || j != entity.getBlockPos().getY() || k != entity.getBlockPos().getZ()) {
//                ((IAsyncLivingEntityAccess) entity).zefiroptimizations$setBlockPos(new BlockPos(i, j, k));
//                ((IAsyncLivingEntityAccess) entity).zefiroptimizations$setStateAtPos(null);
//                if (ChunkSectionPos.getSectionCoord(i) != entity.getChunkPos().x || ChunkSectionPos.getSectionCoord(k) != entity.getChunkPos().z) {
//                    ((IAsyncLivingEntityAccess) entity).zefiroptimizations$setChunkPos(new ChunkPos(entity.getBlockPos()));
//                }
//            }
//
//            // Handle riding
//            if (entity.hasVehicle()) {
//                Objects.requireNonNull(entity.getVehicle()).updatePassengerPosition(entity);
//            }
//
//            // 5. Sync velocity to prevent rubber-banding
//            entity.prevX = newPosition.x;
//            entity.prevY = newPosition.y;
//            entity.prevZ = newPosition.z;
//            world.getChunkManager().sendToNearbyPlayers(entity, new EntityVelocityUpdateS2CPacket(entity));
//        } else {
//            // Resolve collisions
//            entity.setVelocity(Vec3d.ZERO);
//            // TODO: Implement more sophisticated collision resolution if needed
//        }
//
//        // 6. Update last tick position for interpolation (both cases)
//        entity.prevX = newPosition.x;
//        entity.prevY = newPosition.y;
//        entity.prevZ = newPosition.z;
//        entity.prevYaw = entity.getYaw();
//        entity.prevPitch = entity.getPitch();
    }
}
