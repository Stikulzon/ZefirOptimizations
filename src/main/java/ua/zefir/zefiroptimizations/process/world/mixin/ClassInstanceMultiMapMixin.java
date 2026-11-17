package ua.zefir.zefiroptimizations.process.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import ua.zefir.zefiroptimizations.process.Processes;

@Mixin(ClassInstanceMultiMap.class)
public abstract class ClassInstanceMultiMapMixin<T> {
  @Shadow @Final private Map<Class<?>, List<T>> byClass;

  @Shadow @Final private List<T> allInstances;

  @SuppressWarnings("unchecked")
  @WrapMethod(method = "find")
  private <S> Collection<S> zo$getAllOfType(Class<S> type, Operation<Collection<S>> original) {
    if (!Processes.inProcess() || byClass.get(type) != null) {
      return original.call(type);
    }
    List<S> found = new ArrayList<>();
    for (T element : allInstances) {
      if (type.isInstance(element)) {
        found.add((S) element);
      }
    }
    return Collections.unmodifiableList(found);
  }
}
