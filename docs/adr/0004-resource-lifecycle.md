# ADR-0004: Resource Lifecycle — BlobStore as AutoCloseable with Scoping

## Status
Accepted

## Context
`TempFileBlobStore` creates temp files that must eventually be deleted. `deleteOnExit` only fires on clean JVM shutdown and doesn't help long-running servers processing many blobs.

## Decision
- `BlobStore` extends `AutoCloseable`. Calling `close()` deletes all temp files created by that store.
- `deleteOnExit` is registered as a **safety net**, not the primary cleanup mechanism.
- `BlobStore.scoped(store) { ... }` provides try/finally convenience.
- For request-scoped cleanup in servers, create a `TempFileBlobStore` per request (or per scope) and close it when the request completes.

```scala
// Per-request scoping
BlobStore.scoped(TempFileBlobStore(10_000_000)) {
  val blob = Blob.fromInputStream(request.body)
  process(blob)
} // temp files cleaned up here
```

## Consequences
- Temp files are deterministically cleaned up, even in long-running processes.
- Creating a `TempFileBlobStore` per scope is cheap (just an object with a concurrent queue).
- `deleteOnExit` remains as a fallback for unclean shutdown.
- Future: could add nested scopes or `BlobStore.childScope()` if needed.
