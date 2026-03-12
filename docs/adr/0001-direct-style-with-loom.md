# ADR-0001: Direct Style with Loom/Ox over FS2 and Async-Await

## Status
Accepted

## Context
blobs4s aims to onboard developers onto Scala as an alternative to bash/Python scripts and workflows. Streaming libraries like FS2 are powerful but impose a steep learning curve:

- FS2 requires understanding effect types (`IO`, `Resource`, `Stream[F, Byte]`), which alienates newcomers.
- Re-reading a stream requires replaying the source or caching — neither is transparent.
- `scala.concurrent.Future` with async/await produces obscure compile errors and doesn't compose well for byte streaming.

JDK 21 virtual threads (Project Loom) enable blocking I/O without pinning OS threads. Libraries like Ox build on this to provide structured concurrency in direct style.

## Decision
blobs4s uses **synchronous, blocking APIs**. Callers who need concurrency bring their own Loom/Ox runtime. The library itself has **no dependency on Ox or any effect system**.

## Consequences
- Any Scala developer can use blobs4s without learning effect types.
- Blobs are re-readable by design (unlike streams).
- Long-running I/O blocks the calling virtual thread, which is fine under Loom but would be a problem on a traditional thread pool. We document the JDK 21 requirement.
