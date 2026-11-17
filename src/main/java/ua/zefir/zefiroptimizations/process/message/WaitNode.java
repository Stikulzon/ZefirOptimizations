package ua.zefir.zefiroptimizations.process.message;

public interface WaitNode {
  WaitNode waitingOn();

  int MAX_DEPTH = 4096;

  static boolean reaches(WaitNode start, WaitNode target) {
    WaitNode node = start;
    WaitNode mark = null;
    int nextMark = 1;
    for (int depth = 0; node != null && depth < MAX_DEPTH; depth++) {
      if (node == target) {
        return true;
      }
      if (node == mark) {
        return false;
      }
      if (depth == nextMark) {
        mark = node;
        nextMark <<= 1;
      }
      node = node.waitingOn();
    }
    return false;
  }
}
