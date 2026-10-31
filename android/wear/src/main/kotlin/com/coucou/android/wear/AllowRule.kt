package com.coucou.android.wear

import com.coucou.android.core.WatchRequest

/** What the request screen offers next to Deny (Deny is always there). */
enum class AllowOffer {
    /** Press and hold to allow. */
    HOLD,
    /** The phone's switch "Allow from my watch" is off. */
    OFF_ON_PHONE,
    /** The watch has no screen lock: anyone holding it could allow. */
    NO_LOCK,
    /** The computer cut the command: it is allowed there, as on the phone. */
    ON_COMPUTER,
}

/** The watch's rules for a request. Pure, so they are tested on the JVM. */
object AllowRule {
    /** How long Allow is held down. */
    const val HOLD_MS = 1000
    /** A tap this soon after the screen appears was meant for something else (the same 600 ms as the phone's sheet). */
    const val EARLY_TAP_MS = 600L
    /** The phone stops offering a request 120 s after it arrived; the watch never offers one longer after it heard of it. */
    const val TTL_MS = 120_000L

    fun offer(r: WatchRequest, mayAllow: Boolean, deviceSecure: Boolean): AllowOffer = when {
        !r.canAllow -> AllowOffer.ON_COMPUTER
        !mayAllow -> AllowOffer.OFF_ON_PHONE
        !deviceSecure -> AllowOffer.NO_LOCK
        else -> AllowOffer.HOLD
    }

    fun isEarly(shownAtMs: Long, nowMs: Long): Boolean = nowMs - shownAtMs < EARLY_TAP_MS

    fun isFresh(receivedAtMs: Long, nowMs: Long): Boolean = nowMs - receivedAtMs < TTL_MS
}
