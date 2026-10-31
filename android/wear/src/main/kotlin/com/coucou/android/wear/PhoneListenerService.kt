package com.coucou.android.wear

import com.coucou.android.core.WatchLink
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Receives what Coucou on the phone sends, also while the app is closed (the Tile follows it). */
class PhoneListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == WatchLink.STATE_PATH) WatchStore.accept(this, event.data)
    }
}
