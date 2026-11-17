package ua.zefir.zephyrium.process.profile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ua.zefir.zephyrium.process.EntityProcess;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.message.MainNode;
import ua.zefir.zephyrium.process.message.WaitNode;
import ua.zefir.zephyrium.process.sharedstate.SharedState;
import ua.zefir.zephyrium.process.supervision.Supervisor;
import ua.zefir.zephyrium.process.world.MainQueue;

public record ProcessStatus(
    int processes,
    int waitingOnServer,
    int waitingOnEntities,
    long queuedMessages,
    int serverTasks,
    List<Map.Entry<String, Integer>> types,
    Supervisor.Status supervisor,
    SharedState.Status sharedState,
    Session session) {
  private static final int TYPES_SHOWN = 8;

  public record Session(boolean running, long elapsedSeconds, int requestedSeconds) {}

  public static ProcessStatus read() {
    List<EntityProcess> live = Processes.liveProcessList();
    int waitingOnServer = 0;
    int waitingOnEntities = 0;
    long queued = 0;
    Map<String, Integer> byType = new HashMap<>();
    for (EntityProcess process : live) {
      WaitNode on = process.waitingOn();
      if (on == MainNode.INSTANCE) {
        waitingOnServer++;
      } else if (on != null) {
        waitingOnEntities++;
      }
      queued += process.mailboxSize();
      byType.merge(process.stats.type, 1, Integer::sum);
    }
    return new ProcessStatus(
        live.size(),
        waitingOnServer,
        waitingOnEntities,
        queued,
        MainQueue.size(),
        topTypes(byType),
        Supervisor.status(),
        SharedState.status(),
        new Session(Profiler.isActive(), Profiler.elapsedSeconds(), Profiler.requestedSeconds()));
  }

  private static List<Map.Entry<String, Integer>> topTypes(Map<String, Integer> byType) {
    return byType.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(TYPES_SHOWN)
        .map(e -> Map.entry(e.getKey(), e.getValue()))
        .toList();
  }
}
