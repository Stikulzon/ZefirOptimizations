package ua.zefir.zefiroptimizations.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ZefirConfigTest {
  private static final int NEGATIVE = -5;
  private static final int TOO_MANY_CARRIERS = 100_000;

  @Test
  void normalizeClampsOutOfRangeValues() {
    ZefirConfig config = new ZefirConfig();
    config.carrierThreads = TOO_MANY_CARRIERS;

    config.normalize();

    assertEquals(ZefirConfig.MAX_CARRIER_THREADS, config.carrierThreads);

    config.carrierThreads = NEGATIVE;

    config.normalize();

    assertEquals(ZefirConfig.AUTO_CARRIER_THREADS, config.carrierThreads);
  }

  @Test
  void normalizeKeepsDefaults() {
    ZefirConfig config = new ZefirConfig();
    ZefirConfig normalized = new ZefirConfig();

    normalized.normalize();

    assertEquals(config.carrierThreads, normalized.carrierThreads);
  }
}
