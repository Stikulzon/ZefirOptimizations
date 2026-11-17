package ua.zefir.zefiroptimizations.process.profile;

import java.util.Locale;

final class Units {
  static final String NOT_MEASURED = "-";
  private static final double NANOS_PER_MILLI = 1e6;
  private static final double NANOS_PER_MICRO = 1e3;
  private static final double PERCENT = 100.0;
  private static final double KIBI = 1024.0;
  private static final String[] BYTE_UNITS = {"B", "KB", "MB", "GB"};

  private Units() {}

  static String millis(long nanos) {
    return nanos < 0 ? NOT_MEASURED : format("%.2f ms", nanos / NANOS_PER_MILLI);
  }

  static String micros(long nanos) {
    return nanos < 0 ? NOT_MEASURED : format("%.0f us", nanos / NANOS_PER_MICRO);
  }

  static String microsNumber(long nanos) {
    return nanos < 0 ? NOT_MEASURED : format("%.0f", nanos / NANOS_PER_MICRO);
  }

  static String millisNumber(long nanos) {
    return nanos < 0 ? NOT_MEASURED : format("%.2f", nanos / NANOS_PER_MILLI);
  }

  static String percent(double share) {
    return format("%.1f%%", share * PERCENT);
  }

  static String cores(long nanos, long wallNanos) {
    return nanos < 0 || wallNanos <= 0 ? NOT_MEASURED : format("%.2f", (double) nanos / wallNanos);
  }

  static String ratio(long count, long per) {
    return per <= 0 ? NOT_MEASURED : format("%.2f", (double) count / per);
  }

  static String bytes(long bytes) {
    if (bytes < 0) {
      return NOT_MEASURED;
    }
    double value = bytes;
    int unit = 0;
    while (value >= KIBI && unit < BYTE_UNITS.length - 1) {
      value /= KIBI;
      unit++;
    }
    return unit == 0 ? bytes + " B" : format("%.1f %s", value, BYTE_UNITS[unit]);
  }

  static String count(long count) {
    return format("%,d", count);
  }

  static String format(String pattern, Object... values) {
    return String.format(Locale.ROOT, pattern, values);
  }
}
