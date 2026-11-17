package ua.zefir.zephyrium.process.sharedstate;

import ua.zefir.zephyrium.process.EntityProcess;
import ua.zefir.zephyrium.process.Invocation;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.supervision.Supervisor;

public final class SharedStateGuard {
  static final String INTERNAL_NAME = "ua/zefir/zephyrium/process/sharedstate/SharedStateGuard";
  static final String TOUCH = "touch";
  static final String TOUCH_DESC = "(Ljava/lang/String;Ljava/lang/String;)V";
  static final String ROUTE = "route";
  static final String CALL = "call";
  private static final String CLASS_INITIALIZER = "<clinit>";
  private static final StackWalker WALKER = StackWalker.getInstance();

  public static boolean route() {
    return SharedState.armed()
        && EntityProcess.current() != null
        && !SharedState.loadingClass()
        && !initializingClass();
  }

  public static Object call(Invocation invocation) {
    SharedState.routed(invocation.getClass());
    return Processes.callMain(invocation, null);
  }

  public static void touch(String owner, String site) {
    if (!SharedState.armed()) {
      return;
    }
    EntityProcess process = EntityProcess.current();
    if (process == null || SharedState.loadingClass() || initializingClass()) {
      return;
    }
    stop(process, owner, site);
    throw new SharedStateError(site);
  }

  private static boolean initializingClass() {
    return WALKER.walk(
        frames -> frames.anyMatch(frame -> frame.getMethodName().equals(CLASS_INITIALIZER)));
  }

  private static void stop(EntityProcess process, String owner, String site) {
    Class<?> entityClass = process.entity.getClass();
    String marked = inHierarchy(entityClass, owner) ? owner : SharedState.internalName(entityClass);
    if (SharedState.mark(marked, site)) {
      SharedState.LOGGER.warn(
          "{} and its subclasses tick on the server thread from now on, because code that could"
              + " not be routed touched shared state: {}",
          marked.replace('/', '.'),
          site);
    }
    process.retire();
    Supervisor.retireUnsafeProcesses();
  }

  private static boolean inHierarchy(Class<?> type, String owner) {
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      if (SharedState.internalName(c).equals(owner)) {
        return true;
      }
    }
    return false;
  }
}
