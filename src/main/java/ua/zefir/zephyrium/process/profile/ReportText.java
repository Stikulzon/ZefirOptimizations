package ua.zefir.zephyrium.process.profile;

import static ua.zefir.zephyrium.process.profile.Units.format;

import java.util.ArrayList;
import java.util.List;
import ua.zefir.zephyrium.process.profile.ProfileCounters.Counter;
import ua.zefir.zephyrium.process.profile.ProfileResult.EntityCost;
import ua.zefir.zephyrium.process.profile.ProfileResult.ServerTotals;
import ua.zefir.zephyrium.process.profile.ProfileResult.SiteTotals;
import ua.zefir.zephyrium.process.profile.ProfileResult.SlowTick;
import ua.zefir.zephyrium.process.profile.ProfileResult.TypeTotals;
import ua.zefir.zephyrium.process.profile.ProfileResult.Window;

final class ReportText {
  private static final String TYPE_ROW = "%-28s %6s %9s %9s %9s %9s %9s %6s %6s %7s %9s";
  private static final String SITE_ROW = "%-17s %10s %9s %11s %10s  %s";

  private ReportText() {}

  static List<String> render(ProfileResult result) {
    List<String> lines = new ArrayList<>();
    lines.addAll(header(result));
    if (result.warmingUp()) {
      lines.add(
          "Warning: the JIT compiler was still busy, so the server is warming up and these numbers"
              + " are higher and noisier than they will be. Profile again in a few minutes.");
    }
    section(lines, "Server thread", server(result.server(), result.window()));
    section(lines, "Entity cost", cost(result.cost(), result.window()));
    section(lines, "Runtime events per entity tick", counters(result));
    section(lines, "By entity type", types(result.types(), result.server().ticks()));
    section(lines, "Slowest single ticks", slowest(result.slowest()));
    section(lines, "Messages between threads (-> waits for a reply, ~> does not)", sites(result));
    section(lines, "Glossary", glossary());
    return lines;
  }

  private static void section(List<String> lines, String title, List<String> body) {
    lines.add("");
    lines.add("== " + title + " ==");
    lines.addAll(body.isEmpty() ? List.of("(nothing recorded)") : body);
  }

  private static List<String> header(ProfileResult result) {
    Window window = result.window();
    return List.of(
        format(
            "Entity profile started %s, %.1f s; verdict: %s",
            window.startedAt(), window.seconds(), result.verdict()),
        format(
            "%d entity processes on %d carrier threads",
            window.processes(), window.carrierThreads()));
  }

  private static List<String> server(ServerTotals server, Window window) {
    long ticks = Math.max(1, server.ticks());
    return List.of(
        format(
            "%d ticks, %.1f TPS; tick avg %s, max %s",
            server.ticks(),
            server.tps(window),
            Units.millis(server.tickAvgNanos()),
            Units.millis(server.tickMaxNanos())),
        format(
            "Waited on entities %s per tick (%s calls); ran their tasks %s per tick (%s tasks)",
            Units.millis(server.waitNanos() / ticks),
            Units.count(server.waits()),
            Units.millis(server.taskNanos() / ticks),
            Units.count(server.tasks())));
  }

  private static List<String> cost(EntityCost cost, Window window) {
    return List.of(
        format(
            "Entity ticks: %s (%s in processes, %s on the server thread); skipped %s (%s)",
            Units.count(cost.entityTicks()),
            Units.count(cost.processTicks()),
            Units.count(cost.serverEntityTicks()),
            Units.count(cost.skippedTicks()),
            Units.percent(cost.skippedShare())),
        format(
            "CPU per entity tick: %s (carriers %s, server entity loop %s, server tasks %s)",
            Units.micros(cost.cpuPerEntityTickNanos()),
            Units.millis(cost.carrierCpuNanos()),
            Units.millis(cost.serverEntityCpuNanos()),
            Units.millis(cost.serverTaskNanos())),
        format(
            "Allocated per entity tick: %s; GC: %d ms in %d collections; JIT compiling: %d ms",
            Units.bytes(cost.bytesPerEntityTick()),
            cost.gcMillis(),
            cost.gcCount(),
            cost.jitMillis()),
        format(
            "CPU cores used: whole JVM %s, server thread %s, carriers %s",
            Units.cores(cost.jvmCpuNanos(), window.nanos()),
            Units.cores(cost.serverCpuNanos(), window.nanos()),
            Units.cores(cost.carrierCpuNanos(), window.nanos())));
  }

  private static List<String> counters(ProfileResult result) {
    long ticks = result.cost().processTicks();
    long reads = result.counter(Counter.WORLD_READS);
    return List.of(
        format(
            "messages %s (calls %s, casts %s, empty %s), wake-ups %s, snapshot publishes %s",
            Units.ratio(result.counter(Counter.MESSAGES), ticks),
            Units.ratio(result.counter(Counter.CALLS), ticks),
            Units.ratio(result.counter(Counter.CASTS), ticks),
            Units.ratio(result.counter(Counter.EMPTY_MESSAGES), ticks),
            Units.ratio(result.counter(Counter.WAKES), ticks),
            Units.ratio(result.counter(Counter.PUBLISHES), ticks)),
        format(
            "world reads %s; retried %s of them, %s sent to the server thread",
            Units.ratio(reads, ticks),
            reads == 0
                ? Units.NOT_MEASURED
                : Units.percent((double) result.counter(Counter.WORLD_RETRIES) / reads),
            Units.count(result.counter(Counter.WORLD_FALLBACKS))));
  }

  private static List<String> types(List<TypeTotals> types, long serverTicks) {
    List<String> lines = new ArrayList<>();
    lines.add(
        format(
            TYPE_ROW,
            "type",
            "n",
            "ms/tick",
            "tick avg",
            "tick max",
            "wait avg",
            "bookkeep",
            "->ent",
            "->srv",
            "served",
            "skipped"));
    for (TypeTotals t : types) {
      String row =
          format(
              TYPE_ROW,
              t.type(),
              t.processes(),
              Units.millisNumber(t.busyNanos() / Math.max(1, serverTicks)),
              Units.micros(t.perTick(t.tickNanos())),
              Units.millis(t.tickMaxNanos()),
              Units.micros(t.perTick(t.waitNanos())),
              Units.micros(t.perTick(t.publishNanos())),
              format("%.2f", t.perTick((double) t.callsToEntities())),
              format("%.2f", t.perTick((double) t.callsToMain())),
              format("%.2f", t.perTick((double) t.served())),
              Units.count(t.skipped()));
      lines.add(
          t.timeouts() + t.crashes() > 0
              ? row + format("  timeouts %d, crashes %d", t.timeouts(), t.crashes())
              : row);
    }
    return lines;
  }

  private static List<String> slowest(List<SlowTick> slowest) {
    return slowest.stream()
        .map(
            s ->
                format(
                    "%-28s max %s, avg %s over %d ticks, waited %s",
                    s.name(),
                    Units.millis(s.maxNanos()),
                    Units.micros(s.avgNanos()),
                    s.ticks(),
                    Units.millis(s.waitNanos())))
        .toList();
  }

  private static List<String> sites(ProfileResult result) {
    if (result.sites().isEmpty()) {
      return List.of();
    }
    long ticks = Math.max(1, result.server().ticks());
    List<String> lines = new ArrayList<>();
    lines.add(format(SITE_ROW, "kind", "count", "per tick", "total wait", "max wait", "site"));
    for (SiteTotals s : result.sites()) {
      lines.add(
          format(
              SITE_ROW,
              s.kind(),
              Units.count(s.count()),
              format("%.1f", (double) s.count() / ticks),
              Units.millis(s.waitNanos()),
              Units.millis(s.maxNanos()),
              s.site()));
    }
    return lines;
  }

  private static List<String> glossary() {
    return List.of(
        "CPU per entity tick: CPU time of the carrier threads, the server thread's entity loop and"
            + " the tasks entities sent to it, divided by entity ticks. Compare it between"
            + " entityProcesses=true and false; GC and other mods are not in it.",
        "skipped: ticks dropped because the entity's process was still busy with the previous one.",
        "ms/tick: wall time in the type's code per server tick, summed over its processes.",
        "tick avg / max: wall time of one entity tick; wait avg: time blocked on replies per tick.",
        "bookkeep: snapshot, tracker and checkpoint work after each message.",
        "->ent / ->srv: calls made per tick to other entities and to the server thread;"
            + " served: calls and casts handled for others per tick.",
        "empty: wake-up and late reply messages that carried no work;"
            + " wake-ups: times a process parked and was woken again; world reads: reads of shared world"
            + " state, retried when the server thread changed it meanwhile.");
  }
}
