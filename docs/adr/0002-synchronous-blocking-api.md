# ADR-0002: Synchronous Blocking API

## Status
Accepted

## Context
A blob library could expose `Future[Blob]`, `IO[Blob]`, or plain `Blob`. Our target audience writes scripts and services where simplicity beats throughput optimization.

## Decision
Every method on `Blob` and `BlobStore` is synchronous and returns its result directly. No `Future`, no `IO`, no callbacks.

## Consequences
- Using a blob is as easy as using `Array[Byte]`.
- Callers can wrap calls in `Future { ... }` or Ox's `fork { ... }` if they want parallelism.
- File I/O blocks the calling thread. Under Loom virtual threads this is cheap. On a classic thread pool it could be expensive — we document that JDK 21+ is expected.
