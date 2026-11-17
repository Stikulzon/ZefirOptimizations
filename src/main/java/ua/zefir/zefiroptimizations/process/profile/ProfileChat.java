package ua.zefir.zefiroptimizations.process.profile;

import static ua.zefir.zefiroptimizations.command.Feedback.message;
import static ua.zefir.zefiroptimizations.process.profile.Units.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ua.zefir.zefiroptimizations.command.ChatEvents;
import ua.zefir.zefiroptimizations.command.Feedback;
import ua.zefir.zefiroptimizations.process.profile.ProfileResult.EntityCost;
import ua.zefir.zefiroptimizations.process.profile.ProfileResult.ServerTotals;
import ua.zefir.zefiroptimizations.process.profile.ProfileResult.SiteTotals;
import ua.zefir.zefiroptimizations.process.profile.ProfileResult.TypeTotals;
import ua.zefir.zefiroptimizations.process.profile.ProfileResult.Verdict;
import ua.zefir.zefiroptimizations.process.sharedstate.SharedState;
import ua.zefir.zefiroptimizations.process.supervision.Supervisor;

final class ProfileChat {
  private static final String PROFILE_COMMAND = "/zo profile ";
  private static final String MINECRAFT_PREFIX = "minecraft:";
  private static final String INDENT = "  ";
  private static final int ROWS_SHOWN = 3;
  private static final int SITE_CHARS = 48;
  private static final String ELLIPSIS = "…";
  private static final double WARN_SKIPPED_SHARE = 0.01;
  private static final ChatFormatting LABEL = ChatFormatting.GRAY;
  private static final ChatFormatting VALUE = ChatFormatting.WHITE;
  private static final ChatFormatting BUTTON = ChatFormatting.AQUA;

  private ProfileChat() {}

  static List<Component> summary(ProfileResult result, String reportPath) {
    List<Component> lines = new ArrayList<>();
    lines.add(title(result));
    if (result.warmingUp()) {
      lines.add(
          message("profile.summary.warming_up", result.cost().jitMillis())
              .withStyle(ChatFormatting.YELLOW));
    }
    lines.add(serverLine(result.server(), result.window()));
    lines.add(entityLine(result.cost()));
    lines.add(cpuLine(result.cost(), result.window()));
    if (result.server().waits() > 0) {
      lines.add(waitLine(result.server()));
    }
    lines.addAll(typeRows(result.types()));
    lines.addAll(siteRows(result.sites()));
    lines.add(summaryButtons(reportPath, result.window().requestedSeconds()));
    return lines;
  }

  static List<Component> started(int seconds) {
    MutableComponent line =
        seconds == Feedback.UNTIL_STOPPED
            ? message("profile.started_until_stopped")
            : message("profile.started", seconds);
    return List.of(line.append(" ").append(button("stop", PROFILE_COMMAND + "stop")));
  }

  static List<Component> status(ProcessStatus status) {
    List<Component> lines = new ArrayList<>();
    lines.add(
        row(
            "status.processes",
            value(Units.count(status.processes())),
            value(status.waitingOnServer()),
            value(status.waitingOnEntities())));
    lines.add(
        row(
            "status.queued",
            value(Units.count(status.queuedMessages())),
            value(Units.count(status.serverTasks()))));
    lines.add(row("status.types", typeList(status.types())));
    lines.addAll(supervisorRows(status.supervisor()));
    lines.addAll(sharedStateRows(status.sharedState()));
    lines.add(sessionRow(status.session()));
    return lines;
  }

  static List<Component> entity(EntityStatus status) {
    List<Component> lines = new ArrayList<>();
    lines.add(
        message(
                "profile.entity.title",
                value(status.name()),
                value(shortType(status.type())),
                value(format("%.1f %.1f %.1f", status.x(), status.y(), status.z())))
            .withStyle(LABEL));
    if (status.process() == null) {
      lines.add(noProcessRow(status));
      return lines;
    }
    lines.add(processRow(status.process()));
    lines.add(
        status.session() == null
            ? message("profile.entity.no_data")
                .withStyle(LABEL)
                .append(" ")
                .append(
                    button(
                        "start",
                        PROFILE_COMMAND + "start " + Feedback.DEFAULT_SECONDS,
                        Feedback.DEFAULT_SECONDS))
            : sessionStatsRow(status.session()));
    return lines;
  }

  private static Component title(ProfileResult result) {
    Verdict verdict = result.verdict();
    return message("profile.summary.title", format("%.1f", result.window().seconds()))
        .withStyle(ChatFormatting.BOLD)
        .append(" ")
        .append(
            message("profile.verdict." + verdict.name().toLowerCase(Locale.ROOT))
                .withStyle(verdictColor(verdict), ChatFormatting.BOLD)
                .withStyle(
                    s -> s.withHoverEvent(ChatEvents.showText(message("profile.hover.verdict")))));
  }

  private static ChatFormatting verdictColor(Verdict verdict) {
    return switch (verdict) {
      case HEALTHY -> ChatFormatting.GREEN;
      case BUSY -> ChatFormatting.YELLOW;
      case OVERLOADED -> ChatFormatting.RED;
    };
  }

  private static Component serverLine(ServerTotals server, ProfileResult.Window window) {
    return labelled(
        "server",
        message(
            "profile.summary.server",
            value(format("%.1f", server.tps(window))),
            value(Units.millisNumber(server.tickAvgNanos())),
            value(Units.millisNumber(server.tickMaxNanos()))));
  }

  private static Component entityLine(EntityCost cost) {
    ChatFormatting skippedColor =
        cost.skippedShare() > WARN_SKIPPED_SHARE ? ChatFormatting.YELLOW : VALUE;
    return labelled(
        "entities",
        message(
            "profile.summary.entities",
            value(Units.count(cost.entityTicks())),
            value(Units.microsNumber(cost.cpuPerEntityTickNanos())),
            Component.literal(Units.percent(cost.skippedShare())).withStyle(skippedColor)));
  }

  private static Component cpuLine(EntityCost cost, ProfileResult.Window window) {
    return labelled(
        "cpu",
        message(
            "profile.summary.cpu",
            value(Units.cores(cost.jvmCpuNanos(), window.nanos())),
            value(Units.cores(cost.serverCpuNanos(), window.nanos())),
            value(Units.cores(cost.carrierCpuNanos(), window.nanos())),
            value(Units.bytes(cost.bytesPerEntityTick())),
            value(cost.gcMillis())));
  }

  private static Component waitLine(ServerTotals server) {
    return labelled(
        "waits",
        message(
            "profile.summary.waits",
            value(Units.millisNumber(server.waitNanos() / Math.max(1, server.ticks()))),
            value(Units.count(server.waits()))));
  }

  private static List<Component> typeRows(List<TypeTotals> types) {
    if (types.isEmpty()) {
      return List.of();
    }
    List<Component> rows = new ArrayList<>();
    rows.add(message("profile.summary.types").withStyle(LABEL));
    for (TypeTotals t : types.subList(0, Math.min(ROWS_SHOWN, types.size()))) {
      rows.add(
          Component.literal(INDENT)
              .append(
                  message(
                          "profile.summary.type_row",
                          value(shortType(t.type())),
                          value(Units.count(t.processes())),
                          value(Units.microsNumber(t.perTick(t.tickNanos()))),
                          value(Units.microsNumber(t.perTick(t.waitNanos()))),
                          value(Units.count(t.skipped())))
                      .withStyle(LABEL)));
    }
    return rows;
  }

  private static List<Component> siteRows(List<SiteTotals> sites) {
    List<SiteTotals> waited =
        sites.stream().filter(s -> s.waitNanos() > 0).limit(ROWS_SHOWN).toList();
    if (waited.isEmpty()) {
      return List.of();
    }
    List<Component> rows = new ArrayList<>();
    rows.add(message("profile.summary.sites").withStyle(LABEL));
    for (SiteTotals s : waited) {
      rows.add(
          Component.literal(INDENT)
              .append(
                  message(
                          "profile.summary.site_row",
                          value(Units.millisNumber(s.waitNanos())),
                          value(Units.count(s.count())),
                          Component.literal(shorten(s.site()))
                              .withStyle(VALUE)
                              .withStyle(
                                  style ->
                                      style.withHoverEvent(
                                          ChatEvents.showText(
                                              Component.literal(s.kind() + "\n" + s.site())))))
                      .withStyle(LABEL)));
    }
    return rows;
  }

  private static Component summaryButtons(String reportPath, int seconds) {
    MutableComponent buttons = Component.empty();
    if (reportPath != null) {
      buttons
          .append(
              button("copy_path", null)
                  .withStyle(
                      s ->
                          s.withClickEvent(ChatEvents.copyToClipboard(reportPath))
                              .withHoverEvent(
                                  ChatEvents.showText(
                                      message("profile.button.copy_path.hover", reportPath)))))
          .append(" ");
    }
    int again = seconds > 0 ? seconds : Feedback.DEFAULT_SECONDS;
    return buttons
        .append(button("run_again", PROFILE_COMMAND + "start " + again, again))
        .append(" ")
        .append(button("status", PROFILE_COMMAND + "status"));
  }

  private static List<Component> supervisorRows(Supervisor.Status supervisor) {
    List<Component> rows = new ArrayList<>();
    rows.add(
        row(
            "status.supervisor",
            value(supervisor.restarts()),
            value(supervisor.rollbacks()),
            value(supervisor.kills()),
            value(supervisor.deaths())));
    for (Map.Entry<String, Long> demoted : supervisor.demotedSeconds().entrySet()) {
      rows.add(
          Component.literal(INDENT)
              .append(
                  message(
                          "profile.status.demoted",
                          value(shortType(demoted.getKey())),
                          value(demoted.getValue()))
                      .withStyle(LABEL)));
    }
    return rows;
  }

  private static List<Component> sharedStateRows(SharedState.Status shared) {
    List<Component> rows = new ArrayList<>();
    rows.add(
        row(
            "status.shared",
            value(Units.count(shared.routedMethods())),
            value(Units.count(shared.routedCalls()))));
    for (SharedState.RoutedSite site : shared.sites()) {
      MutableComponent name = value(site.name());
      if (site.reason() != null) {
        name.withStyle(
            s -> s.withHoverEvent(ChatEvents.showText(Component.literal(site.reason()))));
      }
      rows.add(
          Component.literal(INDENT)
              .append(
                  message("profile.status.shared_row", name, value(Units.count(site.calls())))
                      .withStyle(LABEL)));
    }
    if (shared.blocked().isEmpty()) {
      return rows;
    }
    rows.add(row("status.blocked", value(shared.blocked().size())));
    shared
        .blocked()
        .forEach(
            (type, reason) ->
                rows.add(
                    Component.literal(INDENT)
                        .append(value(type))
                        .append(Component.literal(": " + reason).withStyle(LABEL))));
    return rows;
  }

  private static Component sessionRow(ProcessStatus.Session session) {
    if (!session.running()) {
      return message("profile.status.not_profiling")
          .withStyle(LABEL)
          .append(" ")
          .append(
              button(
                  "start",
                  PROFILE_COMMAND + "start " + Feedback.DEFAULT_SECONDS,
                  Feedback.DEFAULT_SECONDS));
    }
    MutableComponent progress =
        session.requestedSeconds() > 0
            ? message(
                "profile.status.profiling",
                value(session.elapsedSeconds()),
                value(session.requestedSeconds()))
            : message("profile.status.profiling_open", value(session.elapsedSeconds()));
    return progress.withStyle(LABEL).append(" ").append(button("stop", PROFILE_COMMAND + "stop"));
  }

  private static Component noProcessRow(EntityStatus status) {
    MutableComponent owner =
        switch (status.owner()) {
          case NOBODY -> message("profile.entity.owner.nobody");
          case SERVER -> message("profile.entity.owner.server");
          case OTHER -> Component.literal(status.ownerName());
        };
    MutableComponent row = message("profile.entity.no_process", owner.withStyle(VALUE));
    if (status.notInProcessReason() != null) {
      row.append(Component.literal(" (" + status.notInProcessReason() + ")"));
    }
    return row.withStyle(LABEL);
  }

  private static Component processRow(EntityStatus.ProcessState process) {
    MutableComponent state =
        switch (process.state()) {
          case RUNNING -> message("profile.entity.state.running");
          case IDLE -> message("profile.entity.state.idle");
          case WAITING -> message("profile.entity.state.waiting", process.waitingOn());
        };
    return message(
            "profile.entity.process",
            value(process.thread()),
            state.withStyle(VALUE),
            value(process.mailbox()),
            value(process.stashed()),
            message(process.ticking() ? "profile.entity.ticking" : "profile.entity.not_ticking")
                .withStyle(VALUE),
            value(Units.count(process.skippedSinceSpawn())))
        .withStyle(LABEL);
  }

  private static Component sessionStatsRow(EntityStatus.SessionStats s) {
    MutableComponent row =
        message(
            s.running() ? "profile.entity.session.current" : "profile.entity.session.last",
            value(Units.count(s.ticks())),
            value(Units.microsNumber(s.tickAvgNanos())),
            value(Units.millisNumber(s.tickMaxNanos())),
            value(Units.millisNumber(s.waitNanos())),
            value(Units.millisNumber(s.publishNanos())),
            value(Units.count(s.callsToEntities())),
            value(Units.count(s.callsToMain())),
            value(Units.count(s.callsServed())),
            value(Units.count(s.castsServed())));
    if (s.timeouts() + s.crashes() > 0) {
      row.append(" ")
          .append(
              message("profile.entity.failures", s.timeouts(), s.crashes())
                  .withStyle(ChatFormatting.RED));
    }
    return row.withStyle(LABEL);
  }

  private static Component typeList(List<Map.Entry<String, Integer>> types) {
    if (types.isEmpty()) {
      return message("profile.status.types_none").withStyle(VALUE);
    }
    MutableComponent list = Component.empty();
    for (int i = 0; i < types.size(); i++) {
      Map.Entry<String, Integer> type = types.get(i);
      if (i > 0) {
        list.append(Component.literal(", ").withStyle(LABEL));
      }
      list.append(value(shortType(type.getKey())))
          .append(Component.literal(" " + Units.count(type.getValue())).withStyle(LABEL));
    }
    return list;
  }

  private static Component row(String key, Object... arguments) {
    return message("profile." + key, arguments).withStyle(LABEL);
  }

  private static Component labelled(String label, MutableComponent body) {
    return message("profile.label." + label)
        .withStyle(
            s ->
                s.withColor(ChatFormatting.DARK_AQUA)
                    .withHoverEvent(ChatEvents.showText(message("profile.hover." + label))))
        .append(Component.literal(" "))
        .append(body.withStyle(LABEL));
  }

  private static MutableComponent value(Object value) {
    return Component.literal(String.valueOf(value)).withStyle(VALUE);
  }

  private static MutableComponent button(String key, String command, Object... arguments) {
    MutableComponent button =
        Component.literal("[")
            .append(message("profile.button." + key, arguments))
            .append("]")
            .withStyle(BUTTON);
    if (command == null) {
      return button;
    }
    return button.withStyle(
        s ->
            s.withClickEvent(ChatEvents.runCommand(command))
                .withHoverEvent(ChatEvents.showText(Component.literal(command))));
  }

  private static String shortType(String type) {
    return type.startsWith(MINECRAFT_PREFIX) ? type.substring(MINECRAFT_PREFIX.length()) : type;
  }

  private static String shorten(String site) {
    return site.length() <= SITE_CHARS ? site : site.substring(0, SITE_CHARS) + ELLIPSIS;
  }
}
