package ua.zefir.zefiroptimizations.actors;

import akka.actor.Props;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.GameRules;

public class MobEntityActor extends EntityActor {
    public MobEntityActor(MobEntity entity) {
        super(entity);
    }

    public static Props props(LivingEntity entity) {
        return Props.create(MobEntityActor.class, entity);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(EntityActorMessages.AsyncTick.class, this::handleAsyncTick)
                .match(EntityActorMessages.ContinueTickMovement.class, this::continueTickMovement)
                .build();
    }

    @Override
    protected void handleAsyncTick(EntityActorMessages.AsyncTick msg) {
        if (!entity.isRemoved()) {
            this.tickMobEntityMovement();
        }
    }

    @Override
    public void tickMobEntityMovement() {
//        long millis = System.currentTimeMillis();
        super.tickMobEntityMovement();

        if(this.entity instanceof MobEntity mobEntity) {
            mobEntity.getWorld().getProfiler().push("looting");
            if (!mobEntity.getWorld().isClient && mobEntity.canPickUpLoot() && mobEntity.isAlive() && !mobEntity.isDead() && mobEntity.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
            ) {
                Vec3i vec3i = entityAccess.zefiroptimizations$getItemPickUpRangeExpander();

                for (ItemEntity itemEntity : mobEntity.getWorld()
                        .getNonSpectatingEntities(ItemEntity.class, mobEntity.getBoundingBox().expand((double) vec3i.getX(), (double) vec3i.getY(), (double) vec3i.getZ()))) {
                    if (!itemEntity.isRemoved() && !itemEntity.getStack().isEmpty() && !itemEntity.cannotPickup() && mobEntity.canGather(itemEntity.getStack())) {
                        entityAccess.zefiroptimizations$loot(itemEntity);
                    }
                }
            }

            mobEntity.getWorld().getProfiler().pop();

            // TODO: Перенести виконання цього метода після tick()
            if (!mobEntity.getWorld().isClient && mobEntity.age % 5 == 0) {
                ((IAsyncLivingEntityAccess) mobEntity).zefiroptimizations$updateGoalControls();
            }
        }
//        long tickTake = System.currentTimeMillis() - millis;
//        if(tickTake>20) {
//            System.out.println("tick take: " + tickTake);
//        }
    }
}
