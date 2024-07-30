package ua.zefir.zefiroptimizations.threading;


import akka.actor.AbstractActor;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class WorldSnapshotActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(WorldSnapshotRequest.class, request -> {
                    Entity entity = request.getEntity();
                    Vec3d movement = request.getMovement();
                    int radius = 3; // Adjust this radius as needed

                    // Create the WorldSnapshot
                    WorldSnapshot snapshot = new WorldSnapshot(entity, movement, radius);

                    // Send the snapshot back to the sender
                    sender().tell(snapshot, self());
                })
                .build();
    }
}
