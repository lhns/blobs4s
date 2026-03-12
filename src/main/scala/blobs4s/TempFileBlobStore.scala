package blobs4s

import java.io.InputStream
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue

/** BlobStore that keeps small blobs in memory and spills large ones to temp files.
  *
  * All temp files are deleted when `close()` is called. Also registered with
  * `deleteOnExit` as a safety net.
  *
  * @param threshold blobs up to this size (bytes) stay in memory
  * @param chunkSize internal chunk size used for hashing and in-memory storage
  */
class TempFileBlobStore(
    val threshold: Long,
    val chunkSize: Int = Blob.DefaultChunkSize
) extends BlobStore:

  private val trackedFiles = ConcurrentLinkedQueue[Path]()

  def fromArray(bytes: Array[Byte]): Blob =
    if bytes.isEmpty then internal.EmptyBlob
    else if bytes.length.toLong <= threshold then
      InMemoryBlobStore.fromArray(bytes)
    else
      writeToTempFile(bytes)

  def fromPath(path: Path): Blob =
    val fileSize = Files.size(path)
    if fileSize == 0L then internal.EmptyBlob
    else if fileSize <= threshold then
      InMemoryBlobStore.fromArray(Files.readAllBytes(path))
    else
      val tmp = createTempFile()
      Files.copy(path, tmp, StandardCopyOption.REPLACE_EXISTING)
      val hash = computeFileHash(tmp)
      internal.FileBlob(tmp, fileSize, hash)

  def fromInputStream(in: InputStream): Blob =
    try
      // Phase 1: buffer chunks in memory up to threshold
      val memChunks = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
      val buf = new Array[Byte](chunkSize)
      var totalSize = 0L

      var n = in.readNBytes(buf, 0, chunkSize)
      while n > 0 && totalSize + n <= threshold do
        memChunks += java.util.Arrays.copyOf(buf, n)
        totalSize += n
        n = in.readNBytes(buf, 0, chunkSize)

      if n <= 0 then
        // Everything fit in memory
        if totalSize == 0L then internal.EmptyBlob
        else
          val hash = internal.Hashing.computeHashFromChunks(memChunks.toIndexedSeq)
          internal.InMemoryBlob(memChunks.toIndexedSeq, totalSize, hash)
      else
        // Exceeded threshold — spill everything to a temp file
        val tmp = createTempFile()
        val out = Files.newOutputStream(tmp)
        try
          for c <- memChunks do out.write(c)
          out.write(buf, 0, n)
          in.transferTo(out)
        finally out.close()

        val fileSize = Files.size(tmp)
        val hash = computeFileHash(tmp)
        internal.FileBlob(tmp, fileSize, hash)
    finally in.close()

  def close(): Unit =
    var path = trackedFiles.poll()
    while path != null do
      try Files.deleteIfExists(path)
      catch case _: Exception => ()
      path = trackedFiles.poll()

  // -- private helpers -------------------------------------------------------

  private def createTempFile(): Path =
    val tmp = Files.createTempFile("blobs4s-", ".blob")
    tmp.toFile.deleteOnExit()
    trackedFiles.add(tmp)
    tmp

  private def writeToTempFile(bytes: Array[Byte]): Blob =
    val tmp = createTempFile()
    Files.write(tmp, bytes)
    val chunks = bytes.grouped(chunkSize).toIndexedSeq
    val hash = internal.Hashing.computeHashFromChunks(chunks)
    internal.FileBlob(tmp, bytes.length.toLong, hash)

  private def computeFileHash(path: Path): BlobHash =
    val is = Files.newInputStream(path)
    try
      val outer = MessageDigest.getInstance("SHA-256")
      val buf = new Array[Byte](chunkSize)
      var n = is.readNBytes(buf, 0, chunkSize)
      while n > 0 do
        val inner = MessageDigest.getInstance("SHA-256")
        inner.update(buf, 0, n)
        outer.update(inner.digest())
        n = is.readNBytes(buf, 0, chunkSize)
      BlobHash(outer.digest())
    finally is.close()
