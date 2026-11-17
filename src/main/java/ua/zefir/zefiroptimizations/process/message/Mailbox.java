package ua.zefir.zefiroptimizations.process.message;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import ua.zefir.zefiroptimizations.process.EntityProcess;
import ua.zefir.zefiroptimizations.process.profile.ProfileCounters;
import ua.zefir.zefiroptimizations.process.profile.ProfileCounters.Counter;

public final class Mailbox extends ConcurrentLinkedQueue<Msg> {
  private final transient EntityProcess owner;
  private transient volatile Thread waiter;

  public Mailbox(EntityProcess owner) {
    this.owner = owner;
  }

  @Override
  public boolean offer(Msg msg) {
    super.offer(msg);
    Thread sleeping = waiter;
    if (sleeping != null) {
      LockSupport.unpark(sleeping);
    }
    return true;
  }

  public Msg take() throws InterruptedException {
    return await(false, 0L);
  }

  public Msg poll(long timeoutNanos) throws InterruptedException {
    return await(true, System.nanoTime() + timeoutNanos);
  }

  private Msg await(boolean timed, long deadline) throws InterruptedException {
    Msg msg = poll();
    if (msg != null) {
      return msg;
    }
    waiter = Thread.currentThread();
    try {
      while (true) {
        msg = poll();
        if (msg != null) {
          return msg;
        }
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }
        ProfileCounters.count(owner, Counter.WAKES);
        if (!timed) {
          LockSupport.park(this);
          continue;
        }
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          return null;
        }
        LockSupport.parkNanos(this, remaining);
      }
    } finally {
      waiter = null;
    }
  }
}
