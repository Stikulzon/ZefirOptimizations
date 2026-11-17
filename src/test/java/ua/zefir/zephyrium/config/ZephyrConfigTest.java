package ua.zefir.zephyrium.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ZephyrConfigTest {
  private static final int NEGATIVE = -5;
  private static final int TOO_MANY_CARRIERS = 100_000;

  @Test
  void normalizeClampsOutOfRangeValues() {
    ZephyrConfig config = new ZephyrConfig();
    config.carrierThreads = TOO_MANY_CARRIERS;

    config.normalize();

    assertEquals(ZephyrConfig.MAX_CARRIER_THREADS, config.carrierThreads);

    config.carrierThreads = NEGATIVE;

    config.normalize();

    assertEquals(ZephyrConfig.AUTO_CARRIER_THREADS, config.carrierThreads);
  }

  @Test
  void normalizeKeepsDefaults() {
    ZephyrConfig config = new ZephyrConfig();
    ZephyrConfig normalized = new ZephyrConfig();

    normalized.normalize();

    assertEquals(config.carrierThreads, normalized.carrierThreads);
  }
}
