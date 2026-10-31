package com.coucou.android.wear

import com.coucou.android.core.WatchRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowRuleTest {
    private val r = WatchRequest("fp", "claude-code", "Claude Code", "Running a command", canAllow = true)

    @Test fun holdIsOfferedOnlyWithTheSwitchALockAndAWholeCommand() {
        assertEquals(AllowOffer.HOLD, AllowRule.offer(r, mayAllow = true, deviceSecure = true))
        assertEquals(AllowOffer.OFF_ON_PHONE, AllowRule.offer(r, mayAllow = false, deviceSecure = true))
        assertEquals(AllowOffer.NO_LOCK, AllowRule.offer(r, mayAllow = true, deviceSecure = false))
        assertEquals(AllowOffer.ON_COMPUTER, AllowRule.offer(r.copy(canAllow = false), mayAllow = true, deviceSecure = true))
    }

    @Test fun aCutCommandSaysComputerWhateverElseIsTrue() {
        assertEquals(AllowOffer.ON_COMPUTER, AllowRule.offer(r.copy(canAllow = false), mayAllow = false, deviceSecure = false))
    }

    @Test fun earlyTapsAndOldRequestsAreRefused() {
        assertTrue(AllowRule.isEarly(1_000, 1_599))
        assertFalse(AllowRule.isEarly(1_000, 1_600))
        assertTrue(AllowRule.isFresh(0, 119_999))
        assertFalse(AllowRule.isFresh(0, 120_000))
    }
}
