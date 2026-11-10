package com.splashcleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class InspectionScoreTest {
    @Test fun seriousIssueIsGradeD() {
        val checks = listOf(CheckDefinition("B-01", "close", Severity.SERIOUS, "auto", "", "", CheckStatus.ISSUE))
        assertEquals("D", InspectionScore.calculate(checks, 1.0).grade)
        assertEquals(85, InspectionScore.calculate(checks, 1.0).score)
    }
    @Test fun lowCoverageIsMarkedAndNotAComplianceCertificate() {
        val result = InspectionScore.calculate(emptyList(), .5)
        assertEquals("A（覆盖不足）", result.grade)
    }
}
