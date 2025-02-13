package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.util.TypeFilter;
import net.minecraft.util.annotation.Debug;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.EntityTrackingStatus;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zefiroptimizations.data.ConcurrentTypeFilterableList;

import java.util.Collection;
import java.util.stream.Stream;

@Mixin(EntityTrackingSection.class)
public abstract class EntityTrackingSectionMixin<T extends EntityLike> {
    @Unique
    private ConcurrentTypeFilterableList<T> collection;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(Class entityClass, EntityTrackingStatus initialStatus, CallbackInfo ci) {
        this.status = initialStatus;
        this.collection = new ConcurrentTypeFilterableList<>(entityClass);
    }

//    @Shadow @Final private TypeFilterableList<T> collection;
    @Shadow private EntityTrackingStatus status;

//    @Unique
//    private final ReentrantLock lock = new ReentrantLock();

    /**
     * @author Zefir
     * @reason Thread-safe EntityTrackingSection operations implementation
     */
    @Overwrite
    public void add(T entity) {
        this.collection.add(entity);
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityTrackingSection operations implementation
     */
    @Overwrite
    public boolean remove(T entity) {
        return this.collection.remove(entity);
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityTrackingSection operations implementation
     */
    @Overwrite
    public LazyIterationConsumer.NextIteration forEach(Box box, LazyIterationConsumer<T> consumer) {
        for (T entityLike : this.collection) {
            if (entityLike.getBoundingBox().intersects(box) && consumer.accept(entityLike).shouldAbort()) {
                return LazyIterationConsumer.NextIteration.ABORT;
            }
        }

        return LazyIterationConsumer.NextIteration.CONTINUE;
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityTrackingSection operations implementation
     */
    @Overwrite
    public <U extends T> LazyIterationConsumer.NextIteration forEach(TypeFilter<T, U> type, Box box, LazyIterationConsumer<? super U> consumer) {
        Collection<? extends T> collection = this.collection.getAllOfType(type.getBaseClass());
        if (collection.isEmpty()) {
            return LazyIterationConsumer.NextIteration.CONTINUE;
        } else {
            for (T entityLike : collection) {
                U entityLike2 = (U)type.downcast(entityLike);
                if (entityLike2 != null && entityLike.getBoundingBox().intersects(box) && consumer.accept(entityLike2).shouldAbort()) {
                    return LazyIterationConsumer.NextIteration.ABORT;
                }
            }

            return LazyIterationConsumer.NextIteration.CONTINUE;
        }
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityTrackingSection operations implementation
     */
    @Overwrite
    public boolean isEmpty() {
        return this.collection.isEmpty();
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityTrackingSection operations implementation
     */
    @Overwrite
    public Stream<T> stream() {
        return this.collection.stream();
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityTrackingSection operations implementation
     */
    @Overwrite
    public EntityTrackingStatus getStatus() {
        return this.status;
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityTrackingSection operations implementation
     */
    @Overwrite
    public EntityTrackingStatus swapStatus(EntityTrackingStatus status) {
        EntityTrackingStatus entityTrackingStatus = this.status;
        this.status = status;
        return entityTrackingStatus;
    }

    /**
     * @author Zefir
     * @reason Thread-safe EntityTrackingSection operations implementation
     */
    @Overwrite
    @Debug
    public int size() {
        return this.collection.size();
    }
}
