package ua.zefir.zefiroptimizations.transform;

public interface Instrumenter {
  byte[] instrument(String internalName, byte[] bytes);

  default boolean mightInstrument(String internalName) {
    return true;
  }

  default Object enterClassLoading() {
    return null;
  }

  default void exitClassLoading(Object token) {}

  default void installed() {}
}
