package ua.zefir.zefiroptimizations.process.sharedstate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReadCallsTest {
  private static final String OBJECTS = "java/util/Objects";
  private static final String COLLECTIONS = "java/util/Collections";

  @Test
  void nullChecksAndEqualityOnlyRead() {
    assertTrue(ReadCalls.isStaticRead(OBJECTS, "requireNonNull"));
    assertTrue(ReadCalls.isStaticRead(OBJECTS, "equals"));
  }

  @Test
  void otherStaticCallsMayWrite() {
    assertFalse(ReadCalls.isStaticRead(COLLECTIONS, "sort"));
    assertFalse(ReadCalls.isStaticRead(OBJECTS, "requireNonNullOrWrite"));
  }
}
