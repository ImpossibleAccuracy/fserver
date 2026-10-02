package com.fserver.core.crypto.format

import com.fserver.common.exception.FileSystemException
import com.fserver.core.crypto.impl.AesGcmCipher
import com.fserver.core.crypto.internal.SealedFiles
import com.fserver.core.crypto.spi.StorageCipher
import com.fserver.core.support.FakeStorageKeysStore
import com.fserver.files.fs.FsReader
import com.fserver.files.fs.FsWriter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.SecretKey
import kotlin.random.Random

class SealedFileTest {
    private val keys = FakeStorageKeysStore()
    private val files = SealedFiles(emptyList(), keys)

    @Test
    fun `plaintext survives a round trip at every size around a segment edge`() = runTest {
        for (size in listOf(0, 1, Segment - 1, Segment, Segment + 1, 3 * Segment + 5)) {
            val content = Random(size).nextBytes(size)
            val raw = MemoryFile()
            val file = newFile(raw)
            file.writer(raw, raw).use { it.write(0, content) }

            val reopened = files.open(raw)
            assertArrayEquals("size $size", content, reopened.reader(raw).readAll())
            assertEquals(file.layout.rawSize(size.toLong()), raw.bytes.size.toLong())
        }
    }

    @Test
    fun `random writes and truncates match a plain file`() = runTest {
        val random = Random(42)
        val raw = MemoryFile()
        val file = newFile(raw)
        var expected = ByteArray(0)

        val writer = file.writer(raw, raw)
        repeat(300) {
            if (random.nextInt(8) == 0) {
                val size = random.nextInt(expected.size + 1)
                writer.truncate(size.toLong())
                expected = expected.copyOf(size)
            } else {
                val offset = random.nextInt(expected.size + 2 * Segment)
                val chunk = random.nextBytes(random.nextInt(1, 3 * Segment))
                writer.write(offset.toLong(), chunk)
                if (offset + chunk.size > expected.size) expected = expected.copyOf(offset + chunk.size)
                chunk.copyInto(expected, offset)
            }

            val reader = files.open(raw).reader(raw)
            assertEquals(expected.size.toLong(), reader.size())
            assertArrayEquals(expected, reader.readAll())
        }
    }

    @Test
    fun `reads at any offset return the bytes there`() = runTest {
        val content = Random(7).nextBytes(5 * Segment + 17)
        val raw = MemoryFile()
        newFile(raw).writer(raw, raw).use { it.write(0, content) }
        val reader = files.open(raw).reader(raw)

        for (offset in listOf(0, 1, Segment - 3, Segment, 2 * Segment + 100, content.size - 1)) {
            val buffer = ByteArray(Segment + 10)
            val read = reader.read(offset.toLong(), buffer)
            val expected = minOf(buffer.size, content.size - offset)
            assertEquals(expected, read)
            assertArrayEquals(content.copyOfRange(offset, offset + expected), buffer.copyOf(read))
        }
        assertEquals(-1, reader.read(content.size.toLong(), ByteArray(4)))
    }

    @Test
    fun `rewriting a segment in place never reuses its nonce`() = runTest {
        val raw = MemoryFile()
        val file = newFile(raw)
        val writer = file.writer(raw, raw)
        val offset = file.layout.segmentOffset(0).toInt()

        writer.write(0, ByteArray(100))
        val first = raw.bytes.copyOfRange(offset, offset + Nonce)
        writer.write(0, ByteArray(100))
        val second = raw.bytes.copyOfRange(offset, offset + Nonce)

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `a flipped byte anywhere is refused`() = runTest {
        val raw = sealed(Random(1).nextBytes(2 * Segment + 10))

        for (position in listOf(0, 10, raw.bytes.size / 2, raw.bytes.size - 1)) {
            val damaged = MemoryFile(raw.bytes.copyOf().also { it[position] = (it[position] + 1).toByte() })
            assertFails<FileSystemException>("byte $position") {
                files.open(damaged).reader(damaged).readAll()
            }
        }
    }

    @Test
    fun `a file cut at a segment edge is refused`() = runTest {
        val raw = sealed(Random(2).nextBytes(3 * Segment))
        val layout = files.open(raw).layout
        val cut = MemoryFile(raw.bytes.copyOf(layout.segmentOffset(2).toInt()))

        assertFails<FileSystemException.Corrupted> {
            files.open(cut).reader(cut).readAll()
        }
    }

    @Test
    fun `segments cannot be swapped within a file or moved between files`() = runTest {
        val content = Random(3).nextBytes(3 * Segment)
        val a = sealed(content)
        val b = sealed(content)
        val layout = files.open(a).layout
        val first = layout.segmentOffset(0).toInt()
        val second = layout.segmentOffset(1).toInt()
        val stride = second - first

        val swapped = a.bytes.copyOf().also {
            a.bytes.copyInto(it, first, second, second + stride)
            a.bytes.copyInto(it, second, first, first + stride)
        }
        val moved = a.bytes.copyOf().also { b.bytes.copyInto(it, first, first, first + stride) }

        for (bytes in listOf(swapped, moved)) {
            val raw = MemoryFile(bytes)
            assertFails<FileSystemException.Corrupted> {
                files.open(raw).reader(raw).readAll()
            }
        }
    }

    @Test
    fun `an unknown cipher or a forgotten key is an error, never raw bytes`() = runTest {
        val raw = sealed(ByteArray(10))
        assertFails<FileSystemException.UnknownCipher> {
            files.open(MemoryFile(raw.bytes.withCipherId("fserver.other.v1")))
        }

        keys.forget(Source)
        assertFails<FileSystemException.MissingKey> {
            files.open(raw)
        }
    }

    @Test
    fun `a custom cipher is chosen by the id in the header`() = runTest {
        val custom = SealedFiles(listOf(XorCipher), keys)
        val raw = MemoryFile()
        val file = custom.create(Source, XorCipher.id)
        file.initialize(raw)
        file.writer(raw, raw).use { it.write(0, byteArrayOf(1, 2, 3)) }

        assertArrayEquals(byteArrayOf(1, 2, 3), custom.open(raw).reader(raw).readAll())
        assertFails<FileSystemException.UnknownCipher> {
            files.open(raw)
        }
    }

    @Test
    fun `a header round trips and only sealed files carry the magic`() {
        val header = header("key-1")
        val decoded = SealedHeader.decode(header.encoded)
        assertArrayEquals(header.encoded, decoded.encoded)
        assertTrue(SealedHeader.hasMagic(header.encoded))
        assertFalse(SealedHeader.hasMagic("plain text".toByteArray()))
        assertNotEquals(header("key-1").encoded.toList(), header.encoded.toList())
    }

    private suspend inline fun <reified T : Throwable> assertFails(message: String = "", block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw AssertionError("$message: expected ${T::class.simpleName}, got $e", e)
        }
        throw AssertionError("$message: expected ${T::class.simpleName}, nothing thrown")
    }

    private suspend fun newFile(raw: MemoryFile): SealedFile {
        val key = keys.current(Source)
        return SealedFile(header(key.id), AesGcmCipher, key.secret, SecureRandom()).also { it.initialize(raw) }
    }

    private suspend fun sealed(content: ByteArray): MemoryFile =
        MemoryFile().also { raw -> newFile(raw).writer(raw, raw).use { it.write(0, content) } }

    private fun header(keyId: String) = SealedHeader(
        segmentSize = Segment,
        nonceSize = AesGcmCipher.nonceSize,
        tagSize = AesGcmCipher.tagSize,
        cipherId = AesGcmCipher.id,
        keyId = keyId,
        salt = Random.nextBytes(SealedHeader.SaltSize),
    )

    /** Same-length id, so the rest of the file stays where it was. */
    private fun ByteArray.withCipherId(id: String): ByteArray {
        val old = AesGcmCipher.id.toByteArray()
        val new = id.padEnd(old.size, '-').take(old.size).toByteArray()
        return copyOf().also { new.copyInto(it, CipherIdOffset) }
    }

    private suspend fun FsReader.readAll(): ByteArray {
        val out = ByteArray(size().toInt())
        var position = 0
        while (position < out.size) {
            val buffer = ByteArray(minOf(1000, out.size - position))
            val read = read(position.toLong(), buffer)
            buffer.copyInto(out, position, 0, read)
            position += read
        }
        return out
    }

    private companion object {
        const val Source = "source-1"
        const val Segment = 1024
        const val Nonce = 12
        const val CipherIdOffset = 4 + 1 + 4 + 1 + 1 + 1
    }
}

/** An in-memory file, readable and writable at once like a descriptor opened for both. */
private class MemoryFile(var bytes: ByteArray = ByteArray(0)) : FsReader, FsWriter {
    override suspend fun size(): Long = bytes.size.toLong()

    override suspend fun read(offset: Long, bytes: ByteArray, length: Int): Int {
        if (offset >= this.bytes.size) return -1
        val count = minOf(length, this.bytes.size - offset.toInt())
        this.bytes.copyInto(bytes, 0, offset.toInt(), offset.toInt() + count)
        return count
    }

    override suspend fun write(offset: Long, bytes: ByteArray, length: Int) {
        val end = offset.toInt() + length
        if (end > this.bytes.size) this.bytes = this.bytes.copyOf(end)
        bytes.copyInto(this.bytes, offset.toInt(), 0, length)
    }

    override suspend fun truncate(size: Long) {
        if (size < bytes.size) bytes = bytes.copyOf(size.toInt())
    }

    override suspend fun sync() = Unit

    override fun close() = Unit
}

/** Not a cipher: proves a host method plugs in by id. Tag is a plain checksum. */
private object XorCipher : StorageCipher {
    override val id = "test.xor.v1"
    override val nonceSize = 8
    override val tagSize = 12

    override fun seal(key: SecretKey, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray =
        plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray() + tag(nonce, aad, plain)

    override fun open(key: SecretKey, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray {
        val plain = sealed.copyOf(sealed.size - tagSize).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        if (!tag(nonce, aad, plain).contentEquals(sealed.copyOfRange(sealed.size - tagSize, sealed.size))) {
            throw javax.crypto.AEADBadTagException()
        }
        return plain
    }

    private fun tag(nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(nonce + aad + plain).copyOf(tagSize)
}
