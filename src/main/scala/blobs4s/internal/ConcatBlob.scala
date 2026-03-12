package blobs4s.internal

import blobs4s.{Blob, BlobHash}
import java.io.{InputStream, SequenceInputStream}

/** Lazy concatenation of two blobs. */
private[blobs4s] final class ConcatBlob(
    left: Blob,
    right: Blob
) extends Blob:
  val size: Long = left.size + right.size
  lazy val hash: BlobHash = Hashing.computeHash(this)

  def get(index: Long): Byte =
    if index < 0 || index >= size then
      throw IndexOutOfBoundsException(s"Index $index out of bounds for blob of size $size")
    if index < left.size then left.get(index)
    else right.get(index - left.size)

  def slice(from: Long, until: Long): Blob =
    val f = math.max(0L, from)
    val u = math.min(size, until)
    if f >= u then EmptyBlob
    else if u <= left.size then left.slice(f, u)
    else if f >= left.size then right.slice(f - left.size, u - left.size)
    else left.slice(f, left.size) ++ right.slice(0, u - left.size)

  def toInputStream: InputStream =
    SequenceInputStream(left.toInputStream, right.toInputStream)
