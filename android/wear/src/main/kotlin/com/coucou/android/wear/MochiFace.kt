package com.coucou.android.wear

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.coucou.android.mochi.BotState
import com.coucou.android.mochi.MochiEngine
import com.coucou.android.mochi.MochiPainter.drawMochi
import com.coucou.android.mochi.Rgb

/**
 * Mochi on the watch: the phone's engine and painter (built in from the phone app), redrawn every frame while the app is on screen only.
 * When the screen dims the activity stops and so does the loop.
 */
@Composable
fun MochiFace(state: BotState, argb: Int?, modifier: Modifier = Modifier) {
    val engine = remember { MochiEngine({ SystemClock.elapsedRealtimeNanos() / 1e6 }) }
    val frame = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    SideEffect {
        engine.setState(state)
        engine.bodyColor = argb?.let { Rgb(((it shr 16) and 0xFF) / 255.0, ((it shr 8) and 0xFF) / 255.0, (it and 0xFF) / 255.0) }
    }
    LaunchedEffect(engine, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { ns ->
                    val dt = if (last == 0L) 1.0 / 60 else ((ns - last) / 1e9).coerceIn(0.0, 0.1)
                    last = ns
                    engine.update(dt)
                    frame.longValue = ns
                }
            }
        }
    }
    Canvas(modifier) {
        frame.longValue // read so the canvas redraws every frame
        drawMochi(engine, System.nanoTime() / 1e9)
    }
}
