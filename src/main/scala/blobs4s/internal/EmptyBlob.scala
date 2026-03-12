package blobs4s.internal

import blobs4s.{Blob, BlobHash}
import java.io.{ByteArrayInputStream, InputStream}

private[blobs4s] object EmptyBlob extends Blob:
  val size: Long = 0L
  val hash: BlobHash = Hashing.computeHashFromChunks(IndexedSeq.empty)

  def get(index: Long): Byte =
    throw IndexOutOfBoundsException(s"Empty blob has no bytes")

  def slice(from: Long, until: Long): Blob = this

  override def ++(other: Blob): Blob = other

  def toInputStream: InputStream = ByteArrayInputStream(Array.emptyByteArray)

  override def chunks(chunkSize: Int): Iterator[Array[Byte]] = Iterator.empty
