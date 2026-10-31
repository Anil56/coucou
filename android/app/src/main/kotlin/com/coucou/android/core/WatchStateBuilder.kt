package com.coucou.android.core

import com.coucou.android.link.ApprovalRequest

/** What the phone sends the watch, built from the same content the Widgets draw, so the watch never says anything they do not. */
object WatchStateBuilder {
    fun build(content: WidgetContent, approvals: List<ApprovalRequest>, nameOf: (String) -> String, mayAllow: Boolean): WatchState =
        WatchState(
            connected = content.connected,
            headline = content.headline,
            toneArgb = content.tone.argb,
            waiting = content.waiting,
            agents = content.roster.take(WatchLink.MAX_AGENTS).map { WatchAgent(it.pillId, it.name, it.state, it.stateWord, it.argb) },
            // The label only ("Running a command · npm"), never the command: the watch shows it without a tap.
            requests = if (!content.connected) emptyList() else approvals.take(WatchLink.MAX_REQUESTS).map {
                WatchRequest(it.fingerprint, it.pillId, nameOf(it.pillId), SafeText.requestLabel(it.tool, it.command), ApprovalSheetPlan.canAllowHere(it))
            },
            mayAllow = mayAllow,
        )
}
