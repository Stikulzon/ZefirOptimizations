package ua.zefir.zefiroptimizations.process.supervision;

public final class ProcessKilledError extends Error {
  public static final ProcessKilledError INSTANCE = new ProcessKilledError();

  private ProcessKilledError() {
    super("entity process was killed", null, false, false);
  }
}
