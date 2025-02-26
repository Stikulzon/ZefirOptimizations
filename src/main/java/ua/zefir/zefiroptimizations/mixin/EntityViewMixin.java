package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.world.EntityView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// TODO: useless
@Mixin(EntityView.class)
public interface EntityViewMixin {
    /**
     * @author Zefir
     * @reason Thread-safe EntityList usage implementation
     */
    @Overwrite
    @Nullable
    default <T extends LivingEntity> T getClosestEntity(
            List<? extends T> entityList, TargetPredicate targetPredicate, @Nullable LivingEntity entity, double x, double y, double z
    ) {
        List<? extends T> safeEntityList = new CopyOnWriteArrayList<>(entityList);

        double d = -1.0;
        T livingEntity = null;

        for (T livingEntity2 : safeEntityList) { // Iterate over the thread-safe copy
            if (targetPredicate.test(entity, livingEntity2)) {
                double e = livingEntity2.squaredDistanceTo(x, y, z);
                if (d == -1.0 || e < d) {
                    d = e;
                    livingEntity = livingEntity2;
                }
            }
        }

        return livingEntity;
    }

}
