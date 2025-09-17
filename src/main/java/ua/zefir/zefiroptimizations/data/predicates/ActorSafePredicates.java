package ua.zefir.zefiroptimizations.data.predicates;

import net.minecraft.entity.Entity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.scoreboard.AbstractTeam;
import ua.zefir.zefiroptimizations.data.DummyPredicate;

import java.util.function.Predicate;

public class ActorSafePredicates {
    /**
     * Creates an actor-safe predicate that approximates EntityPredicates.canBePushedBy.
     * It uses a snapshot of the "pusher" entity's team data.
     * IMPORTANT: This predicate CANNOT safely access the "pushee's" team data from an actor thread.
     * Thus, its team-based collision logic is a simplification.
     *
     * @param pusherSnapshot Snapshot of the pusher entity's team data.
     * @param originalPusher The original pusher entity (used for EXCEPT_SPECTATOR and isPushable on pushee).
     *                       It's assumed 'originalPusher' is also the 'except' entity.
     * @return A predicate for use by the actor.
     */
    public static Predicate<Entity> actorSafeCanBePushedBy(CanBePushedBySnapshot pusherSnapshot, Entity originalPusher) {
        // Basic checks from EntityPredicates.EXCEPT_SPECTATOR and isPushable
        Predicate<Entity> baseChecks = pushee ->
                !pushee.isSpectator() && pushee.isPushable() && pushee != originalPusher;


        if (pusherSnapshot.pusherCollisionRule == AbstractTeam.CollisionRule.NEVER) {
            return baseChecks.and(entity -> false); // Effectively always false after base checks
        }

        // Simplified server-side logic using only the pusher's snapshot
        Predicate<Entity> pusherRuleLogic = pushee -> {
            // We cannot safely get pushee.getScoreboardTeam() here.
            // So, the logic is based solely on the pusher's rule.
            switch (pusherSnapshot.pusherCollisionRule) {
                case ALWAYS:
                    return true; // Pushes everyone (respecting baseChecks)
                case PUSH_OTHER_TEAMS:
                    // Since we can't check if pushee is "other", we conservatively allow pushing.
                    // This means it might push teammates if this rule is set. Vanilla would not.
                    return true;
                case PUSH_OWN_TEAM:
                    // Since we can't check if pushee is "own", we conservatively disallow pushing.
                    // This means it might not push teammates. Vanilla would.
                    return false;
                default: // Should include NEVER, but handled above
                    return false;
            }
        };

        return baseChecks.and(pusherRuleLogic);
    }

    public static final DummyPredicate<Entity> EXCEPT_SPECTATOR = entity -> !entity.isSpectator();
    public static final DummyPredicate<Entity> CAN_COLLIDE = EXCEPT_SPECTATOR.and(Entity::isCollidable);

    public static Predicate<Entity> createCollisionPredicate(Entity entity) {
        if (entity == null) {
            return CAN_COLLIDE;
        } else {
            return EXCEPT_SPECTATOR.and(entity::collidesWith);
        }
    }
}
