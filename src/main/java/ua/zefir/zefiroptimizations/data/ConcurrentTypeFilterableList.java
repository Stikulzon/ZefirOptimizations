package ua.zefir.zefiroptimizations.data;

import com.google.common.collect.ImmutableList;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.Iterator;

public class ConcurrentTypeFilterableList<T> extends AbstractCollection<T> {
    // Use ConcurrentHashMap for thread-safe map operations.
    private final ConcurrentHashMap<Class<?>, AtomicReference<List<T>>> elementsByType = new ConcurrentHashMap<>();
    private final Class<T> elementType;
    // Use CopyOnWriteArrayList for thread-safe list operations.  Crucial for safe iteration.
    private final CopyOnWriteArrayList<T> allElements = new CopyOnWriteArrayList<>();

    public ConcurrentTypeFilterableList(Class<T> elementType) {
        this.elementType = elementType;
        // Initialize the base type with an AtomicReference to the allElements list.
        this.elementsByType.put(elementType, new AtomicReference<>(this.allElements));
    }

    @Override
    public boolean add(T e) {
        boolean added = false;
        // Iterate over a snapshot of the keys (Class<?>).  This avoids ConcurrentModificationException.
        for (Class<?> type : elementsByType.keySet()) {
            if (type.isInstance(e)) {
                AtomicReference<List<T>> listRef = elementsByType.get(type);
                // Use updateAndGet to atomically update the list.  Important for correctness.
                listRef.updateAndGet(list -> {
                    // Create a new list with the added element to ensure immutability of previous versions.
                    // Add to allElements too
                    if (!allElements.contains(e)) {
                        allElements.add(e);
                    }
                    ArrayList<T> newList = new ArrayList<>(list);  // Create a mutable copy
                    newList.add(e);
                    return newList;
                });
                added = true; // Set added to true if at least one list accepted the element
            }
        }


        //If element type hasn't yet been added, add it now.
        if (!added) {
            elementsByType.computeIfAbsent(e.getClass(), k -> {
                allElements.add(e);
                List<T> newList = new ArrayList<>();
                newList.add(e);
                return new AtomicReference<>(newList);
            });
            added = true;
        }

        return added;
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = false;

        // Iterate over a snapshot, similar to add().
        for (Class<?> type : elementsByType.keySet()) {
            if (type.isInstance(o)) {
                AtomicReference<List<T>> listRef = elementsByType.get(type);
                // Atomically update the list, removing the element if present.
                listRef.updateAndGet(list -> {
                    if (list.contains(o)) {
                        ArrayList<T> newList = new ArrayList<>(list); // Mutable copy
                        newList.remove(o);
                        return newList;
                    }
                    return list; // Return the original list if the element wasn't found
                });
                removed = allElements.remove(o) || removed; //also try to remove from allElements
            }
        }

        return removed;
    }

    @Override
    public boolean contains(Object o) {
        //getAllOfType is made thread-safe below.
        return this.getAllOfType(o.getClass()).contains(o);
    }


    @SuppressWarnings("unchecked")
    public <S> Collection<S> getAllOfType(Class<S> type) {
        if (!this.elementType.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Don't know how to search for " + type);
        }

        // Use computeIfAbsent with an AtomicReference.  This is the key to thread-safety.
        AtomicReference<List<T>> listRef = elementsByType.computeIfAbsent(type, k -> {
            //If the type doesn't yet exist, filter from a copy of allElements and place into listRef
            List<T> newList = allElements.stream()
                    .filter(type::isInstance)
                    .collect(Collectors.toList());
            return new AtomicReference<>(newList);
        });

        // Return an unmodifiable view of the *current* list.  Safe because of the AtomicReference.
        return Collections.unmodifiableCollection((List<S>) listRef.get());
    }


    public Iterator<T> iterator() {
        // CopyOnWriteArrayList's iterator is already thread-safe (snapshot iterator).
        return (java.util.Iterator<T>) allElements.iterator();
    }

    public List<T> copy() {
        // CopyOnWriteArrayList already provides a consistent snapshot.
        return ImmutableList.copyOf(allElements);
    }

    @Override
    public int size() {
        return allElements.size();
    }

    // Helper method to create a new ArrayList (used for updating lists atomically).  Avoids code duplication.
    private <E> ArrayList<E> copyAndAdd(List<E> original, E element) {
        ArrayList<E> newList = new ArrayList<>(original);
        newList.add(element);
        return newList;
    }
}
