# ADR-0006: Copy Files Into Store, Don't Hardlink

## Status
Accepted

## Context
When `TempFileBlobStore.fromPath(path)` ingests a large file, it could either:
1. **Hardlink** — free, but if the caller mutates the original file, the "immutable" blob is silently corrupted.
2. **Copy** — costs disk I/O, but the blob is truly independent of the source.

## Decision
Always **copy** the file into a new temp file. Do not hardlink.

## Consequences
- Blobs are genuinely immutable. No action by the caller can corrupt a blob after creation.
- Ingest of large files has a one-time copy cost. Under Loom this blocks a virtual thread cheaply.
- Cross-device ingest works without fallback logic (hardlinks fail across mount points).
- Future: could offer an opt-in `unsafeHardlink` mode for callers who control the source file lifecycle.
