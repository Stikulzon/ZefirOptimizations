package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.EntityView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.data.DummyPredicate;
import ua.zefir.zefiroptimizations.data.predicates.ActorSafePredicates;

import java.util.function.Predicate;

@Mixin(EntityView.class)
public interface EntityViewMixin {

    @ModifyArg(method = "getEntityCollisions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/EntityView;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;"), index = 2)
    private Predicate<? super Entity> onIsSpectator(@Nullable Entity entity, Box box, Predicate<? super Entity> predicate) {
        Predicate<? super Entity> newPredicate = ActorSafePredicates.createCollisionPredicate(entity);
//        ZefirOptimizations.LOGGER.info("onIsSpectator {}", newPredicate instanceof DummyPredicate);
        return newPredicate;
    }
}
