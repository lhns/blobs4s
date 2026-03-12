package blobs4s

import java.nio.file.{Files, Path}

class BlobSuite extends munit.FunSuite:

  // -- helpers ----------------------------------------------------------------

  private def bytes(s: String): Array[Byte] = s.getBytes("UTF-8")
  private def bytes(n: Int): Array[Byte] = Array.tabulate(n)(i => (i % 256).toByte)

  // -- InMemoryBlobStore ------------------------------------------------------
  println("a")
  test("InMemoryBlobStore: empty blob") {
    println("b")
    given BlobStore = InMemoryBlobStore
    println("c")
    val b = Blob(Array.emptyByteArray)
    println("d")
    assertEquals(b.size, 0L)
    assert(b.isEmpty)
    assertEquals(b.toArray.length, 0)
    assertEquals(b.toArrayOption, Some(Array.emptyByteArray))
    println("e")
  }

  test("InMemoryBlobStore: single byte") {
    println("f")
    given BlobStore = InMemoryBlobStore
    val b = Blob(Array[Byte](42))
    assertEquals(b.size, 1L)
    assertEquals(b.get(0), 42.toByte)
    assertEquals(b.toArray.toSeq, Seq(42.toByte))
  }

  test("InMemoryBlobStore: round-trip through toArray") {
    println("g")
    given BlobStore = InMemoryBlobStore
    println("g1")
    val data = bytes("hello world")
    println("g2")
    val b = Blob(data)
    println("g3")
    assertEquals(b.toArray.toSeq, data.toSeq)
    assertEquals(b.size, data.length.toLong)
  }

  test("InMemoryBlobStore: round-trip through toInputStream") {
    println("h")
    given BlobStore = InMemoryBlobStore
    println("h1")
    val data = bytes(100_000) // spans multiple internal chunks at small chunk sizes
    println("h2")
    val b = Blob(data)
    println("h3")
    val is = b.toInputStream
    println("h4")
    val result = is.readAllBytes()
    println("h5")
    is.close()
    println("h6")
    assertEquals(result.toSeq, data.toSeq)
    println("h7")
  }

  test("InMemoryBlobStore: chunks iterator") {
    println("i")
    given BlobStore = InMemoryBlobStore
    val data = bytes(1000)
    val b = Blob(data)
    val reassembled = b.chunks(256).flatten.toArray
    assertEquals(reassembled.toSeq, data.toSeq)
  }

  test("InMemoryBlobStore: slice") {
    println("j")
    given BlobStore = InMemoryBlobStore
    val data = bytes("hello world")
    val b = Blob(data)
    val s = b.slice(6, 11)
    assertEquals(s.size, 5L)
    assertEquals(String(s.toArray, "UTF-8"), "world")
  }

  test("InMemoryBlobStore: take and drop") {
    println("k")
    given BlobStore = InMemoryBlobStore
    val data = bytes("abcdef")
    val b = Blob(data)
    assertEquals(String(b.take(3).toArray, "UTF-8"), "abc")
    assertEquals(String(b.drop(3).toArray, "UTF-8"), "def")
  }

  test("InMemoryBlobStore: slice past end is clamped") {
    given BlobStore = InMemoryBlobStore
    val b = Blob(bytes("abc"))
    val s = b.slice(1, 100)
    assertEquals(s.size, 2L)
    assertEquals(String(s.toArray, "UTF-8"), "bc")
  }

  test("InMemoryBlobStore: empty slice") {
    given BlobStore = InMemoryBlobStore
    val b = Blob(bytes("abc"))
    assert(b.slice(2, 2).isEmpty)
    assert(b.slice(5, 10).isEmpty)
  }

  test("InMemoryBlobStore: concat") {
    given BlobStore = InMemoryBlobStore
    val a = Blob(bytes("hello "))
    val b = Blob(bytes("world"))
    val c = a ++ b
    assertEquals(c.size, 11L)
    assertEquals(String(c.toArray, "UTF-8"), "hello world")
  }

  test("InMemoryBlobStore: concat with empty") {
    given BlobStore = InMemoryBlobStore
    val a = Blob(bytes("test"))
    val empty = Blob.empty
    assert((a ++ empty) eq a)
    assert((empty ++ a) eq a)
  }

  // -- hash / equality --------------------------------------------------------

  test("hash: same content produces same hash") {
    given BlobStore = InMemoryBlobStore
    val a = Blob(bytes("test data"))
    val b = Blob(bytes("test data"))
    assertEquals(a.hash, b.hash)
    assertEquals(a, b)
  }

  test("hash: different content produces different hash") {
    given BlobStore = InMemoryBlobStore
    val a = Blob(bytes("aaa"))
    val b = Blob(bytes("bbb"))
    assertNotEquals(a.hash, b.hash)
    assertNotEquals(a, b)
  }

  test("hash: slice has correct content-based hash") {
    given BlobStore = InMemoryBlobStore
    val full = Blob(bytes("hello"))
    val sliced = Blob(bytes("abchello")).drop(3)
    // both represent "hello" — same hash
    assertEquals(full.hash, sliced.hash)
  }

  test("hash: concat has correct content-based hash") {
    given BlobStore = InMemoryBlobStore
    val whole = Blob(bytes("helloworld"))
    val concatenated = Blob(bytes("hello")) ++ Blob(bytes("world"))
    assertEquals(whole.hash, concatenated.hash)
  }

  test("hashCode consistent with equals") {
    given BlobStore = InMemoryBlobStore
    val a = Blob(bytes("x"))
    val b = Blob(bytes("x"))
    assertEquals(a.hashCode(), b.hashCode())
  }

  // -- fromPath ---------------------------------------------------------------

  test("InMemoryBlobStore: fromPath") {
    given BlobStore = InMemoryBlobStore
    val tmp = Files.createTempFile("blob-test-", ".dat")
    try
      val data = bytes("file content")
      Files.write(tmp, data)
      val b = Blob.fromPath(tmp)
      assertEquals(b.toArray.toSeq, data.toSeq)
    finally Files.deleteIfExists(tmp)
  }

  // -- TempFileBlobStore ------------------------------------------------------

  test("TempFileBlobStore: small blob stays in memory") {
    val store = TempFileBlobStore(threshold = 1024)
    try
      given BlobStore = store
      val b = Blob(bytes(100))
      assert(
        b.isInstanceOf[internal.InMemoryBlob] || b == internal.EmptyBlob,
        s"Expected InMemoryBlob, got ${b.getClass.getSimpleName}"
      )
    finally store.close()
  }

  test("TempFileBlobStore: large blob goes to file") {
    val store = TempFileBlobStore(threshold = 64)
    try
      given BlobStore = store
      val data = bytes(256)
      val b = Blob(data)
      assert(
        b.isInstanceOf[internal.FileBlob],
        s"Expected FileBlob, got ${b.getClass.getSimpleName}"
      )
      assertEquals(b.toArray.toSeq, data.toSeq)
    finally store.close()
  }

  test("TempFileBlobStore: fromInputStream spills to file") {
    val store = TempFileBlobStore(threshold = 64)
    try
      given BlobStore = store
      val data = bytes(256)
      val b = Blob.fromInputStream(java.io.ByteArrayInputStream(data))
      assert(b.isInstanceOf[internal.FileBlob])
      assertEquals(b.toArray.toSeq, data.toSeq)
    finally store.close()
  }

  test("TempFileBlobStore: fromInputStream small stays in memory") {
    val store = TempFileBlobStore(threshold = 1024)
    try
      given BlobStore = store
      val data = bytes(100)
      val b = Blob.fromInputStream(java.io.ByteArrayInputStream(data))
      assert(
        b.isInstanceOf[internal.InMemoryBlob] || b == internal.EmptyBlob,
        s"Expected InMemoryBlob, got ${b.getClass.getSimpleName}"
      )
      assertEquals(b.toArray.toSeq, data.toSeq)
    finally store.close()
  }

  test("TempFileBlobStore: close deletes temp files") {
    val store = TempFileBlobStore(threshold = 64)
    val data = bytes(256)
    given BlobStore = store
    val b = Blob(data).asInstanceOf[internal.FileBlob]
    val filePath = b.path
    assert(Files.exists(filePath))
    store.close()
    assert(!Files.exists(filePath))
  }

  test("TempFileBlobStore: fromPath large file") {
    val store = TempFileBlobStore(threshold = 64)
    try
      given BlobStore = store
      val tmp = Files.createTempFile("blob-test-", ".dat")
      try
        val data = bytes(256)
        Files.write(tmp, data)
        val b = Blob.fromPath(tmp)
        assert(b.isInstanceOf[internal.FileBlob])
        assertEquals(b.toArray.toSeq, data.toSeq)
      finally Files.deleteIfExists(tmp)
    finally store.close()
  }

  // -- cross-store operations -------------------------------------------------

  test("cross-store: concat InMemory ++ FileBacked") {
    val store = TempFileBlobStore(threshold = 64)
    try
      given BlobStore = store
      val small = Blob(bytes("hello "))
      val big = Blob(bytes(256))
      val concatenated = small ++ big
      val expected = bytes("hello ") ++ bytes(256)
      assertEquals(concatenated.size, expected.length.toLong)
      assertEquals(concatenated.toArray.toSeq, expected.toSeq)
    finally store.close()
  }

  test("cross-store: hash matches regardless of backing store") {
    val data = bytes(100)
    val inMem = InMemoryBlobStore.fromArray(data)
    val store = TempFileBlobStore(threshold = 10)
    try
      val onDisk = store.fromArray(data)
      assertEquals(inMem.hash, onDisk.hash)
    finally store.close()
  }

  // -- scoped -----------------------------------------------------------------

  test("BlobStore.scoped closes store") {
    var closed = false
    val store = new BlobStore:
      def fromArray(bytes: Array[Byte]): Blob = internal.EmptyBlob
      def fromPath(path: Path): Blob = internal.EmptyBlob
      def fromInputStream(in: java.io.InputStream): Blob = internal.EmptyBlob
      def close(): Unit = closed = true

    BlobStore.scoped(store) {
      val b = Blob(Array.emptyByteArray)
      assert(!closed)
    }
    assert(closed)
  }

  // -- edge cases: slice spanning ConcatBlob boundary -------------------------

  test("slice spanning ConcatBlob boundary") {
    given BlobStore = InMemoryBlobStore
    val left = Blob(bytes("abcde"))
    val right = Blob(bytes("fghij"))
    val c = left ++ right
    val s = c.slice(3, 7) // "defg"
    assertEquals(String(s.toArray, "UTF-8"), "defg")
  }

  test("get on ConcatBlob") {
    given BlobStore = InMemoryBlobStore
    val c = Blob(bytes("abc")) ++ Blob(bytes("def"))
    assertEquals(c.get(0).toChar, 'a')
    assertEquals(c.get(2).toChar, 'c')
    assertEquals(c.get(3).toChar, 'd')
    assertEquals(c.get(5).toChar, 'f')
  }

  test("toInputStream on SlicedBlob") {
    given BlobStore = InMemoryBlobStore
    val b = Blob(bytes("hello world"))
    val s = b.slice(6, 11)
    val is = s.toInputStream
    val result = is.readAllBytes()
    is.close()
    assertEquals(String(result, "UTF-8"), "world")
  }

  test("large blob: multi-chunk round-trip") {
    given BlobStore = InMemoryBlobStore
    // Create blob larger than DefaultChunkSize to test multi-chunk storage
    val data = bytes(Blob.DefaultChunkSize * 3 + 100)
    val b = Blob(data)
    assertEquals(b.size, data.length.toLong)
    assertEquals(b.toArray.toSeq, data.toSeq)
    // Verify chunks iterator reassembles correctly
    val reassembled = b.chunks().flatten.toArray
    assertEquals(reassembled.toSeq, data.toSeq)
  }
