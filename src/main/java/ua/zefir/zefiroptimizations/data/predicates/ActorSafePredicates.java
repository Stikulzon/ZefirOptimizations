package ua.zefir.zefiroptimizations.data.predicates;

import net.minecraft.entity.Entity;

import java.util.function.Predicate;

public class ActorSafePredicates {
    public static final Predicate<Entity> EXCEPT_SPECTATOR = entity -> !entity.isSpectator();
    public static final Predicate<Entity> CAN_COLLIDE = EXCEPT_SPECTATOR.and(Entity::isCollidable);

    public static Predicate<Entity> createCollisionPredicate(Entity entity) {
        if (entity == null) {
            return CAN_COLLIDE;
        } else {
            return EXCEPT_SPECTATOR.and(entity::collidesWith);
        }
    }
}
