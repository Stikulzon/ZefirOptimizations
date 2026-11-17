package ua.zefir.zefiroptimizations.process.supervision;

public class ProcessCallTimeoutException extends RuntimeException {
  private final boolean blamesServer;

  public ProcessCallTimeoutException(String message) {
    this(message, false);
  }

  public ProcessCallTimeoutException(String message, boolean blamesServer) {
    super(message);
    this.blamesServer = blamesServer;
  }

  boolean blamesServer() {
    return blamesServer;
  }
}
