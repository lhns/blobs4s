# ADR-0003: BlobStore Injected via Scala 3 `given`

## Status
Accepted

## Context
Creating a blob requires deciding where bytes live (memory, temp file, S3, …). This decision should be made once at the application boundary, not at every call site.

## Decision
`BlobStore` is a trait passed as a Scala 3 `given` (context parameter). Blob factory methods like `Blob(bytes)` and `Blob.fromPath(path)` require `(using BlobStore)`.

```scala
given BlobStore = TempFileBlobStore(threshold = 10_000_000)
val blob = Blob(myBytes) // BlobStore resolved implicitly
```

## Consequences
- Call sites are clean — no explicit store parameter.
- Swapping backends (e.g., in-memory for tests, temp-file for prod) is a single-line change.
- Newcomers unfamiliar with `given`/`using` may be confused; we mitigate with clear examples in the README.
