package ua.zefir.zefiroptimizations.process.message;

public final class MainNode implements WaitNode {
  public static final MainNode INSTANCE = new MainNode();

  public volatile WaitNode waitingOn;

  public long chain;

  @Override
  public WaitNode waitingOn() {
    return waitingOn;
  }
}
