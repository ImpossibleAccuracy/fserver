package com.fserver.core.export

import android.content.ContextWrapper
import com.fserver.common.model.FileSize
import com.fserver.common.task.ProgressTask
import com.fserver.core.crypto.model.EncryptionPolicy
import com.fserver.core.external.export.DataExport
import com.fserver.core.external.export.ExportProgress
import com.fserver.core.external.export.ExportReport
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.access.LocalFileEditor
import com.fserver.core.requirement.Requirement
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.support.FakeRequirementsChecker
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.support.sourceFiles
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.FilesNode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/** The archive carries plaintext bytes of what is here, metadata of the rest, and the settings. */
class DataExportTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider(TestEpoch)
    private val storage = FakeStorage(clock = clock)
    private val requirements = FakeRequirementsChecker()

    private lateinit var root: File
    private lateinit var editor: LocalFileEditor
    private lateinit var export: DataExport

    @Before
    fun setUp() {
        val node = FilesNode.create(ContextWrapper(null), stagingDir = temp.newFolder("staging"))
        root = temp.newFolder("source-root")
        editor = LocalFileEditor(
            storage,
            sourceFiles(storage, node),
            LocalIndex(storage, node, clock).writer,
            requirements,
            clock,
        )
        export = DataExport(storage, editor, requirements, clock)
    }

    @Test
    fun `present files go in as bytes, evicted and peer-only ones as metadata`() = runTest {
        register()
        write("docs/a.txt", "hello")
        val evicted = write("b.txt", "gone")
        storage.index.updateFileState(key(evicted), LocalIndexedFile.State.Evicted(TestEpoch))
        storage.remoteIndex.upsert(
            "device-peer",
            RemoteIndexedFile(
                sourceId = SourceId, fileId = "peer-file", path = "peer.txt",
                state = LocalIndexedFile.State.Present(), size = FileSize(3),
                modifiedAt = TestEpoch, seenAt = TestEpoch,
            ),
        )

        val (archive, task) = run()
        val report = task.result().getOrThrow()

        assertEquals("hello", archive["files/$SourceId/docs/a.txt"])
        assertFalse(archive.keys.any { it.endsWith("b.txt") && it.startsWith("files/") })
        assertEquals(1, report.files)
        assertEquals(2, report.metadataOnly)
        assertEquals(FileSize(5), report.size)

        val metadata = archive.getValue("metadata/$SourceId.json")
        assertTrue(metadata.contains("\"held\": \"evicted\""))
        assertTrue(metadata.contains("\"held\": \"peer\""))
        assertTrue(archive.getValue("manifest.json").contains("\"formatVersion\": 1"))
        assertTrue(archive.getValue("settings/sources.json").contains("\"id\": \"$SourceId\""))
    }

    @Test
    fun `encrypted sources export plaintext`() = runTest {
        register(encryption = EncryptionPolicy.Required())
        write("secret.txt", "plain")

        assertFalse(File(root, "secret.txt").readText() == "plain")
        assertEquals("plain", run().first["files/$SourceId/secret.txt"])
    }

    @Test
    fun `an unreachable source is skipped, not fatal`() = runTest {
        register()
        write("a.txt", "hello")
        requirements.source = RequirementReport(
            blockers = emptyList(),
            solvable = listOf(Requirement.RuntimePermission(listOf("android.permission.READ_EXTERNAL_STORAGE"))),
        )

        val (archive, task) = run()
        val report = task.result().getOrThrow()

        assertEquals(0, report.files)
        assertEquals(listOf("a.txt"), report.skipped.map { it.path })
        assertTrue(archive.getValue("metadata/$SourceId.json").contains("\"archived\": false"))
    }

    @Test
    fun `progress ends at the totals`() = runTest {
        register()
        write("a.txt", "hello")
        write("b.txt", "world!")

        val out = ByteArrayOutputStream()
        val progress = export.export(out).progress.toList()

        assertEquals(ExportProgress(2, 2, FileSize(11), FileSize(11)), progress.last())
    }

    @Test
    fun `host entries land under host`() = runTest {
        register()

        val out = ByteArrayOutputStream()
        export.export(out, hostEntries = mapOf("app.json" to "{}".toByteArray())).result().getOrThrow()

        assertEquals("{}", unzip(out.toByteArray())["host/app.json"])
    }

    @Test
    fun `a partial export holds only the chosen sources`() = runTest {
        register()
        write("a.txt", "hello")
        register(id = OtherId, location = temp.newFolder("other-root"))
        editor.create(OtherId, "b.txt").write { it.write(0, "other".toByteArray()) }

        val out = ByteArrayOutputStream()
        val report = export.export(out, sourceIds = setOf(OtherId)).result().getOrThrow()
        val archive = unzip(out.toByteArray())

        assertEquals(1, report.files)
        assertEquals("other", archive["files/$OtherId/b.txt"])
        assertFalse(archive.keys.any { it.contains(SourceId) })
        assertTrue(archive.getValue("manifest.json").contains("\"scope\": \"sources\""))
    }

    @Test
    fun `an unknown source fails the export`() = runTest {
        val result = export.export(ByteArrayOutputStream(), sourceIds = setOf("missing")).result()

        assertTrue(result.isFailure)
    }

    private suspend fun run(): Pair<Map<String, String>, ProgressTask<ExportProgress, ExportReport>> {
        val out = ByteArrayOutputStream()
        val task = export.export(out)
        task.result().getOrThrow()
        return unzip(out.toByteArray()) to task
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().decodeToString())
            }
        }
    }

    private suspend fun register(
        encryption: EncryptionPolicy = EncryptionPolicy.Off,
        id: String = SourceId,
        location: File = root,
    ) {
        val entry = sourceEntry(
            id = id,
            location = SourceLocation.Directory(location.absolutePath),
            role = SourceEntry.Role.Initiator,
        )
        storage.sources.upsert(entry.copy(preferences = entry.preferences.copy(encryption = encryption)))
    }

    private suspend fun write(path: String, content: String): String =
        editor.create(SourceId, path).write { it.write(0, content.toByteArray()) }.fileId

    private fun key(fileId: String) = IndexedFileKey(fileId = fileId, sourceId = SourceId)

    private companion object {
        const val SourceId = "source-1"
        const val OtherId = "source-2"
    }
}
