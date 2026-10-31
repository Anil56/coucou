package com.coucou.android

import com.coucou.android.core.GlanceTone
import com.coucou.android.core.UserSettings
import com.coucou.android.core.WatchAgent
import com.coucou.android.core.WatchDecision
import com.coucou.android.core.WatchLink
import com.coucou.android.core.WatchRequest
import com.coucou.android.core.WatchState
import com.coucou.android.core.WatchStateBuilder
import com.coucou.android.core.WidgetContent
import com.coucou.android.core.WidgetTile
import com.coucou.android.link.ApprovalRequest
import com.coucou.android.mochi.BotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone-watch format (android/wear/README.md): what goes to the watch, and that a command never does. */
class WatchStateTest {
    private val agent = WatchAgent("claude-code", "Claude Code", BotState.WORKING, "Working", 0xFFD97757.toInt())
    private val state = WatchState(
        connected = true, headline = "Claude Code is working", toneArgb = GlanceTone.BUSY.argb, waiting = 1,
        agents = listOf(agent), requests = listOf(WatchRequest("fp1", "claude-code", "Claude Code", "Running a command · npm", true)), mayAllow = false,
    )

    @Test fun aStateComesBackAsItWent() {
        assertEquals(state, WatchLink.decode(WatchLink.encode(state)))
    }

    @Test fun anotherVersionOrBrokenTextIsIgnored() {
        assertNull(WatchLink.decode(WatchLink.encode(state).replace("\"v\":1", "\"v\":2")))
        assertNull(WatchLink.decode("{"))
        assertNull(WatchLink.decode("{}"))
    }

    @Test fun anUnknownStateDropsOnlyThatAgent() {
        val text = WatchLink.encode(state.copy(agents = listOf(agent, agent.copy(pillId = "x")))).replaceFirst("\"working\"", "\"dancing\"")
        assertEquals(listOf("x"), WatchLink.decode(text)!!.agents.map { it.pillId })
    }

    @Test fun longTextsAndLongListsAreCut() {
        val long = state.copy(headline = "a".repeat(500), agents = List(20) { agent.copy(pillId = "p$it") })
        val back = WatchLink.decode(WatchLink.encode(long))!!
        assertEquals(WatchLink.MAX_TEXT, back.headline.length)
        assertEquals(WatchLink.MAX_AGENTS, back.agents.size)
    }

    @Test fun aDecisionComesBackAsItWentAndJunkIsRefused() {
        assertEquals(WatchDecision("fp1", true), WatchLink.decodeDecision(WatchLink.encodeDecision(WatchDecision("fp1", true))))
        assertEquals(WatchDecision("fp1", false), WatchLink.decodeDecision(WatchLink.encodeDecision(WatchDecision("fp1", false))))
        assertNull(WatchLink.decodeDecision("{\"v\":1,\"fp\":\"\",\"allow\":true}".toByteArray()))
        assertNull(WatchLink.decodeDecision("{\"v\":2,\"fp\":\"a\",\"allow\":true}".toByteArray()))
        assertNull(WatchLink.decodeDecision("allow".toByteArray()))
        assertNull(WatchLink.decodeDecision(ByteArray(WatchLink.MAX_BYTES + 1) { 'a'.code.toByte() }))
    }

    private fun content(connected: Boolean) = WidgetContent(
        headline = "Claude Code needs your OK", tone = GlanceTone.ATTENTION, connected = connected, waiting = 1,
        tiles = emptyList(), roster = listOf(WidgetTile("claude-code", "Claude Code", BotState.APPROVAL, "Waiting for your approval", 0xFFD97757.toInt())),
    )

    @Test fun theWatchGetsTheLabelNeverTheCommand() {
        val r = ApprovalRequest("claude-code", "fp1", "Bash", "npm publish --token=s3cret /home/me/project", 0L)
        val built = WatchStateBuilder.build(content(true), listOf(r), { "Claude Code" }, mayAllow = true)
        val text = WatchLink.encode(built)
        assertFalse(text.contains("s3cret"))
        assertFalse(text.contains("/home/me"))
        assertEquals("fp1", built.requests.single().fingerprint)
        assertTrue(built.requests.single().canAllow)
        assertTrue(built.mayAllow)
    }

    @Test fun aCutCommandIsNotAllowedFromTheWatch() {
        val r = ApprovalRequest("claude-code", "fp1", "Bash", "npm run", 0L, truncated = true)
        assertFalse(WatchStateBuilder.build(content(true), listOf(r), { "Claude Code" }, mayAllow = true).requests.single().canAllow)
    }

    @Test fun noRequestIsOfferedWhileTheComputerIsNotConnected() {
        val r = ApprovalRequest("claude-code", "fp1", "Bash", "ls", 0L)
        assertTrue(WatchStateBuilder.build(content(false), listOf(r), { "Claude Code" }, mayAllow = true).requests.isEmpty())
    }

    @Test fun allowFromTheWatchIsOffByDefault() {
        assertFalse(UserSettings().watchAllow)
    }
}
