package com.fserver.core.requirement.impl

import android.Manifest
import android.app.Application
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.requirement.Requirement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The half of the checker that cannot be a plain JVM test: it reads the merged manifest through
 * `PackageManager` and branches on the real `Build.VERSION.SDK_INT`, neither of which exists
 * without Robolectric. Everything version-dependent that *can* take `sdkInt` as a parameter lives
 * in [SourceRequirementRulesTest] instead.
 *
 * All of it goes through `forSource`, which is the one entry point that touches neither the network
 * nor Play services - so what is asserted is the permission split and nothing else.
 */
@RunWith(RobolectricTestRunner::class)
class RequirementsCheckerPermissionsTest {

    @Test
    @Config(sdk = [28])
    fun `a permission the manifest declares but has not granted is the user's to solve`() = runTest {
        declare(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )

        val report = checker().forSource(SourceLocation.Media)

        assertEquals(
            listOf(
                Requirement.RuntimePermission(
                    listOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    )
                )
            ),
            report.solvable,
        )
        assertTrue(report.blockers.isEmpty())
    }

    @Test
    @Config(sdk = [28])
    fun `a permission the manifest never declares is a blocker, not a request`() = runTest {
        // The host shipped the read half and skipped the write half - no dialog exists for the
        // second one, so offering the user a button would be offering a button that does nothing.
        declare(Manifest.permission.READ_EXTERNAL_STORAGE)

        val report = checker().forSource(SourceLocation.Media)

        assertEquals(
            listOf(Requirement.RuntimePermission(listOf(Manifest.permission.READ_EXTERNAL_STORAGE))),
            report.solvable,
        )
        assertEquals(
            listOf(
                Requirement.UndeclaredPermission(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            ),
            report.blockers,
        )
    }

    /**
     * API 33 is also where `getPackageInfo` moves to `PackageInfoFlags`, so this covers the other
     * branch of the manifest read as well as the media permissions the rules ask for there.
     */
    @Test
    @Config(sdk = [33])
    fun `the split holds on api 33, where the manifest is read a different way`() = runTest {
        declare(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)

        val report = checker().forSource(SourceLocation.Media)

        assertEquals(
            listOf(
                Requirement.RuntimePermission(
                    listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                    )
                )
            ),
            report.solvable,
        )
        assertEquals(
            listOf(Requirement.UndeclaredPermission(listOf(Manifest.permission.READ_MEDIA_AUDIO))),
            report.blockers,
        )
    }

    @Test
    @Config(sdk = [33])
    fun `nothing is reported once every permission is declared and granted`() = runTest {
        val media = listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
        declare(*media.toTypedArray())
        shadowOf(application).grantPermissions(*media.toTypedArray())

        assertTrue(checker().forSource(SourceLocation.Media).isSatisfied)
    }

    /**
     * A source that asks for nothing must not be reported as blocked by the manifest it never
     * consults - the empty rule set and the "host declared nothing" case look alike from here.
     */
    @Test
    @Config(sdk = [33])
    fun `a source that needs no permission is satisfied by an empty manifest`() = runTest {
        declare()

        assertTrue(checker().forSource(SourceLocation.Internal("bucket")).isSatisfied)
    }

    private val application: Application get() = RuntimeEnvironment.getApplication()

    private fun checker() = RequirementsCheckerImpl(application, UnusedNetworkInfoRepository)

    /**
     * Replaces what the merged manifest declares. This is the module's own manifest under test, and
     * it deliberately declares none of these - the permissions a host may or may not ship are the
     * subject here, so each test states its own host.
     */
    private fun declare(vararg permissions: String) {
        shadowOf(application.packageManager)
            .getInternalMutablePackageInfo(application.packageName)
            .requestedPermissions = arrayOf(*permissions)
    }
}

/**
 * `forSource` never reads the network - the rules it resolves have nothing to say about it - and a
 * repository that throws is how that stays true.
 */
private object UnusedNetworkInfoRepository : NetworkInfoRepository {
    override val networkInfo: Flow<NetworkInfo?> get() = error("source rules must not read the network")

    override val localRoutes: Flow<List<KnownRoute>> get() = flowOf(emptyList())

    override fun refresh() = error("source rules must not read the network")
}
