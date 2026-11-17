package ua.zefir.zephyrium.process.message;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WaitNodeTest {
  private static final class Node implements WaitNode {
    private WaitNode next;

    @Override
    public WaitNode waitingOn() {
      return next;
    }
  }

  @Test
  void reachesTargetAlongChain() {
    Node a = new Node();
    Node b = new Node();
    Node c = new Node();
    a.next = b;
    b.next = c;

    assertTrue(WaitNode.reaches(a, c));
    assertFalse(WaitNode.reaches(c, a));
  }

  @Test
  void stopsOnCycleWithoutTarget() {
    Node a = new Node();
    Node b = new Node();
    Node c = new Node();
    Node outside = new Node();
    a.next = b;
    b.next = c;
    c.next = b;

    assertFalse(WaitNode.reaches(a, outside));
  }

  @Test
  void findsTargetInsideCycle() {
    Node a = new Node();
    Node b = new Node();
    Node c = new Node();
    Node d = new Node();
    a.next = b;
    b.next = c;
    c.next = d;
    d.next = b;

    assertTrue(WaitNode.reaches(a, d));
  }
}
