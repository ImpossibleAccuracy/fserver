package com.fserver.net.transport.android.datasource.multicastdns

import timber.log.Timber

/**
 * The service type used for mDNS discovery and advertising.
 */
internal const val SERVICE_TYPE = "_fserver._tcp."

/**
 * [SERVICE_TYPE] in the shape comparisons are made in: dots trimmed, case folded.
 *
 * NSD hands the type back in several forms - `_fserver._tcp`, `_fserver._tcp.local.`,
 * `_sub._fserver._tcp.` - so filtration is a substring test against this token rather than equality.
 */
internal val SERVICE_TYPE_TOKEN = SERVICE_TYPE.trim('.').lowercase()

/** Longest `key=value` pair mDNS carries in a single TXT entry. */
private const val MAX_TXT_ENTRY_BYTES = 255

/** mDNS caps a service instance name at 63 bytes. */
private const val MAX_INSTANCE_NAME_BYTES = 63

/** Shown when the local identity has no usable display name. */
private const val FALLBACK_INSTANCE_NAME = "mDNS device"

/**
 * Attributes in the shape NSD accepts. Keys mDNS cannot carry - non-printable, containing `=`, or
 * too long together with their value - are dropped rather than corrupting the whole record.
 */
internal fun Map<String, String>.asTxtEntries(): Map<String, String> = buildMap {
    this@asTxtEntries.forEach { (key, value) ->
        val entrySize = key.toByteArray().size + 1 + value.toByteArray().size

        when {
            !key.isUsableTxtKey() -> Timber.w("Dropping TXT entry with unusable key: %s", key)

            entrySize > MAX_TXT_ENTRY_BYTES ->
                Timber.w("Dropping oversized TXT entry %s (%d bytes)", key, entrySize)

            else -> put(key, value)
        }
    }
}

/** TXT values as advertised; entries NSD could not decode are skipped. */
internal fun Map<String, ByteArray?>.asAttributes(): Map<String, String> =
    mapNotNull { (key, value) -> value?.let { key to it.toString(Charsets.UTF_8) } }.toMap()

/** A display name trimmed to what mDNS accepts as an instance name. */
internal fun instanceName(displayName: String): String = displayName
    .trim()
    .takeIf { it.isNotBlank() }
    ?.truncateToBytes(MAX_INSTANCE_NAME_BYTES)
    ?: FALLBACK_INSTANCE_NAME

private fun String.isUsableTxtKey(): Boolean =
    isNotEmpty() && all { it.code in 0x20..0x7E && it != '=' }

private fun String.truncateToBytes(limit: Int): String {
    var result = this
    while (result.toByteArray().size > limit) {
        result = result.dropLast(1)
    }
    return result.trimEnd { it.isHighSurrogate() }
}
