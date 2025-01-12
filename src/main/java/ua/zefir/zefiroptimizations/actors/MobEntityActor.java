package ua.zefir.zefiroptimizations.actors;

import akka.actor.Props;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.GameRules;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

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
                .match(ZefirsActorMessages.AsyncTick.class, this::handleAsyncTick)
                .match(ZefirsActorMessages.ContinueTickMovement.class, this::continueTickMovement)
                .match(ZefirsActorMessages.BaseTickSingleEntity.class, this::handleBaseTick)
                .build();
    }

    @Override
    protected void handleAsyncTick(ZefirsActorMessages.AsyncTick msg) {
        if (!entity.isRemoved()) {
            super.handleAsyncTick(msg);
        }
    }

//    @Override
//    public void tickMobEntityMovement() {
//        super.tickMobEntityMovement();

//        if (this.entity instanceof MobEntity mobEntity) {
//            mobEntity.getWorld().getProfiler().push("looting");
//            if (!mobEntity.getWorld().isClient && mobEntity.canPickUpLoot() && mobEntity.isAlive() && !mobEntity.isDead() && mobEntity.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
//            ) {
//                Vec3i vec3i = entityAccess.zefiroptimizations$getItemPickUpRangeExpander();
//
//                for (ItemEntity itemEntity : mobEntity.getWorld()
//                        .getNonSpectatingEntities(ItemEntity.class, mobEntity.getBoundingBox().expand(vec3i.getX(), vec3i.getY(), vec3i.getZ()))) {
//                    if (!itemEntity.isRemoved() && !itemEntity.getStack().isEmpty() && !itemEntity.cannotPickup() && mobEntity.canGather(itemEntity.getStack())) {
//                        ZefirOptimizations.getMainThreadActor().tell(
//                                new ZefirsActorMessages.
//                                        LootItemEntity(entityAccess, itemEntity),
//                                getSelf()
//                        );
//                    }
//                }
//            }
//
//            mobEntity.getWorld().getProfiler().pop();
//
//            // TODO: Перенести виконання цього метода після tick()
//            if (!mobEntity.getWorld().isClient && mobEntity.age % 5 == 0) {
//                ((IAsyncLivingEntityAccess) mobEntity).zefiroptimizations$updateGoalControls();
//            }
//        }
//    }
}
