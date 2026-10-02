package com.fserver.core.storage.internal

import com.fserver.common.utils.IdGenerator
import com.fserver.core.crypto.spi.StorageKey
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.storage.database.StorageKey as StorageKeyRow
import com.fserver.core.store.crypto.StorageKeysStore
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Unwrapped keys stay in memory for the process, so Keystore is asked once per key. */
internal class StorageKeysStoreImpl(
    database: FServerStorageDatabase,
    private val wrapper: KeyWrapper,
    private val timeProvider: TimeProvider,
    private val random: SecureRandom = SecureRandom(),
) : StorageKeysStore {
    private val dao = database.storageKeyQueries
    private val unwrapped = ConcurrentHashMap<String, SecretKey>()

    // One source never gets two keys from racing first writes.
    private val creating = Mutex()

    /** A key Keystore can no longer unwrap (app data restored elsewhere) is replaced, not reused. */
    override suspend fun current(sourceId: String): StorageKey = withContext(Dispatchers.IO) {
        dao.selectCurrent(sourceId).executeAsOneOrNull()?.toKeyOrNull()?.let { return@withContext it }

        creating.withLock {
            dao.selectCurrent(sourceId).executeAsOneOrNull()?.toKeyOrNull() ?: create(sourceId)
        }
    }

    override suspend fun resolve(keyId: String): SecretKey? = unwrapped[keyId] ?: withContext(Dispatchers.IO) {
        dao.selectById(keyId).executeAsOneOrNull()?.toKeyOrNull()?.secret
    }

    override suspend fun forget(sourceId: String) = withContext(Dispatchers.IO) {
        dao.deleteBySource(sourceId)
        unwrapped.clear()
    }

    private fun create(sourceId: String): StorageKey {
        val keyId = IdGenerator.nextId
        val material = ByteArray(KeySize).also(random::nextBytes)
        val wrapped = wrapper.wrap(material, keyId.toByteArray())

        dao.insert(
            keyId = keyId,
            sourceId = sourceId,
            wrappingKey = wrapper.alias,
            iv = wrapped.iv,
            wrapped = wrapped.bytes,
            createdAtEpochMs = timeProvider.now().toEpochMilliseconds(),
        )
        return StorageKey(keyId, SecretKeySpec(material, "AES").also { unwrapped[keyId] = it })
    }

    private fun StorageKeyRow.toKeyOrNull(): StorageKey? {
        unwrapped[keyId]?.let { return StorageKey(keyId, it) }
        if (wrappingKey != wrapper.alias) return null

        val material = try {
            wrapper.unwrap(KeyWrapper.Wrapped(iv, wrapped), keyId.toByteArray())
        } catch (e: GeneralSecurityException) {
            Timber.w(e, "Storage key %s no longer unwraps", keyId)
            return null
        }
        return StorageKey(keyId, SecretKeySpec(material, "AES").also { unwrapped[keyId] = it })
    }

    private companion object {
        const val KeySize = 32
    }
}
