package blobs4s.internal

import blobs4s.{Blob, BlobHash}
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{Files, Path, StandardOpenOption}

/** Blob backed by a temp file. Single byte access seeks into the file;
  * prefer `chunks` or `toInputStream` for bulk reads.
  */
private[blobs4s] final class FileBlob(
    private[blobs4s] val path: Path,
    val size: Long,
    override val hash: BlobHash
) extends Blob:

  def get(index: Long): Byte =
    if index < 0 || index >= size then
      throw IndexOutOfBoundsException(s"Index $index out of bounds for blob of size $size")
    val ch = FileChannel.open(path, StandardOpenOption.READ)
    try
      ch.position(index)
      val buf = ByteBuffer.allocate(1)
      ch.read(buf)
      buf.flip()
      buf.get()
    finally ch.close()

  def slice(from: Long, until: Long): Blob =
    val f = math.max(0L, from)
    val u = math.min(size, until)
    if f >= u then EmptyBlob
    else if f == 0L && u == size then this
    else SlicedBlob(this, f, u - f)

  def toInputStream: InputStream = Files.newInputStream(path)
