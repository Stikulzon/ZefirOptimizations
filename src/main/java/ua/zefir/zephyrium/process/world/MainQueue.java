package ua.zefir.zephyrium.process.world;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.message.WaitNode;
import ua.zefir.zephyrium.process.profile.Profiler;

public final class MainQueue {
  record Task(Runnable runnable, long chain, WaitNode origin, boolean awaited) {}

  private static final ConcurrentLinkedQueue<Task> QUEUE = new ConcurrentLinkedQueue<>();
  private static final int MAX_PER_DRAIN = 1 << 16;
  private static final long DRAIN_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
  private static final long WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(2);
  public static volatile Thread mainThread;

  public static void submit(Runnable task) {
    add(new Task(task, 0L, null, false));
  }

  public static void submitCall(Runnable task, long chain, WaitNode origin) {
    add(new Task(task, chain, origin, true));
  }

  private static void add(Task task) {
    QUEUE.add(task);
    Thread main = mainThread;
    if (main != null) {
      LockSupport.unpark(main);
    }
  }

  public static boolean runOne() {
    Task task = QUEUE.poll();
    if (task == null) {
      return false;
    }
    run(task);
    return true;
  }

  public static void drain() {
    int budget = Math.min(QUEUE.size(), MAX_PER_DRAIN);
    long stopAt = System.nanoTime() + DRAIN_BUDGET_NANOS;
    while (budget > 0 && System.nanoTime() - stopAt < 0) {
      long windowEnd = System.nanoTime() + WINDOW_NANOS;
      WorldSeq.beginWrite();
      try {
        do {
          Task task = QUEUE.poll();
          if (task == null) {
            return;
          }
          budget--;
          run(task);
        } while (budget > 0 && System.nanoTime() - windowEnd < 0);
      } finally {
        WorldSeq.endWrite();
      }
    }
  }

  public static boolean runOneRelevant(long chain, WaitNode waitingOn) {
    for (Task task : QUEUE) {
      if (!task.awaited()) {
        continue;
      }
      boolean sameChain = chain != 0L && task.chain() == chain;
      boolean inCycle = task.origin() != null && WaitNode.reaches(waitingOn, task.origin());
      if ((sameChain || inCycle) && QUEUE.remove(task)) {
        run(task);
        return true;
      }
    }
    return false;
  }

  public static boolean isEmpty() {
    return QUEUE.isEmpty();
  }

  public static int size() {
    return QUEUE.size();
  }

  private static void run(Task task) {
    long start = Profiler.isActive() ? System.nanoTime() : 0L;
    try {
      task.runnable().run();
    } catch (Throwable t) {
      Processes.LOGGER.error("Task sent to the server thread by an entity process failed", t);
    } finally {
      if (start != 0L) {
        Profiler.mainTaskRan(System.nanoTime() - start);
      }
    }
  }
}
