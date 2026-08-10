package com.fserver.core.data.datasource.multicastdns

import android.content.Context
import android.os.Build
import android.provider.Settings


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


/**
 * Name this device presents to peers. User-set device name when the platform has one, hardware
 * model otherwise.
 */
@Deprecated("migrate to shared prefs")
internal fun defaultEndpointName(context: Context): String =
    Settings.Global
        // Settings.Global.DEVICE_NAME is API 25; minSdk is 24.
        .getString(context.contentResolver, "device_name")
        ?.takeIf { it.isNotBlank() }
        ?: Build.MODEL
