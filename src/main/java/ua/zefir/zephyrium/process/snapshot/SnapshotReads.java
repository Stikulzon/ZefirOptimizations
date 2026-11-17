package ua.zefir.zephyrium.process.snapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ua.zefir.zephyrium.process.ownership.Ownership;
import ua.zefir.zephyrium.process.ownership.analysis.Purity;

public final class SnapshotReads {
  public static final byte UNKNOWN = 0;
  public static final byte READABLE = 1;
  public static final byte NOT_READABLE = 2;
  private static final byte DECIDING = 3;
  private static final int INITIAL_SITES = 1024;
  private static final int GROWTH = 2;

  private static final List<String> SITES = new ArrayList<>();
  private static volatile byte[] states = new byte[INITIAL_SITES];
  private static volatile Purity purity;

  public static void install(Purity analysis) {
    purity = analysis;
  }

  public static synchronized int register(String key) {
    int site = SITES.size();
    SITES.add(key);
    if (site >= states.length) {
      states = Arrays.copyOf(states, states.length * GROWTH);
    }
    return site;
  }

  public static byte state(int site) {
    byte[] current = states;
    byte state = site < current.length ? current[site] : UNKNOWN;
    return state == DECIDING ? NOT_READABLE : state;
  }

  public static boolean decide(int site) {
    String key = key(site);
    boolean readable;
    try {
      readable = purity != null && purity.isSnapshotReadable(key);
    } catch (Throwable t) {
      Ownership.LOGGER.error("Could not analyze whether {} only reads", key, t);
      readable = false;
    }
    set(site, readable ? READABLE : NOT_READABLE);
    Ownership.LOGGER.debug("{} is {}read from snapshots", key, readable ? "" : "not ");
    return readable;
  }

  public static void decideLater(int site) {
    byte[] current = states;
    if (site >= current.length || current[site] != UNKNOWN) {
      return;
    }
    current[site] = DECIDING;
    Thread.ofVirtual().name("zephyrium-read-analysis").start(() -> decide(site));
  }

  private static synchronized String key(int site) {
    return SITES.get(site);
  }

  private static void set(int site, byte state) {
    byte[] current = states;
    if (site < current.length) {
      current[site] = state;
    }
  }
}
