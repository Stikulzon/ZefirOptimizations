package ua.zefir.zephyrium.process.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class Players {
  public static <T> List<T> matching(List<T> all, Predicate<? super T> predicate, int limit) {
    List<T> result = new ArrayList<>();
    for (T player : all) {
      if (!predicate.test(player)) {
        continue;
      }
      result.add(player);
      if (result.size() >= limit) {
        break;
      }
    }
    return result;
  }
}
