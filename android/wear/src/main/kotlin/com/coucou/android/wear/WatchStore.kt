package com.coucou.android.wear

import android.content.Context
import android.os.SystemClock
import androidx.wear.tiles.TileService
import com.coucou.android.core.WatchLink
import com.coucou.android.core.WatchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the phone answered the last time the watch looked for it. */
enum class PhoneReach { LOOKING, REACHABLE, AWAY }

/** A state from the phone and when it arrived ([SystemClock.elapsedRealtime]), so a request is not offered after it expired. */
data class Received(val state: WatchState, val atMs: Long)

/** What the phone last said. In memory for the app; a copy without the requests is kept on the watch for the Tile. */
object WatchStore {
    private val _state = MutableStateFlow<Received?>(null)
    val state: StateFlow<Received?> = _state
    val phone = MutableStateFlow(PhoneReach.LOOKING)

    private const val PREFS = "coucou_watch"
    private const val TILE_KEY = "tile_state"

    /** A message from the phone ([WatchLink.STATE_PATH]); anything else is ignored. Called on a background thread. */
    fun accept(context: Context, bytes: ByteArray) {
        if (bytes.size > WatchLink.MAX_BYTES) return
        val s = WatchLink.decode(String(bytes, Charsets.UTF_8)) ?: return
        _state.value = Received(s, SystemClock.elapsedRealtime())
        phone.value = PhoneReach.REACHABLE
        // The Tile's copy never holds a request: it only says how many wait.
        val tileCopy = WatchLink.encode(s.copy(requests = emptyList()))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(TILE_KEY, null) != tileCopy) {
            prefs.edit().putString(TILE_KEY, tileCopy).apply()
            TileService.getUpdater(context).requestUpdate(CoucouTileService::class.java)
        }
    }

    /** What the Tile shows: the state in memory, else the copy kept from last time, else nothing yet. */
    fun forTile(context: Context): WatchState? = _state.value?.state
        ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TILE_KEY, null)?.let(WatchLink::decode)
}
