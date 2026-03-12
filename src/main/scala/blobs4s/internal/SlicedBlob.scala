package blobs4s.internal

import blobs4s.{Blob, BlobHash}
import java.io.{FilterInputStream, InputStream}

/** Zero-copy view into a region of another blob. */
private[blobs4s] final class SlicedBlob(
    underlying: Blob,
    offset: Long,
    val size: Long
) extends Blob:

  lazy val hash: BlobHash = Hashing.computeHash(this)

  def get(index: Long): Byte =
    if index < 0 || index >= size then
      throw IndexOutOfBoundsException(s"Index $index out of bounds for blob of size $size")
    underlying.get(offset + index)

  def slice(from: Long, until: Long): Blob =
    val f = math.max(0L, from)
    val u = math.min(size, until)
    if f >= u then EmptyBlob
    else if f == 0L && u == size then this
    else SlicedBlob(underlying, offset + f, u - f) // flatten — avoid nesting

  def toInputStream: InputStream =
    val is = underlying.toInputStream
    is.skipNBytes(offset)
    BoundedInputStream(is, size)

// ---------------------------------------------------------------------------

/** Wraps an InputStream to yield at most `limit` bytes. */
private[blobs4s] final class BoundedInputStream(
    in: InputStream,
    limit: Long
) extends FilterInputStream(in):
  private var remaining: Long = limit

  override def read(): Int =
    if remaining <= 0 then -1
    else
      val b = super.read()
      if b != -1 then remaining -= 1
      b

  override def read(buf: Array[Byte], off: Int, len: Int): Int =
    if remaining <= 0 then -1
    else
      val toRead = math.min(len.toLong, remaining).toInt
      val n = super.read(buf, off, toRead)
      if n > 0 then remaining -= n
      n
