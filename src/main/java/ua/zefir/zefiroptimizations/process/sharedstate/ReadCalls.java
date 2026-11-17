package ua.zefir.zefiroptimizations.process.sharedstate;

import java.util.Set;

final class ReadCalls {
  private static final Set<String> READ_PREFIXES =
      Set.of("get", "contains", "is", "has", "peek", "first", "last", "indexOf", "lastIndexOf");
  private static final Set<String> READ_NAMES =
      Set.of(
          "size",
          "keySet",
          "values",
          "entrySet",
          "iterator",
          "listIterator",
          "spliterator",
          "stream",
          "toArray",
          "equals",
          "hashCode",
          "toString",
          "forEach",
          "subList",
          "headMap",
          "tailMap",
          "subMap",
          "headSet",
          "tailSet",
          "comparator",
          "length",
          "charAt",
          "previous",
          "nextIndex",
          "previousIndex",
          "clone");
  private static final String OBJECTS = "java/util/Objects";
  private static final Set<String> OBJECTS_READS =
      Set.of(
          "requireNonNull",
          "requireNonNullElse",
          "requireNonNullElseGet",
          "equals",
          "deepEquals",
          "hash",
          "hashCode",
          "isNull",
          "nonNull",
          "toString",
          "toIdentityString",
          "checkIndex",
          "checkFromToIndex",
          "checkFromIndexSize");
  private static final String COPY = "clone";
  private static final String CONSTRUCTOR = "<init>";
  private static final String[] COLLECTION_PREFIXES = {
    "java/util/", "it/unimi/dsi/fastutil/", "com/google/common/collect/"
  };
  private static final String MUTATING_GET_PREFIX = "getAnd";
  private static final String ITERATOR = "Iterator";
  private static final String NEXT = "next";
  private static final String JDK_RANDOM = "java/util/Random";
  private static final char SEPARATOR = '/';

  private final String randomType;

  ReadCalls(String randomType) {
    this.randomType = randomType;
  }

  static boolean copies(String name) {
    return name.equals(COPY);
  }

  static boolean isStaticRead(String owner, String name) {
    return owner.equals(OBJECTS) && OBJECTS_READS.contains(name);
  }

  static boolean isCollectionCopy(String owner, String name) {
    if (!name.equals(CONSTRUCTOR)) {
      return false;
    }
    for (String prefix : COLLECTION_PREFIXES) {
      if (owner.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  boolean isRead(String owner, String name) {
    if (owner.equals(randomType) || owner.equals(JDK_RANDOM)) {
      return false;
    }
    if (READ_NAMES.contains(name)) {
      return true;
    }
    if (name.startsWith(NEXT)) {
      return owner.substring(owner.lastIndexOf(SEPARATOR) + 1).contains(ITERATOR);
    }
    if (name.startsWith(MUTATING_GET_PREFIX)) {
      return false;
    }
    for (String prefix : READ_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
