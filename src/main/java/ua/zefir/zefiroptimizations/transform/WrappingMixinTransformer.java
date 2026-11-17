package ua.zefir.zefiroptimizations.transform;

import java.util.List;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.IExtensionRegistry;

final class WrappingMixinTransformer implements IMixinTransformer {
  private final IMixinTransformer delegate;
  private final Instrumenter[] instrumenters;

  WrappingMixinTransformer(IMixinTransformer delegate, List<Instrumenter> instrumenters) {
    this.delegate = delegate;
    this.instrumenters = instrumenters.toArray(Instrumenter[]::new);
  }

  @Override
  public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
    Object[] tokens = new Object[instrumenters.length];
    for (int i = 0; i < instrumenters.length; i++) {
      tokens[i] = instrumenters[i].enterClassLoading();
    }
    try {
      byte[] bytes = delegate.transformClassBytes(name, transformedName, basicClass);
      String internalName = name.replace('.', '/');
      for (Instrumenter instrumenter : instrumenters) {
        bytes = instrumenter.instrument(internalName, bytes);
      }
      return bytes;
    } finally {
      for (int i = instrumenters.length - 1; i >= 0; i--) {
        instrumenters[i].exitClassLoading(tokens[i]);
      }
    }
  }

  @Override
  public void audit(MixinEnvironment environment) {
    delegate.audit(environment);
  }

  @Override
  public List<String> reload(String mixinClass, ClassNode classNode) {
    return delegate.reload(mixinClass, classNode);
  }

  @Override
  public boolean computeFramesForClass(
      MixinEnvironment environment, String name, ClassNode classNode) {
    return delegate.computeFramesForClass(environment, name, classNode);
  }

  @Override
  public byte[] transformClass(MixinEnvironment environment, String name, byte[] classBytes) {
    return delegate.transformClass(environment, name, classBytes);
  }

  @Override
  public boolean transformClass(MixinEnvironment environment, String name, ClassNode classNode) {
    return delegate.transformClass(environment, name, classNode);
  }

  @Override
  public boolean couldTransformClass(MixinEnvironment environment, String name) {
    if (delegate.couldTransformClass(environment, name)) {
      return true;
    }
    if (name == null) {
      return false;
    }
    String internalName = name.replace('.', '/');
    for (Instrumenter instrumenter : instrumenters) {
      if (instrumenter.mightInstrument(internalName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public byte[] generateClass(MixinEnvironment environment, String name) {
    return delegate.generateClass(environment, name);
  }

  @Override
  public boolean generateClass(MixinEnvironment environment, String name, ClassNode classNode) {
    return delegate.generateClass(environment, name, classNode);
  }

  @Override
  public IExtensionRegistry getExtensions() {
    return delegate.getExtensions();
  }
}
