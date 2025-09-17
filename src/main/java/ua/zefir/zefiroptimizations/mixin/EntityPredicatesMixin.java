package ua.zefir.zefiroptimizations.mixin;

import com.google.common.base.Predicates;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.scoreboard.AbstractTeam;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Predicate;

@Mixin(EntityPredicates.class)
public class EntityPredicatesMixin {

    @Shadow @Final public static Predicate<Entity> EXCEPT_SPECTATOR;
//    /**
//     * @author 1
//     * @reason 1
//     */
//    @Overwrite
//    public static Predicate<Entity> canBePushedBy(Entity entity) {
////        throw new RuntimeException("canBePushedBy");
////        System.out.println(1);
//        AbstractTeam abstractTeam = entity.getScoreboardTeam();
////        System.out.println(2);
//        AbstractTeam.CollisionRule collisionRule = abstractTeam == null ? AbstractTeam.CollisionRule.ALWAYS : abstractTeam.getCollisionRule();
////        System.out.println(3);
//        return (collisionRule == AbstractTeam.CollisionRule.NEVER
//                ? Predicates.alwaysFalse()
//                : EXCEPT_SPECTATOR.and(
//                entityxx -> {
////                    System.out.println(4 + " ");
////                    System.out.println(entityxx + " " + entityxx.isRemoved() + " " + entityxx.isAlive());
////                    System.out.println(entityxx.isPushable());
//                    if (!entityxx.isPushable()) {
////                        System.out.println(5);
//                        return false;
//                    } else if (!entity.getWorld().isClient || entityxx instanceof PlayerEntity && ((PlayerEntity)entityxx).isMainPlayer())
//                        {
//                        System.out.println(6);
//                        AbstractTeam abstractTeam2 = entityxx.getScoreboardTeam();
//                        AbstractTeam.CollisionRule collisionRule2 = abstractTeam2 == null ? AbstractTeam.CollisionRule.ALWAYS : abstractTeam2.getCollisionRule();
//                        if (collisionRule2 == AbstractTeam.CollisionRule.NEVER) {
//                            return false;
//                        } else {
//                            boolean bl = abstractTeam != null && abstractTeam.isEqual(abstractTeam2);
//                            return (collisionRule == AbstractTeam.CollisionRule.PUSH_OWN_TEAM || collisionRule2 == AbstractTeam.CollisionRule.PUSH_OWN_TEAM) && bl
//                                    ? false
//                                    : collisionRule != AbstractTeam.CollisionRule.PUSH_OTHER_TEAMS && collisionRule2 != AbstractTeam.CollisionRule.PUSH_OTHER_TEAMS || bl;
//                        }
//                    } else {
//                        System.out.println(7);
//                        return false;
//                    }
//                }
//        ));
//    }
}
