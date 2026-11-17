package ua.zefir.zefiroptimizations.process;

public interface ProcessEntity {
  Thread zo$owner();

  void zo$setOwner(Thread owner);

  Object zo$process();

  void zo$setProcess(Object process);

  Object zo$snapshot();

  void zo$setSnapshot(Object snapshot);

  Object zo$live();

  void zo$setLive(Object live);

  int zo$readGate();

  void zo$addReadGate(int delta);

  Object zo$cloneRaw();

  void zo$setDataTracker(Object dataTracker);
}
