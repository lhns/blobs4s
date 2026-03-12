package blobs4s

import java.io.InputStream
import java.nio.file.{Files, Path}

/** BlobStore that keeps everything in memory. No resource tracking needed. */
object InMemoryBlobStore extends BlobStore:

  given BlobStore = this

  def fromArray(bytes: Array[Byte]): Blob =
    if bytes.isEmpty then internal.EmptyBlob
    else
      val chunkSize = Blob.DefaultChunkSize
      val chunks = bytes.grouped(chunkSize).toIndexedSeq
      val hash = internal.Hashing.computeHashFromChunks(chunks)
      internal.InMemoryBlob(chunks, bytes.length.toLong, hash)

  def fromPath(path: Path): Blob =
    fromArray(Files.readAllBytes(path))

  def fromInputStream(in: InputStream): Blob =
    try fromArray(in.readAllBytes())
    finally in.close()

  def close(): Unit = () // no-op
