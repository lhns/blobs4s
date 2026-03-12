package blobs4s.internal

import blobs4s.{Blob, BlobHash}
import java.io.InputStream

/** Blob backed by chunked in-memory arrays. O(1) slice (no copy). */
private[blobs4s] final class InMemoryBlob(
    private[blobs4s] val dataChunks: IndexedSeq[Array[Byte]],
    val size: Long,
    override val hash: BlobHash
) extends Blob:

  // Cumulative byte offset of each chunk, for binary search in get()
  private val cumulativeOffsets: Array[Long] =
    val arr = new Array[Long](dataChunks.size)
    var offset = 0L
    for i <- dataChunks.indices do
      arr(i) = offset
      offset += dataChunks(i).length
    arr

  def get(index: Long): Byte =
    if index < 0 || index >= size then
      throw IndexOutOfBoundsException(s"Index $index out of bounds for blob of size $size")
    val ci = findChunkIndex(index)
    val localOffset = (index - cumulativeOffsets(ci)).toInt
    dataChunks(ci)(localOffset)

  def slice(from: Long, until: Long): Blob =
    val f = math.max(0L, from)
    val u = math.min(size, until)
    if f >= u then EmptyBlob
    else if f == 0L && u == size then this
    else SlicedBlob(this, f, u - f)

  def toInputStream: InputStream = new InputStream:
    private var chunkIdx = 0
    private var posInChunk = 0

    override def read(): Int =
      if chunkIdx >= dataChunks.size then -1
      else
        val b = dataChunks(chunkIdx)(posInChunk) & 0xff
        posInChunk += 1
        if posInChunk >= dataChunks(chunkIdx).length then
          chunkIdx += 1
          posInChunk = 0
        b

    override def read(buf: Array[Byte], off: Int, len: Int): Int =
      if chunkIdx >= dataChunks.size then -1
      else
        var written = 0
        var remaining = len
        while remaining > 0 && chunkIdx < dataChunks.size do
          val chunk = dataChunks(chunkIdx)
          val available = chunk.length - posInChunk
          val toCopy = math.min(remaining, available)
          System.arraycopy(chunk, posInChunk, buf, off + written, toCopy)
          written += toCopy
          remaining -= toCopy
          posInChunk += toCopy
          if posInChunk >= chunk.length then
            chunkIdx += 1
            posInChunk = 0
        if written == 0 then -1 else written

  private def findChunkIndex(globalOffset: Long): Int =
    var lo = 0
    var hi = dataChunks.size - 1
    while lo < hi do
      val mid = (lo + hi + 1) / 2
      if cumulativeOffsets(mid) <= globalOffset then lo = mid
      else hi = mid - 1
    lo
