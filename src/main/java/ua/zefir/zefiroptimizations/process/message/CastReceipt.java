package ua.zefir.zefiroptimizations.process.message;

import java.util.concurrent.atomic.AtomicInteger;
import ua.zefir.zefiroptimizations.process.ProcessEntity;

public final class CastReceipt {
  public static final CastReceipt UNTRACKED = new CastReceipt(null, null);

  private final AtomicInteger sender;
  private final ProcessEntity target;

  private CastReceipt(AtomicInteger sender, ProcessEntity target) {
    this.sender = sender;
    this.target = target;
  }

  public static CastReceipt tracked(Object entity) {
    AtomicInteger sender = PendingCasts.current().increment(entity);
    ProcessEntity target = (ProcessEntity) entity;
    target.zo$addReadGate(1);
    return new CastReceipt(sender, target);
  }

  public void done() {
    if (sender != null) {
      sender.decrementAndGet();
    }
    if (target != null) {
      target.zo$addReadGate(-1);
    }
  }
}
