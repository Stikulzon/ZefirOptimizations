package ua.zefir.zefiroptimizations.data;

import java.util.Objects;
import java.util.function.Predicate;

public interface DummyPredicate<T> extends Predicate<T> {
    @Override
    default DummyPredicate<T> and(Predicate<? super T> other) {
        Objects.requireNonNull(other);
        return (t) -> test(t) && other.test(t);
    }
}
