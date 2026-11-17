package ua.zefir.zefiroptimizations.process.message;

import java.util.concurrent.locks.LockSupport;
import ua.zefir.zefiroptimizations.process.EntityProcess;
import ua.zefir.zefiroptimizations.process.Processes;

public interface ReplySink {
  void reply(Object value, Throwable error, boolean retry);

  record ToProcess(EntityProcess caller, long id) implements ReplySink {
    @Override
    public void reply(Object value, Throwable error, boolean retry) {
      caller.deliver(new Msg.Reply(id, value, error, retry));
    }
  }

  final class Waiter implements ReplySink {
    private final Thread thread = Thread.currentThread();
    private volatile boolean done;
    private Object value;
    private Throwable error;
    private boolean retry;

    @Override
    public synchronized void reply(Object value, Throwable error, boolean retry) {
      if (done) {
        return;
      }
      this.value = value;
      this.error = error;
      this.retry = retry;
      this.done = true;
      LockSupport.unpark(thread);
    }

    public boolean isDone() {
      return done;
    }

    public boolean isRetry() {
      return retry;
    }

    public Object valueOrThrow() {
      if (error != null) {
        throw Processes.sneakyThrow(error);
      }
      return value;
    }
  }
}
