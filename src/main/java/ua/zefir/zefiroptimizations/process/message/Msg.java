package ua.zefir.zefiroptimizations.process.message;

import ua.zefir.zefiroptimizations.process.Invocation;

public sealed interface Msg {
  record Tick() implements Msg {
    public static final Tick INSTANCE = new Tick();
  }

  record Call(
      long id, long chain, WaitNode caller, Object target, Invocation invocation, ReplySink sink)
      implements Msg {}

  record Cast(Object target, Invocation invocation, CastReceipt receipt) implements Msg {}

  record Reply(long id, Object value, Throwable error, boolean retry) implements Msg {}

  record Wake() implements Msg {
    public static final Wake INSTANCE = new Wake();
  }

  record Exit() implements Msg {
    public static final Exit INSTANCE = new Exit();
  }
}
