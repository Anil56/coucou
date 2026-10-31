package com.coucou.android.core

import com.coucou.android.mochi.BotState
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * What the phone tells the watch (android/wear/README.md, "The phone link"). The same file is built into the watch app, so both sides
 * read and write one format. Pure Kotlin plus org.json, so it runs in JVM unit tests.
 *
 * The phone sends words it has already chosen and translated: an agent's name and state word, a request's label. A command, a path or
 * the agent's own text never reaches the watch.
 */
data class WatchAgent(val pillId: String, val name: String, val state: BotState, val word: String, val argb: Int)

/** A permission request waiting on the phone. [canAllow] is false when the computer cut the command: it is then answered there. */
data class WatchRequest(val fingerprint: String, val pillId: String, val agent: String, val label: String, val canAllow: Boolean)

data class WatchState(
    val connected: Boolean,
    val headline: String,
    val toneArgb: Int,
    /** Requests and questions waiting for the user (questions are answered on the phone). */
    val waiting: Int,
    /** Most urgent first. */
    val agents: List<WatchAgent>,
    val requests: List<WatchRequest>,
    /** The phone's switch "Allow from my watch" (off by default): without it the watch only offers Deny. */
    val mayAllow: Boolean,
)

/** A decision from the watch: the request's fingerprint and Allow or Deny. */
data class WatchDecision(val fingerprint: String, val allow: Boolean)

object WatchLink {
    const val VERSION = 1
    /** The watch app announces this capability (wear/src/main/res/values/wear.xml); the phone only talks to nearby nodes that have it. */
    const val WATCH_CAPABILITY = "coucou_watch"
    /** The phone app announces this one (app/src/main/res/values/wear.xml), so the watch knows where to send. */
    const val PHONE_CAPABILITY = "coucou_phone"
    /** Phone to watch: a [WatchState]. */
    const val STATE_PATH = "/coucou/state"
    /** Watch to phone: "send me the state now" (the watch app just opened). Empty body. */
    const val HELLO_PATH = "/coucou/hello"
    /** Watch to phone: a [WatchDecision]. */
    const val DECIDE_PATH = "/coucou/decide"
    const val MAX_AGENTS = 8
    const val MAX_REQUESTS = 4
    const val MAX_TEXT = 80
    /** A message never exceeds this; a longer one is ignored. */
    const val MAX_BYTES = 16 * 1024

    private fun cut(s: String) = if (s.length <= MAX_TEXT) s else s.take(MAX_TEXT - 1) + "…"

    fun encode(s: WatchState): String = JSONObject()
        .put("v", VERSION)
        .put("connected", s.connected)
        .put("headline", cut(s.headline))
        .put("tone", s.toneArgb)
        .put("waiting", s.waiting.coerceAtLeast(0))
        .put("agents", JSONArray().apply {
            s.agents.take(MAX_AGENTS).forEach {
                put(JSONObject().put("pill", it.pillId).put("name", cut(it.name)).put("state", it.state.key).put("word", cut(it.word)).put("argb", it.argb))
            }
        })
        .put("requests", JSONArray().apply {
            s.requests.take(MAX_REQUESTS).forEach {
                put(JSONObject().put("fp", it.fingerprint).put("pill", it.pillId).put("agent", cut(it.agent)).put("label", cut(it.label)).put("allow", it.canAllow))
            }
        })
        .put("mayAllow", s.mayAllow)
        .toString()

    /** Null for anything that is not a state of this version: the watch then keeps what it had. */
    fun decode(text: String): WatchState? = try {
        val o = JSONObject(text)
        if (o.getInt("v") != VERSION) null else WatchState(
            connected = o.getBoolean("connected"),
            headline = cut(o.getString("headline")),
            toneArgb = o.getInt("tone"),
            waiting = o.getInt("waiting").coerceAtLeast(0),
            agents = o.getJSONArray("agents").objects().take(MAX_AGENTS).mapNotNull { a ->
                val state = BotState.entries.firstOrNull { it.key == a.getString("state") } ?: return@mapNotNull null
                WatchAgent(a.getString("pill"), cut(a.getString("name")), state, cut(a.getString("word")), a.getInt("argb"))
            },
            requests = o.getJSONArray("requests").objects().take(MAX_REQUESTS).map { r ->
                WatchRequest(r.getString("fp"), r.getString("pill"), cut(r.getString("agent")), cut(r.getString("label")), r.getBoolean("allow"))
            },
            mayAllow = o.getBoolean("mayAllow"),
        )
    } catch (_: JSONException) {
        null
    }

    fun encodeDecision(d: WatchDecision): ByteArray =
        JSONObject().put("v", VERSION).put("fp", d.fingerprint).put("allow", d.allow).toString().toByteArray(Charsets.UTF_8)

    fun decodeDecision(bytes: ByteArray): WatchDecision? {
        if (bytes.size > MAX_BYTES) return null
        return try {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            val fp = o.getString("fp")
            if (o.getInt("v") != VERSION || fp.isBlank()) null else WatchDecision(fp, o.getBoolean("allow"))
        } catch (_: JSONException) {
            null
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
