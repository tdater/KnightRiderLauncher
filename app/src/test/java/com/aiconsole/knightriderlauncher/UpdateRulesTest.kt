package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.data.UpdateRules
import com.aiconsole.knightriderlauncher.data.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRulesTest {

    private val pkg = "com.aiconsole.knightriderlauncher"

    @Test
    fun releaseSignedWithTheSameKeyInstalls() {
        assertNull(UpdateRules.apkProblem(pkg, pkg, installedVersionCode = 40, apkVersionCode = 52, sameSigner = true))
        // Same build reinstalled is allowed too.
        assertNull(UpdateRules.apkProblem(pkg, pkg, 52, 52, sameSigner = true))
    }

    @Test
    fun debugBuildOnTheHeadUnitIsExplained() {
        assertEquals(UpdateRules.DIFFERENT_KEY, UpdateRules.apkProblem(pkg, pkg, 1, 52, sameSigner = false))
    }

    @Test
    fun brokenWrongOrOlderApksAreRefused() {
        assertTrue(UpdateRules.apkProblem(pkg, null, 1, 0, null)!!.contains("isn't a valid APK"))
        assertTrue(UpdateRules.apkProblem(pkg, "com.other", 1, 5, true)!!.contains("different app"))
        assertTrue(UpdateRules.apkProblem(pkg, pkg, 60, 52, true)!!.contains("downgrades"))
    }

    @Test
    fun everyInstallerResultIsReported() {
        assertTrue(UpdateRules.statusMessage(UpdateRules.STATUS_SUCCESS, null).startsWith("Updated"))
        assertEquals(UpdateRules.DIFFERENT_KEY, UpdateRules.statusMessage(UpdateRules.STATUS_FAILURE_CONFLICT, "signatures do not match"))
        assertEquals(UpdateRules.DIFFERENT_KEY, UpdateRules.statusMessage(UpdateRules.STATUS_FAILURE_INCOMPATIBLE, null))
        assertTrue(UpdateRules.statusMessage(UpdateRules.STATUS_FAILURE_BLOCKED, "policy").contains("blocked the install (policy)"))
        assertTrue(UpdateRules.statusMessage(UpdateRules.STATUS_FAILURE_STORAGE, null).contains("storage"))
        assertTrue(UpdateRules.statusMessage(UpdateRules.STATUS_FAILURE_ABORTED, null).contains("cancelled"))
        assertTrue(UpdateRules.statusMessage(99, "odd").contains("(odd)"))
    }

    @Test
    fun releaseTagsCompareWithInstalledVersionNames() {
        assertTrue(Versions.isNewer("v1.2", "1.1"))
        assertTrue(Versions.isNewer("v1.2", "1.0.37"))
        assertFalse(Versions.isNewer("v1.2", "1.2"))
    }
}
