package ua.zefir.zefiroptimizations.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                .build();
    }

    private void handleAsyncTick(EntityActorMessages.AsyncTick msg) {
        ServerWorld world = ZefirOptimizations.SERVER.getOverworld();

        for (LivingEntity entity : world.getEntitiesByType(
                TypeFilter.instanceOf(LivingEntity.class),
                e -> e instanceof IAsyncTickingLivingEntity && ((IAsyncTickingLivingEntity) e).zefiroptimizations$isAsyncTicking())) {

            ActorRef entityActor = entityActors.get(entity);
            if (entityActor != null) {
                entityActor.tell(msg, getSelf());
            }
        }
    }

    private void handleEntityCreated(EntityActorMessages.EntityCreated msg) {
        LivingEntity entity = msg.entity();
        if (entity instanceof IAsyncTickingLivingEntity && ((IAsyncTickingLivingEntity) entity).zefiroptimizations$isAsyncTicking()) {
            ActorRef entityActor = getContext().actorOf(EntityActor.props(entity), "entityActor_" + entity.getUuid());
            entityActors.put(entity, entityActor);
        }
    }

    private void handleEntityRemoved(EntityActorMessages.EntityRemoved msg) {
        LivingEntity entity = msg.entity();
        ActorRef entityActor = entityActors.remove(entity);
        if (entityActor != null) {
            getContext().stop(entityActor);
        }
    }

    private void handleSyncPosition(EntityActorMessages.SyncPosition msg) {
        LivingEntity entity = msg.entity();
        Vec3d newPosition = msg.newPosition();

        ServerWorld world = (ServerWorld) entity.getWorld();

        // Collision detection and resolution
        Box newBoundingBox = entity.getBoundingBox().offset(newPosition.subtract(entity.getPos()));
        List<VoxelShape> collidingEntities = world.getEntityCollisions(entity, newBoundingBox);

        if (collidingEntities.isEmpty()) {
            // No collisions, update the entity's position
            // Calculate relative change for EntityS2CPacket.MoveRelative
            double d = newPosition.x - entity.getX();
            double e = newPosition.y - entity.getY();
            double f = newPosition.z - entity.getZ();
            // Send position update
            world.getChunkManager().sendToNearbyPlayers(entity, new EntityS2CPacket.MoveRelative(
                    entity.getId(),
                    (short) (d * 4096),
                    (short) (e * 4096),
                    (short) (f * 4096),
                    entity.isOnGround()
            ));

            entity.setPos(newPosition.x, newPosition.y, newPosition.z);

            // Update chunk position
            int i = MathHelper.floor(newPosition.x);
            int j = MathHelper.floor(newPosition.y);
            int k = MathHelper.floor(newPosition.z);
            if (i != entity.getBlockPos().getX() || j != entity.getBlockPos().getY() || k != entity.getBlockPos().getZ()) {
                ((IAsyncLivingEntityAccess) entity).zefiroptimizations$setBlockPos(new BlockPos(i, j, k));
                ((IAsyncLivingEntityAccess) entity).zefiroptimizations$setStateAtPos(null);
                if (ChunkSectionPos.getSectionCoord(i) != entity.getChunkPos().x || ChunkSectionPos.getSectionCoord(k) != entity.getChunkPos().z) {
                    ((IAsyncLivingEntityAccess) entity).zefiroptimizations$setChunkPos(new ChunkPos(entity.getBlockPos()));
                }
            }

            // Handle riding
            if (entity.hasVehicle()) {
                Objects.requireNonNull(entity.getVehicle()).updatePassengerPosition(entity);
            }
        } else {
            // Resolve collisions, for simplicity just stop the entity
            entity.setVelocity(Vec3d.ZERO);
            // More sophisticated collision resolution can be implemented here
        }

        // Update last tick position for interpolation
        entity.prevX = newPosition.x;
        entity.prevY = newPosition.y;
        entity.prevZ = newPosition.z;
    }
}
