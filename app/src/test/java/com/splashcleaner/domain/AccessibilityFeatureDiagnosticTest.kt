package com.splashcleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityFeatureDiagnosticTest {
    @Test fun noNodesOrNoSuccessfulReadsIsIndeterminate() {
        assertEquals("readability_indeterminate", AccessibilityFeatureDiagnostic(0, 0, 0, false, false).result())
        assertEquals("readability_indeterminate", AccessibilityFeatureDiagnostic(4, 0, 4, false, false).result())
    }

    @Test fun readFeaturesAreReducedToCoarseStates() {
        assertEquals("readable_ad_and_skip", AccessibilityFeatureDiagnostic(5, 5, 0, true, true).result())
        assertEquals("readable_ad_label", AccessibilityFeatureDiagnostic(5, 5, 0, true, false).result())
        assertEquals("readable_skip_label", AccessibilityFeatureDiagnostic(5, 5, 0, false, true).result())
        assertEquals("readable_features_absent", AccessibilityFeatureDiagnostic(5, 5, 0, false, false).result())
    }

    @Test fun persistedOutcomeContainsOnlyCoarseFeatureAndRuleStates() {
        assertEquals(
            "weibo_readable_ad_and_skip_no_rules",
            weiboDiagnosticOutcome(AccessibilityFeatureDiagnostic(8, 8, 0, true, true), "no_rules")
        )
        assertEquals(
            "weibo_readability_indeterminate_rules_unavailable",
            weiboDiagnosticOutcome(AccessibilityFeatureDiagnostic(0, 0, 0, false, false), "rules_unavailable")
        )
    }
}

