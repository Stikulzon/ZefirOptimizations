package ua.zefir.zefiroptimizations.actors;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public class ArmorStandEntityActor extends EntityActor {
    private final IArmorStandEntityAccess entityAccess;

    public ArmorStandEntityActor(LivingEntity entity) {
        super(entity);
        this.entityAccess = (IArmorStandEntityAccess) entity;
    }

    @Override
    protected void tickCramming() {
        for (Entity entity : getEntity().getWorld().getOtherEntities(getEntity(), getEntity().getBoundingBox(), entityAccess.zefiroptimizations$RIDEABLE_MINECART_PREDICATE())) {
            if (getEntity().squaredDistanceTo(entity) <= 0.2) {
                entity.pushAwayFrom(getEntity());
            }
        }
    }
}
