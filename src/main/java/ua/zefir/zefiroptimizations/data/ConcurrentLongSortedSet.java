package ua.zefir.zefiroptimizations.data;

import it.unimi.dsi.fastutil.longs.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;

public class ConcurrentLongSortedSet extends AbstractLongSortedSet {

    private final ConcurrentSkipListSet<Long> set;

    public ConcurrentLongSortedSet() {
        this(LongComparators.NATURAL_COMPARATOR);
    }

    public ConcurrentLongSortedSet(LongComparator comparator) {
        this.set = new ConcurrentSkipListSet<>(Comparator.comparingLong(l -> l));
    }

    public ConcurrentLongSortedSet(Collection<? extends Long> c) {
        this();
        addAll(c);
    }

    @Override
    public @NotNull LongBidirectionalIterator iterator() {
        return new IteratorWrapper(set.iterator());
    }

    @Override
    public LongBidirectionalIterator iterator(long fromElement) {
        return new IteratorWrapper(set.tailSet(fromElement).iterator());
    }

    @Override
    public LongComparator comparator() {
        return LongComparators.NATURAL_COMPARATOR;
    }

    @Override
    public LongSortedSet subSet(long fromElement, long toElement) {
        return new ConcurrentLongSortedSet(set.subSet(fromElement, toElement));
    }

    @Override
    public LongSortedSet headSet(long toElement) {
        return new ConcurrentLongSortedSet(set.headSet(toElement));
    }

    @Override
    public LongSortedSet tailSet(long fromElement) {
        return new ConcurrentLongSortedSet(set.tailSet(fromElement));
    }

    @Override
    public long firstLong() {
        return set.first();
    }

    @Override
    public long lastLong() {
        return set.last();
    }

    @Override
    public boolean add(long e) {
        return set.add(e);
    }

    @Override
    public boolean rem(long e) {
        return set.remove(e);
    }

    @Override
    public boolean contains(long e) {
        return set.contains(e);
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public void clear() {
        set.clear();
    }

    private static class IteratorWrapper implements LongListIterator {
        private final java.util.Iterator<Long> iterator;

        public IteratorWrapper(java.util.Iterator<Long> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public boolean hasPrevious() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long nextLong() {
            return iterator.next();
        }

        @Override
        public long previousLong() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int previousIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove() {
            iterator.remove();
        }

    }
}
