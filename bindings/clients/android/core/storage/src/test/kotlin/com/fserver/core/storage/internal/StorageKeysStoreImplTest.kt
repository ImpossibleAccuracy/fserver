package com.fserver.core.storage.internal

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.time.Instant

/** Against real SQLite, with a plain JCE key standing in for Keystore. */
class StorageKeysStoreImplTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        .also { FServerStorageDatabase.Schema.create(it) }

    private val database = FServerStorageDatabase(driver)
    private val wrapper = JceWrapper()

    @After
    fun tearDown() = driver.close()

    @Test
    fun `one key per source, the same on every call`() = runTest {
        val store = store()
        val a = store.current("a")

        assertEquals(a.id, store.current("a").id)
        assertNotEquals(a.id, store.current("b").id)
    }

    @Test
    fun `a key outlives the process that made it, and is stored only wrapped`() = runTest {
        val key = store().current("a")

        assertArrayEquals(key.secret.encoded, store().resolve(key.id)?.encoded)
        assertEquals(key.id, store().current("a").id)

        val row = database.storageKeyQueries.selectById(key.id).executeAsOne()
        assertFalse(row.wrapped.asList().windowed(key.secret.encoded.size).contains(key.secret.encoded.asList()))
    }

    @Test
    fun `a forgotten source opens nothing`() = runTest {
        val store = store()
        val key = store.current("a")
        store.forget("a")

        assertNull(store.resolve(key.id))
        assertNotEquals(key.id, store.current("a").id)
    }

    @Test
    fun `a key the wrapping key no longer opens is replaced, not handed out`() = runTest {
        val key = store().current("a")
        // As after restoring the app's data on another device: same rows, different Keystore.
        val restored = StorageKeysStoreImpl(database, JceWrapper(), FixedTime)

        assertNull(restored.resolve(key.id))
        assertNotEquals(key.id, restored.current("a").id)
    }

    @Test
    fun `an unknown id resolves to nothing`() = runTest {
        assertNull(store().resolve("nope"))
    }

    private fun store() = StorageKeysStoreImpl(database, wrapper, FixedTime)

    private object FixedTime : TimeProvider {
        private var tick = 0L
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000 + tick++)
    }
}

private class JceWrapper : KeyWrapper {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override val alias = "test"

    override fun wrap(plain: ByteArray, aad: ByteArray): KeyWrapper.Wrapped {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(aad)
        }
        return KeyWrapper.Wrapped(cipher.iv, cipher.doFinal(plain))
    }

    override fun unwrap(wrapped: KeyWrapper.Wrapped, aad: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped.iv))
            updateAAD(aad)
            doFinal(wrapped.bytes)
        }
}
