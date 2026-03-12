# ADR-0005: Chunked Storage and One-Deep Merkle Hashing

## Status
Accepted

## Context
Blobs can be arbitrarily large (multi-GB). We need:
1. Bounded memory during ingest — can't allocate a single contiguous array for a 4 GB file.
2. Content-based equality — two blobs with the same bytes must be `==`.
3. Efficient hashing — should not require loading the entire blob into RAM.

## Decision
- **In-memory blobs** store data as `IndexedSeq[Array[Byte]]` (chunked), not a single array. Default chunk size is 256 KB. This avoids huge single allocations and GC pressure.
- **File-backed blobs** store data as a single temp file (no need to split across files — the OS handles paging). Chunk boundaries are logical, used only for hashing.
- **Hashing** uses a one-deep merkle tree: each 256 KB chunk is SHA-256'd individually, then the root hash is `SHA-256(chunk_hash_1 ‖ chunk_hash_2 ‖ …)`.
- For blobs created from raw data (fromArray, fromPath, fromInputStream), chunk hashes are computed during ingest and the root hash is cached eagerly.
- For derived blobs (slice, concat), the root hash is computed lazily via streaming — chunk boundaries of the derived blob may not align with the original.
- `equals` compares root hashes. SHA-256 collision probability is negligible.

## Consequences
- Memory usage during ingest is bounded by chunk size, not blob size.
- Hash computation is streaming — works for arbitrarily large blobs.
- Two blobs with identical content always compare as equal, regardless of backing store.
- Derived blobs (slice/concat) pay the hashing cost only when `hash` or `equals` is first accessed.
