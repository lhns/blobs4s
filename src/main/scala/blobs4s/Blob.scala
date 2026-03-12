package blobs4s

import java.io.InputStream
import java.util

/** Immutable byte sequence whose backing store is transparent to callers. */
trait Blob:
  def size: Long
  def isEmpty: Boolean = size == 0
  def nonEmpty: Boolean = !isEmpty

  /** Content hash (one-deep merkle: SHA-256 of per-chunk SHA-256 digests). */
  def hash: BlobHash

  /** Single byte access. Prefer `chunks` or `toInputStream` for bulk reads. */
  def get(index: Long): Byte

  def slice(from: Long, until: Long): Blob
  def take(n: Long): Blob = slice(0, math.min(n, size))
  def drop(n: Long): Blob = slice(math.min(n, size), size)

  def ++(other: Blob): Blob =
    if this.isEmpty then other
    else if other.isEmpty then this
    else internal.ConcatBlob(this, other)

  def toArray: Array[Byte] =
    if size > Int.MaxValue then
      throw UnsupportedOperationException(s"Blob too large for array: $size bytes")
    val is = toInputStream
    try is.readAllBytes()
    finally is.close()

  def toArrayOption: Option[Array[Byte]] =
    if size > Int.MaxValue then None
    else Some(toArray)

  def toInputStream: InputStream

  /** Iterate over the blob in fixed-size byte arrays. Last chunk may be smaller. */
  def chunks(chunkSize: Int = Blob.DefaultChunkSize): Iterator[Array[Byte]] =
    val is = toInputStream
    Iterator.continually(is.readNBytes(chunkSize)).takeWhile(_.length > 0)

  override def equals(that: Any): Boolean = that match
    case other: Blob => this.hash == other.hash
    case _           => false

  override def hashCode(): Int = hash.hashCode()

object Blob:
  val DefaultChunkSize: Int = 262_144 // 256 KB

  def apply(bytes: Array[Byte])(using store: BlobStore): Blob =
    store.fromArray(bytes)

  def fromPath(path: java.nio.file.Path)(using store: BlobStore): Blob =
    store.fromPath(path)

  def fromInputStream(in: InputStream)(using store: BlobStore): Blob =
    store.fromInputStream(in)

  def empty(using store: BlobStore): Blob =
    store.fromArray(Array.emptyByteArray)

// ---------------------------------------------------------------------------

/** Content-based hash (SHA-256). Two blobs with the same bytes have the same hash. */
final class BlobHash private[blobs4s] (private[blobs4s] val bytes: Array[Byte]):
  override def equals(that: Any): Boolean = that match
    case other: BlobHash => util.Arrays.equals(bytes, other.bytes)
    case _               => false

  override def hashCode(): Int = util.Arrays.hashCode(bytes)
  def toHexString: String = bytes.map(b => f"$b%02x").mkString
  override def toString: String = s"BlobHash(${toHexString.take(16)}...)"
