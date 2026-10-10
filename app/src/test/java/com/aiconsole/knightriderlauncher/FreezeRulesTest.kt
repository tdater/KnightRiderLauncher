package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.data.FreezeRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeRulesTest {

    @Test
    fun frozenOnlyAfterTheThreshold() {
        assertFalse(FreezeRules.frozen(lastBeatMs = 10_000, nowMs = 13_999, thresholdMs = 4_000))
        assertTrue(FreezeRules.frozen(lastBeatMs = 10_000, nowMs = 14_000, thresholdMs = 4_000))
    }

    @Test
    fun oneReportPerFreeze() {
        assertTrue(FreezeRules.shouldReport(frozen = true, reportedThisFreeze = false))
        assertFalse(FreezeRules.shouldReport(frozen = true, reportedThisFreeze = true))
        assertFalse(FreezeRules.shouldReport(frozen = false, reportedThisFreeze = false))
    }
}
