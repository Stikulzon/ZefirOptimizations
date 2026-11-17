package ua.zefir.zefiroptimizations.process.sharedstate;

public final class SharedStateError extends Error {
  SharedStateError(String site) {
    super(site, null, false, false);
  }
}
