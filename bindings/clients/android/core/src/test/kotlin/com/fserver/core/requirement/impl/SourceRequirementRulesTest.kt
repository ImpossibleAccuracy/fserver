package com.fserver.core.requirement.impl

import android.Manifest
import com.fserver.common.utils.SourcePaths
import com.fserver.core.files.SourceLocation
import com.fserver.core.requirement.Requirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Storage is the area Android has rewritten hardest, so which grant a source needs is entirely a
 * function of the sdk it runs on. Same deal as [RequirementRulesTest]: `sdkInt` is a parameter
 * precisely so this runs as a plain JVM test.
 */
class SourceRequirementRulesTest {

    @Test
    fun `app-private storage asks for nothing, on every sdk`() {
        for (sdkInt in SupportedSdks) {
            val rules = sourceRequirementRules(SourceLocation.Internal("bucket"), sdkInt)

            assertEquals(RequirementRules(), rules)
        }
    }

    @Test
    fun `a document tree asks for nothing, because the grant is the tree itself`() {
        for (sdkInt in SupportedSdks) {
            val rules = sourceRequirementRules(SourceLocation.Tree("content://tree/1"), sdkInt)

            assertEquals(RequirementRules(), rules)
        }
    }

    @Test
    fun `media on api 33 asks for one permission per media type`() {
        val rules = sourceRequirementRules(SourceLocation.Media, sdkInt = 33)

        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            ),
            rules.permissions,
        )
    }

    @Test
    fun `media on api 30 asks for the one storage permission it had then`() {
        val rules = sourceRequirementRules(SourceLocation.Media, sdkInt = 30)

        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), rules.permissions)
    }

    @Test
    fun `media before scoped storage asks to write too, because the write goes through the file`() {
        val rules = sourceRequirementRules(SourceLocation.Media, sdkInt = 28)

        assertEquals(
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ),
            rules.permissions,
        )
    }

    @Test
    fun `media from scoped storage on never asks to write`() {
        for (sdkInt in SupportedSdks.filter { it >= 29 }) {
            assertFalse(
                "sdk $sdkInt",
                Manifest.permission.WRITE_EXTERNAL_STORAGE in
                    sourceRequirementRules(SourceLocation.Media, sdkInt).permissions,
            )
        }
    }

    @Test
    fun `a raw path on api 30 asks for all files access and no runtime permission`() {
        for (location in rawPathLocations) {
            val rules = sourceRequirementRules(location, sdkInt = 30)

            assertEquals(
                listOf(Requirement.SpecialPermission.Kind.ALL_FILES_ACCESS),
                rules.specialPermissions,
            )
            // The runtime pair no longer reaches a path outside the app, so asking for it would
            // only spend the user's one denial on a permission that would not have helped.
            assertTrue(rules.permissions.isEmpty())
        }
    }

    @Test
    fun `a raw path on api 29 asks for nothing it cannot use`() {
        for (location in rawPathLocations) {
            val rules = sourceRequirementRules(location, sdkInt = 29)

            // Scoped storage already redirects the runtime pair here and ALL_FILES_ACCESS does not
            // exist yet, so both halves would be a grant that changes nothing - and the write half
            // is capped at 28 in the manifest, which would leave a requirement no dialog can clear.
            assertTrue(rules.permissions.isEmpty())
            assertTrue(rules.specialPermissions.isEmpty())
        }
    }

    @Test
    fun `a raw path before scoped storage asks for the runtime pair instead`() {
        for (location in rawPathLocations) {
            val rules = sourceRequirementRules(location, sdkInt = 28)

            assertEquals(
                listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
                rules.permissions,
            )
            assertTrue(rules.specialPermissions.isEmpty())
        }
    }

    @Test
    fun `no source rule has anything to say about the network`() {
        // Every one of these is answered from this device alone, so a report must not wait on a
        // network read to produce it.
        for (sdkInt in SupportedSdks) {
            for (location in AllLocations) {
                assertFalse("sdk $sdkInt, $location", sourceRequirementRules(location, sdkInt).needsNetwork)
            }
        }
    }

    private val rawPathLocations = listOf(
        SourceLocation.Directory("/storage/emulated/0/DCIM"),
        SourceLocation.Root(
            listOf(
                SourceLocation.Root.Volume(
                    id = SourcePaths.PrimaryVolume,
                    path = "/storage/emulated/0",
                ),
            ),
        ),
    )

    private val AllLocations: List<SourceLocation>
        get() = rawPathLocations + listOf(
            SourceLocation.Media,
            SourceLocation.Tree("content://tree/1"),
            SourceLocation.Internal("bucket"),
        )

    private companion object {
        /** minSdk to the newest the rules branch on, plus every boundary in between. */
        val SupportedSdks = listOf(24, 28, 29, 30, 32, 33, 34, 36, 37)
    }
}
