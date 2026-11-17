package ua.zefir.zephyrium.transform;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

public final class TransformerInstaller {
  private static final Logger LOGGER = LoggerFactory.getLogger("Zephyrium/transformer");
  private static boolean installed;

  public static synchronized void install(List<Instrumenter> instrumenters) {
    if (installed || instrumenters.isEmpty()) {
      return;
    }
    try {
      ClassLoader loader = TransformerInstaller.class.getClassLoader();
      Class.forName(MergedMixins.class.getName(), true, loader);
      Method getDelegate = findMethod(loader.getClass(), "getDelegate");
      getDelegate.setAccessible(true);
      Object delegate = getDelegate.invoke(loader);
      Field field = findField(delegate.getClass(), "mixinTransformer");
      field.setAccessible(true);
      Object current = field.get(delegate);
      if (!(current instanceof IMixinTransformer original)) {
        throw new IllegalStateException("Knot has no Mixin transformer yet: " + current);
      }
      field.set(delegate, new WrappingMixinTransformer(original, instrumenters));
    } catch (Throwable t) {
      LOGGER.error("Could not install the bytecode transformer; the game runs as vanilla", t);
      return;
    }
    installed = true;
    for (Instrumenter instrumenter : instrumenters) {
      instrumenter.installed();
    }
  }

  private static Method findMethod(Class<?> type, String name) throws NoSuchMethodException {
    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
      for (Method method : c.getDeclaredMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == 0) {
          return method;
        }
      }
    }
    throw new NoSuchMethodException(type.getName() + "." + name + "()");
  }

  private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (field.getName().equals(name)) {
          return field;
        }
      }
    }
    throw new NoSuchFieldException(type.getName() + "." + name);
  }
}
