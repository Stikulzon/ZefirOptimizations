package ua.zefir.zephyrium.process.push;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import net.minecraft.world.entity.Entity;
import ua.zefir.zephyrium.process.EntityProcess;
import ua.zefir.zephyrium.process.ProcessEntity;

public final class Impulse {
  private static final VarHandle X;
  private static final VarHandle Y;
  private static final VarHandle Z;
  private static final VarHandle PENDING;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      X = lookup.findVarHandle(Impulse.class, "x", long.class);
      Y = lookup.findVarHandle(Impulse.class, "y", long.class);
      Z = lookup.findVarHandle(Impulse.class, "z", long.class);
      PENDING = lookup.findVarHandle(Impulse.class, "pending", boolean.class);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @SuppressWarnings("unused")
  private volatile long x, y, z;

  @SuppressWarnings("unused")
  private volatile boolean pending;

  public static boolean add(Entity target, double dx, double dy, double dz) {
    if (!(((ProcessEntity) target).zo$process() instanceof EntityProcess process)
        || process.isKilled()) {
      return false;
    }
    Impulse impulse = process.impulse;
    if (dx != 0.0) {
      addTo(impulse, X, dx);
    }
    if (dy != 0.0) {
      addTo(impulse, Y, dy);
    }
    if (dz != 0.0) {
      addTo(impulse, Z, dz);
    }
    PENDING.setVolatile(impulse, true);
    return true;
  }

  private static void addTo(Impulse impulse, VarHandle handle, double delta) {
    long bits = (long) handle.getVolatile(impulse);
    while (true) {
      long next = Double.doubleToRawLongBits(Double.longBitsToDouble(bits) + delta);
      long witness = (long) handle.compareAndExchange(impulse, bits, next);
      if (witness == bits) {
        return;
      }
      bits = witness;
    }
  }

  public void applyTo(Entity entity) {
    if (!pending) {
      return;
    }
    PENDING.setVolatile(this, false);
    double dx = Double.longBitsToDouble((long) X.getAndSet(this, 0L));
    double dy = Double.longBitsToDouble((long) Y.getAndSet(this, 0L));
    double dz = Double.longBitsToDouble((long) Z.getAndSet(this, 0L));
    if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
      entity.push(dx, dy, dz);
    }
  }
}
