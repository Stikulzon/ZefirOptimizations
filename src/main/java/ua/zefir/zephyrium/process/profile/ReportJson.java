package ua.zefir.zephyrium.process.profile;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ua.zefir.zephyrium.process.profile.ProfileResult.EntityCost;
import ua.zefir.zephyrium.process.profile.ProfileResult.ServerTotals;
import ua.zefir.zephyrium.process.profile.ProfileResult.SiteTotals;
import ua.zefir.zephyrium.process.profile.ProfileResult.TypeTotals;
import ua.zefir.zephyrium.process.profile.ProfileResult.Window;

final class ReportJson {
  private ReportJson() {}

  static String render(ProfileResult result) {
    JsonObject root = new JsonObject();
    root.addProperty("verdict", result.verdict().name());
    root.addProperty("warmingUp", result.warmingUp());
    root.add("window", window(result.window()));
    root.add("server", server(result.server(), result.window()));
    root.add("cost", cost(result.cost()));
    root.add("counters", counters(result));
    root.add("types", types(result));
    root.add("sites", sites(result));
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }

  private static JsonObject window(Window window) {
    JsonObject json = new JsonObject();
    json.addProperty("startedAt", window.startedAt());
    json.addProperty("seconds", window.seconds());
    json.addProperty("requestedSeconds", window.requestedSeconds());
    json.addProperty("processes", window.processes());
    json.addProperty("carrierThreads", window.carrierThreads());
    return json;
  }

  private static JsonObject server(ServerTotals server, Window window) {
    JsonObject json = new JsonObject();
    json.addProperty("ticks", server.ticks());
    json.addProperty("tps", server.tps(window));
    json.addProperty("tickAvgNanos", server.tickAvgNanos());
    json.addProperty("tickMaxNanos", server.tickMaxNanos());
    json.addProperty("waitNanos", server.waitNanos());
    json.addProperty("waits", server.waits());
    json.addProperty("taskNanos", server.taskNanos());
    json.addProperty("tasks", server.tasks());
    return json;
  }

  private static JsonObject cost(EntityCost cost) {
    JsonObject json = new JsonObject();
    json.addProperty("entityTicks", cost.entityTicks());
    json.addProperty("processTicks", cost.processTicks());
    json.addProperty("serverEntityTicks", cost.serverEntityTicks());
    json.addProperty("skippedTicks", cost.skippedTicks());
    json.addProperty("skippedShare", cost.skippedShare());
    json.addProperty("cpuPerEntityTickNanos", cost.cpuPerEntityTickNanos());
    json.addProperty("entityCpuNanos", cost.entityCpuNanos());
    json.addProperty("carrierCpuNanos", cost.carrierCpuNanos());
    json.addProperty("serverEntityCpuNanos", cost.serverEntityCpuNanos());
    json.addProperty("serverTaskNanos", cost.serverTaskNanos());
    json.addProperty("jvmCpuNanos", cost.jvmCpuNanos());
    json.addProperty("serverCpuNanos", cost.serverCpuNanos());
    json.addProperty("allocatedBytes", cost.allocatedBytes());
    json.addProperty("bytesPerEntityTick", cost.bytesPerEntityTick());
    json.addProperty("gcMillis", cost.gcMillis());
    json.addProperty("gcCount", cost.gcCount());
    json.addProperty("jitMillis", cost.jitMillis());
    return json;
  }

  private static JsonObject counters(ProfileResult result) {
    JsonObject json = new JsonObject();
    result.counters().forEach((counter, value) -> json.addProperty(counter.name(), value));
    return json;
  }

  private static JsonArray types(ProfileResult result) {
    JsonArray array = new JsonArray();
    for (TypeTotals t : result.types()) {
      JsonObject json = new JsonObject();
      json.addProperty("type", t.type());
      json.addProperty("processes", t.processes());
      json.addProperty("ticks", t.ticks());
      json.addProperty("tickNanos", t.tickNanos());
      json.addProperty("tickMaxNanos", t.tickMaxNanos());
      json.addProperty("serveNanos", t.serveNanos());
      json.addProperty("waitNanos", t.waitNanos());
      json.addProperty("publishNanos", t.publishNanos());
      json.addProperty("callsToEntities", t.callsToEntities());
      json.addProperty("callsToMain", t.callsToMain());
      json.addProperty("served", t.served());
      json.addProperty("skipped", t.skipped());
      json.addProperty("timeouts", t.timeouts());
      json.addProperty("crashes", t.crashes());
      array.add(json);
    }
    return array;
  }

  private static JsonArray sites(ProfileResult result) {
    JsonArray array = new JsonArray();
    for (SiteTotals s : result.sites()) {
      JsonObject json = new JsonObject();
      json.addProperty("kind", s.kind());
      json.addProperty("site", s.site());
      json.addProperty("count", s.count());
      json.addProperty("waitNanos", s.waitNanos());
      json.addProperty("maxWaitNanos", s.maxNanos());
      array.add(json);
    }
    return array;
  }
}
