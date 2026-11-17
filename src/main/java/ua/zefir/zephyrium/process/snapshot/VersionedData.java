package ua.zefir.zephyrium.process.snapshot;

public interface VersionedData {
  int zo$version();

  int zo$copiedVersion();

  void zo$setCopiedVersion(int version);
}
