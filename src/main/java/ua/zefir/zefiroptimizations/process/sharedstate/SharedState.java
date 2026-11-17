package ua.zefir.zefiroptimizations.process.sharedstate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.zefir.zefiroptimizations.config.ConfigService;
import ua.zefir.zefiroptimizations.process.ownership.analysis.ClassRepo;
import ua.zefir.zefiroptimizations.process.ownership.analysis.McNames;
import ua.zefir.zefiroptimizations.transform.Instrumenter;

public final class SharedState {
  static final Logger LOGGER = LoggerFactory.getLogger("ZefirOptimizations/shared-state");
  private static final int SHOWN = 10;
  private static final Map<String, String> UNSAFE = new ConcurrentHashMap<>();
  private static final Map<String, String> BLOCKED = new ConcurrentHashMap<>();
  private static final ThreadLocal<int[]> LOADING = ThreadLocal.withInitial(() -> new int[1]);
  private static final AtomicInteger ROUTED_METHODS = new AtomicInteger();
  private static final LongAdder ROUTED_CALLS = new LongAdder();
  private static final Map<Class<?>, LongAdder> ROUTED_BY_SITE = new ConcurrentHashMap<>();
  private static final Map<String, String> ROUTE_REASONS = new ConcurrentHashMap<>();
  private static final String LAMBDA_MARKER = "$$Lambda";
  private static final int SHOWN_SITES = 5;
  private static volatile boolean armed;
  private static final ClassValue<String> INTERNAL_NAMES =
      new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
          return type.getName().replace('.', '/');
        }
      };

  public static Optional<Instrumenter> instrumenter(ClassLoader loader) {
    if (!ConfigService.get().sharedStateGuard) {
      LOGGER.warn(
          "Shared-state routing disabled by sharedStateGuard=false in the config; other mods' code"
              + " runs in entity processes unchecked");
      return Optional.empty();
    }
    try {
      long start = System.nanoTime();
      ModCode code = ModCode.scan();
      McNames names = McNames.load(loader);
      StaticTypes types = new StaticTypes(code, loader, names.random);
      AddedFields added =
          new AddedFields(loader, new ClassRepo(loader, names.entity)::isEntity, names.entityOwned);
      LOGGER.info(
          "Shared-state routing covers {} mods in {} packages, indexed in {} ms",
          code.modCount(),
          code.packageCount(),
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
      return Optional.of(
          new SharedStateInstrumenter(
              code,
              types,
              new SharedAccess(code, types, added, new ReadCalls(names.random), loader),
              ConfigService.get().splitRoutedMethods));
    } catch (Throwable t) {
      LOGGER.error(
          "Could not prepare shared-state routing; other mods' code runs in entity processes"
              + " unchecked",
          t);
      return Optional.empty();
    }
  }

  public static boolean isSafe(Class<?> type) {
    return UNSAFE.isEmpty() || reason(type) == null;
  }

  public static String reason(Class<?> type) {
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      String reason = UNSAFE.get(INTERNAL_NAMES.get(c));
      if (reason != null) {
        BLOCKED.putIfAbsent(INTERNAL_NAMES.get(type), reason);
        return reason;
      }
    }
    return null;
  }

  public static void arm() {
    armed = true;
  }

  static boolean armed() {
    return armed;
  }

  static void enterClassLoading() {
    LOADING.get()[0]++;
  }

  static void exitClassLoading() {
    LOADING.get()[0]--;
  }

  static boolean loadingClass() {
    return LOADING.get()[0] > 0;
  }

  static void routedMethods(int count) {
    ROUTED_METHODS.addAndGet(count);
  }

  static void routeReason(String owner, String site) {
    ROUTE_REASONS.putIfAbsent(owner.replace('/', '.'), site);
  }

  public static boolean routes(Class<?> type) {
    return ROUTE_REASONS.containsKey(type.getName());
  }

  static void routed(Class<?> site) {
    ROUTED_CALLS.increment();
    LongAdder count = ROUTED_BY_SITE.get(site);
    if (count == null) {
      count = ROUTED_BY_SITE.computeIfAbsent(site, key -> new LongAdder());
    }
    count.increment();
  }

  private static String siteName(Class<?> site) {
    String name = site.getName();
    int lambda = name.indexOf(LAMBDA_MARKER);
    return lambda < 0 ? name : name.substring(0, lambda);
  }

  static boolean mark(String owner, String reason) {
    return UNSAFE.putIfAbsent(owner, reason) == null;
  }

  static String internalName(Class<?> type) {
    return INTERNAL_NAMES.get(type);
  }

  public record RoutedSite(String name, long calls, String reason) {}

  public record Status(
      int routedMethods, long routedCalls, List<RoutedSite> sites, Map<String, String> blocked) {}

  public static Status status() {
    List<RoutedSite> sites =
        ROUTED_BY_SITE.entrySet().stream()
            .sorted(Map.Entry.comparingByValue((a, b) -> Long.compare(b.sum(), a.sum())))
            .limit(SHOWN_SITES)
            .map(entry -> routedSite(siteName(entry.getKey()), entry.getValue().sum()))
            .toList();
    Map<String, String> blocked = new TreeMap<>();
    BLOCKED.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .limit(SHOWN)
        .forEach(entry -> blocked.put(entry.getKey().replace('/', '.'), entry.getValue()));
    return new Status(ROUTED_METHODS.get(), ROUTED_CALLS.sum(), sites, blocked);
  }

  private static RoutedSite routedSite(String name, long calls) {
    return new RoutedSite(name, calls, ROUTE_REASONS.get(name));
  }
}
