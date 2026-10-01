package com.fserver.files.fs.impl

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.fserver.common.exception.FileSystemException
import com.fserver.files.fs.FsReader
import com.fserver.files.fs.FsWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

// Byte I/O for files served by a ContentProvider: the same call for every provider, unlike
// creating, renaming and deleting.

@SuppressLint("Recycle")
internal suspend fun readProviderFile(context: Context, uri: Uri): InputStream =
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)
            ?: throw FileSystemException.InvalidPath(uri.toString())
    }

/** Positional, so a provider that hands back a pipe instead of a seekable descriptor fails on read. */
@SuppressLint("Recycle")
internal suspend fun openProviderReader(context: Context, uri: Uri): FsReader =
    withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileSystemException.InvalidPath(uri.toString())

        ChannelReader(FileInputStream(descriptor.fileDescriptor).channel, onClose = descriptor::close)
    }

@SuppressLint("Recycle")
internal suspend fun openProviderDescriptor(context: Context, uri: Uri): ParcelFileDescriptor =
    withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileSystemException.InvalidPath(uri.toString())
    }

/**
 * Positional, because chunks may land out of order — so a provider that hands back a pipe instead
 * of a seekable descriptor cannot serve a transfer here at all.
 */
@SuppressLint("Recycle")
internal suspend fun openProviderWriter(context: Context, uri: Uri): FsWriter =
    withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")
            ?: throw FileSystemException.InvalidPath(uri.toString())

        // Built from the descriptor, so the stream does not own it: only `descriptor` closes the fd.
        ChannelWriter(FileOutputStream(descriptor.fileDescriptor).channel, onClose = descriptor::close)
    }

@SuppressLint("Recycle")
internal suspend fun openProviderOutput(context: Context, uri: Uri): OutputStream =
    withContext(Dispatchers.IO) {
        // "wt": a plain "w" leaves the old tail behind on some providers.
        context.contentResolver.openOutputStream(uri, "wt")
            ?: throw FileSystemException.InvalidPath(uri.toString())
    }

internal fun Cursor.longOrZero(column: Int): Long =
    if (isNull(column)) 0L else getLong(column)
