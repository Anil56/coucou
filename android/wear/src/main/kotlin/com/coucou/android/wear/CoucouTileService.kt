package com.coucou.android.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * The Tile (swipe from the watch face): the phone's headline, the first agents with their state word, how many requests wait, and Open.
 * Redrawn only when the phone sends something new ([WatchStore.accept]), never on a timer. No command or request label is ever on it.
 */
class CoucouTileService : TileService() {
    override fun onTileRequest(request: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { done ->
            done.set(
                TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES)
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(request.deviceConfiguration)))
                    .build(),
            )
            "coucou tile"
        }

    override fun onTileResourcesRequest(request: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { done ->
            done.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build())
            "coucou tile resources"
        }

    private fun text(s: String, typography: Int, color: Int, lines: Int = 1) =
        Text.Builder(this, s).setTypography(typography).setColor(argb(color)).setMaxLines(lines).build()

    private fun layout(device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val s = WatchStore.forTile(this)
        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(ActionBuilders.AndroidActivity.Builder().setPackageName(packageName).setClassName(MainActivity::class.java.name).build())
                    .build(),
            )
            .build()
        val body = LayoutElementBuilders.Column.Builder()
        if (s == null) {
            body.addContent(text(getString(R.string.open_on_phone), Typography.TYPOGRAPHY_BODY1, WHITE, lines = 2))
        } else {
            body.addContent(text(s.headline, Typography.TYPOGRAPHY_TITLE3, WHITE, lines = 2))
            if (s.connected && s.waiting > 0) body.addContent(text(resources.getQuantityString(R.plurals.tile_waiting, s.waiting, s.waiting), Typography.TYPOGRAPHY_BODY1, ORANGE))
            if (s.connected) for (a in s.agents.take(2)) body.addContent(text("${a.name} · ${a.word}", Typography.TYPOGRAPHY_CAPTION1, DIM))
        }
        return PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(text(getString(R.string.app_name), Typography.TYPOGRAPHY_CAPTION1, DIM))
            .setContent(body.build())
            .setPrimaryChipContent(CompactChip.Builder(this, getString(R.string.tile_open), open, device).build())
            .build()
    }

    private companion object {
        const val RESOURCES = "1"
        const val WHITE = 0xFFFFFFFF.toInt()
        const val DIM = 0xFF8E8E93.toInt()
        const val ORANGE = 0xFFFF9F0A.toInt()
    }
}
