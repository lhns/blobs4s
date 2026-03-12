package blobs4s

import java.io.InputStream
import java.nio.file.Path

/** Factory for blobs. Implementations decide where bytes are stored.
  *
  * Pass as a `given` so callers never see the storage strategy.
  */
trait BlobStore extends AutoCloseable:
  def fromArray(bytes: Array[Byte]): Blob
  def fromPath(path: Path): Blob
  def fromInputStream(in: InputStream): Blob

object BlobStore:
  /** Run `f` with `store` as the given BlobStore, closing the store afterwards. */
  def scoped[A](store: BlobStore)(f: BlobStore ?=> A): A =
    try f(using store)
    finally store.close()
