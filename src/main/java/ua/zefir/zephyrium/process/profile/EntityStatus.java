package ua.zefir.zephyrium.process.profile;

import net.minecraft.world.entity.Entity;
import ua.zefir.zephyrium.process.EntityProcess;
import ua.zefir.zephyrium.process.ProcessEntity;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.message.WaitNode;
import ua.zefir.zephyrium.process.supervision.Supervisor;

public record EntityStatus(
    String name,
    String type,
    double x,
    double y,
    double z,
    Owner owner,
    String ownerName,
    String notInProcessReason,
    ProcessState process,
    SessionStats session) {
  public enum Owner {
    NOBODY,
    SERVER,
    OTHER
  }

  public enum State {
    RUNNING,
    IDLE,
    WAITING
  }

  public record ProcessState(
      String thread,
      State state,
      String waitingOn,
      int mailbox,
      int stashed,
      boolean ticking,
      long skippedSinceSpawn) {}

  public record SessionStats(
      boolean running,
      long ticks,
      long tickAvgNanos,
      long tickMaxNanos,
      long waitNanos,
      long publishNanos,
      long callsToEntities,
      long callsToMain,
      long callsServed,
      long castsServed,
      long timeouts,
      long crashes) {}

  public static EntityStatus of(Entity entity) {
    EntityProcess process = Processes.processOf(entity);
    Thread owner = ((ProcessEntity) entity).zo$owner();
    return new EntityStatus(
        Processes.describe(entity),
        Profiler.typeId(entity),
        entity.getX(),
        entity.getY(),
        entity.getZ(),
        ownerOf(owner),
        owner == null ? null : owner.getName(),
        Supervisor.canRunInProcess(entity) ? null : Supervisor.whyNotInProcess(entity),
        process == null ? null : processState(process),
        process == null ? null : sessionStats(process.stats));
  }

  private static Owner ownerOf(Thread owner) {
    if (owner == null) {
      return Owner.NOBODY;
    }
    return owner == Processes.mainThread() ? Owner.SERVER : Owner.OTHER;
  }

  private static ProcessState processState(EntityProcess process) {
    WaitNode on = process.waitingOn();
    State state =
        on != null
            ? State.WAITING
            : process.thread.getState() == Thread.State.RUNNABLE ? State.RUNNING : State.IDLE;
    return new ProcessState(
        process.thread.getName(),
        state,
        on == null ? null : Processes.describeNode(on),
        process.mailboxSize(),
        process.stashSize(),
        process.isTickable(),
        process.skippedTicks());
  }

  private static SessionStats sessionStats(ProcessStats s) {
    if (s.ticks <= 0 && s.callsServed <= 0) {
      return null;
    }
    return new SessionStats(
        Profiler.isActive(),
        s.ticks,
        s.nanos[ProcessStats.TICK] / Math.max(1, s.ticks),
        s.tickMaxNanos,
        s.nanos[ProcessStats.WAIT],
        s.nanos[ProcessStats.PUBLISH],
        s.callsToEntities,
        s.callsToMain,
        s.callsServed,
        s.castsServed,
        s.timeouts,
        s.crashes);
  }
}
