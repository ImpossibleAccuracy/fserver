package com.fserver.core.requirement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequirementReportTest {

    @Test
    fun `an empty report is satisfied and needs nothing from the user`() {
        val report = RequirementReport.Satisfied

        assertTrue(report.isSatisfied)
        assertFalse(report.isResolvableByUser)
    }

    @Test
    fun `solvable alone puts a button in front of the user`() {
        val report = RequirementReport(
            blockers = emptyList(),
            solvable = listOf(Requirement.SystemToggle(Requirement.SystemToggle.Kind.WIFI)),
        )

        assertFalse(report.isSatisfied)
        assertTrue(report.isResolvableByUser)
    }

    @Test
    fun `one blocker sinks the report even when everything else is solvable`() {
        val report = RequirementReport(
            blockers = listOf(
                Requirement.MissingHardware(Requirement.MissingHardware.Feature.BLUETOOTH_LE)
            ),
            solvable = listOf(Requirement.RuntimePermission(listOf("android.permission.BLUETOOTH_SCAN"))),
        )

        assertFalse(report.isSatisfied)
        assertFalse(report.isResolvableByUser)
    }
}
