package com.coucou.android.wear

import android.content.Context
import android.util.Log
import com.coucou.android.core.WatchDecision
import com.coucou.android.core.WatchLink
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable

/**
 * The watch's side of the phone link: messages to Coucou on a nearby phone only (Bluetooth or the same Wi-Fi), never through Google's
 * cloud. What comes back arrives in [PhoneListenerService]. Callbacks run on the main thread.
 */
object PhoneLink {
    private const val TAG = "CoucouWear"

    private fun toPhone(context: Context, path: String, body: ByteArray, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        Wearable.getCapabilityClient(app).getCapability(WatchLink.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { info ->
                val phone = info.nodes.firstOrNull { it.isNearby }
                if (phone == null) {
                    done(false)
                    return@addOnSuccessListener
                }
                Wearable.getMessageClient(app).sendMessage(phone.id, path, body)
                    .addOnSuccessListener { done(true) }
                    .addOnFailureListener { Log.d(TAG, "not sent: ${it.javaClass.simpleName}"); done(false) }
            }
            .addOnFailureListener { Log.d(TAG, "no phone: ${it.javaClass.simpleName}"); done(false) }
    }

    /** Asks the phone for its state now (the app just opened); the answer comes as a [WatchLink.STATE_PATH] message. */
    fun hello(context: Context) {
        WatchStore.phone.value = PhoneReach.LOOKING
        toPhone(context, WatchLink.HELLO_PATH, ByteArray(0)) { ok ->
            if (!ok) WatchStore.phone.value = PhoneReach.AWAY
            else if (WatchStore.state.value != null) WatchStore.phone.value = PhoneReach.REACHABLE
        }
    }

    /** Sends Allow or Deny; [done] says whether the phone got it (the phone then checks it and tells the computer). */
    fun decide(context: Context, fingerprint: String, allow: Boolean, done: (Boolean) -> Unit) =
        toPhone(context, WatchLink.DECIDE_PATH, WatchLink.encodeDecision(WatchDecision(fingerprint, allow)), done)
}
