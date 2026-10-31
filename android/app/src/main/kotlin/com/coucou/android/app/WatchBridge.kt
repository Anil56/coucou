package com.coucou.android.app

import android.content.Context
import android.util.Log
import com.coucou.android.core.WatchDecision
import com.coucou.android.core.WatchLink
import com.coucou.android.core.WatchState
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

/**
 * The phone's side of the watch link (android/wear/README.md). The phone keeps the one link to the computer; a watch with Coucou installed
 * gets what the Widgets show, and sends back Allow or Deny, which go through [AppModel.decide] like a tap here.
 *
 * Only messages to nearby nodes (Bluetooth or the same Wi-Fi), never Data Layer items, which Google may sync through its cloud. With no
 * watch nearby nothing is sent at all. On a phone without Google Play services every call fails quietly and the app is as before.
 * Callbacks arrive on the main thread.
 */
class WatchBridge(
    context: Context,
    private val current: () -> WatchState,
    private val onDecision: (WatchDecision) -> Unit,
) : MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {
    private val messages = Wearable.getMessageClient(context)
    private val capabilities = Wearable.getCapabilityClient(context)
    private var watches: Set<String> = emptySet()
    /** The last state each watch was sent, so an identical push is skipped. */
    private val sent = HashMap<String, String>()

    init {
        try {
            messages.addListener(this)
            capabilities.addListener(this, WatchLink.WATCH_CAPABILITY)
            capabilities.getCapability(WatchLink.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { onCapabilityChanged(it) }
                .addOnFailureListener { Log.d(TAG, "no wearable API: ${it.javaClass.simpleName}") }
        } catch (e: RuntimeException) {
            Log.d(TAG, "no wearable API: ${e.javaClass.simpleName}")
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        watches = info.nodes.filter { it.isNearby }.map { it.id }.toSet()
        sent.keys.retainAll(watches)
        push(current())
    }

    /** Sends [state] to every nearby watch that does not have it yet. Called whenever the Widgets are redrawn. */
    fun push(state: WatchState) {
        if (watches.isEmpty()) return
        val json = WatchLink.encode(state)
        for (node in watches) if (sent[node] != json) send(node, json)
    }

    private fun send(node: String, json: String) {
        sent[node] = json
        messages.sendMessage(node, WatchLink.STATE_PATH, json.toByteArray(Charsets.UTF_8))
            .addOnFailureListener { sent.remove(node) } // sent again with the next change, or when the watch says hello
    }

    override fun onMessageReceived(e: MessageEvent) {
        when (e.path) {
            WatchLink.HELLO_PATH -> {
                // The watch app just opened: it may be one the capability has not shown yet (or it lost what it had).
                watches = watches + e.sourceNodeId
                send(e.sourceNodeId, WatchLink.encode(current()))
            }
            WatchLink.DECIDE_PATH -> WatchLink.decodeDecision(e.data)?.let(onDecision)
        }
    }

    private companion object {
        const val TAG = "CoucouWatch"
    }
}
