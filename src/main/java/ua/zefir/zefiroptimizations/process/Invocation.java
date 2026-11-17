package ua.zefir.zefiroptimizations.process;

@FunctionalInterface
public interface Invocation {
  Object invoke(Object self) throws Throwable;
}
