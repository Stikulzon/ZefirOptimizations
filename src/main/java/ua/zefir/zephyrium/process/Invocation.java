package ua.zefir.zephyrium.process;

@FunctionalInterface
public interface Invocation {
  Object invoke(Object self) throws Throwable;
}
