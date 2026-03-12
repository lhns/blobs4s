package blobs4s.internal

import blobs4s.{Blob, BlobHash}
import java.security.MessageDigest

private[blobs4s] object Hashing:

  /** One-deep merkle hash: SHA-256( SHA-256(chunk_1) ++ SHA-256(chunk_2) ++ ... ) */
  def computeHash(blob: Blob): BlobHash =
    val outer = MessageDigest.getInstance("SHA-256")
    val iter = blob.chunks(Blob.DefaultChunkSize)
    while iter.hasNext do
      val chunk = iter.next()
      val inner = MessageDigest.getInstance("SHA-256")
      inner.update(chunk)
      outer.update(inner.digest())
    BlobHash(outer.digest())

  /** Same algorithm, but from pre-split chunks (avoids creating an InputStream). */
  def computeHashFromChunks(chunks: IndexedSeq[Array[Byte]]): BlobHash =
    val outer = MessageDigest.getInstance("SHA-256")
    for chunk <- chunks do
      val inner = MessageDigest.getInstance("SHA-256")
      inner.update(chunk)
      outer.update(inner.digest())
    BlobHash(outer.digest())
