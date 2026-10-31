package com.coucou.android.wear

import android.app.KeyguardManager
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.coucou.android.core.WatchAgent
import com.coucou.android.mochi.BotState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The state colours of the phone's Widgets (core/Glance.kt, GlanceTone).
private val ORANGE = Color(0xFFFF9F0A)
private val GREEN = Color(0xFF34C759)
private val RED = Color(0xFFFF453A)
private val DIM = Color(0xFF8E8E93)
private val ROW = Color(0xFF1C1C1E)

@Composable
fun CoucouWatchApp() {
    val received by WatchStore.state.collectAsState()
    val phone by WatchStore.phone.collectAsState()
    val nav = rememberSwipeDismissableNavController()
    MaterialTheme {
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(received, phone) { fp -> nav.navigate("request/${Uri.encode(fp)}") }
            }
            composable("request/{fp}") { entry ->
                RequestScreen(received, entry.arguments?.getString("fp")) { nav.popBackStack("home", inclusive = false) }
            }
        }
    }
}

@Composable
private fun HomeScreen(received: Received?, phone: PhoneReach, onRequest: (String) -> Unit) {
    val list = rememberScalingLazyListState()
    val s = received?.state
    // Wait a moment for the phone's answer before saying it is not there.
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(3000); waited = true }
    val now = SystemClock.elapsedRealtime()
    val requests = if (received != null && AllowRule.isFresh(received.atMs, now)) received.state.requests else emptyList()
    val top = s?.agents?.firstOrNull()
    val mochiState = when {
        s == null || !s.connected -> BotState.SLEEPING
        requests.isNotEmpty() -> BotState.APPROVAL
        else -> top?.state ?: BotState.IDLE
    }
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = list) }) {
        ScalingLazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item { MochiFace(mochiState, top?.argb, Modifier.size(76.dp)) }
            item {
                val line = when {
                    s != null -> s.headline
                    phone == PhoneReach.AWAY -> stringResource(R.string.phone_away)
                    !waited -> stringResource(R.string.looking_for_phone)
                    else -> stringResource(R.string.open_on_phone)
                }
                Text(line, textAlign = TextAlign.Center, style = MaterialTheme.typography.title3, modifier = Modifier.padding(horizontal = 8.dp))
            }
            if (s != null && phone == PhoneReach.AWAY) {
                item { Text(stringResource(R.string.phone_away), color = DIM, style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center) }
            }
            items(requests, key = { "r" + it.fingerprint }) { r ->
                Chip(
                    onClick = { onRequest(r.fingerprint) },
                    label = { Text(r.agent, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(stringResource(R.string.needs_you), maxLines = 1) },
                    colors = ChipDefaults.chipColors(backgroundColor = ORANGE, contentColor = Color.Black, secondaryContentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (s != null && s.connected) {
                if (s.agents.isEmpty()) item { Text(stringResource(R.string.no_agents), color = DIM, textAlign = TextAlign.Center) }
                items(s.agents, key = { "a" + it.pillId }) { AgentRow(it) }
            }
        }
    }
}

/** One agent: a dot in its colour, its name and its state word. Not a button: the phone has the details. */
@Composable
private fun AgentRow(a: WatchAgent) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(ROW).padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(a.argb)))
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column {
            Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.button)
            Text(a.word, maxLines = 1, overflow = TextOverflow.Ellipsis, color = DIM, style = MaterialTheme.typography.caption2)
        }
    }
}

@Composable
private fun RequestScreen(received: Received?, fingerprint: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    // Sending, then whether the phone got it. Set once: a second tap sends nothing.
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    if (sending) {
        val r = result
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            if (r != null) Text(stringResource(if (r) R.string.sent else R.string.not_sent), textAlign = TextAlign.Center, color = if (r) GREEN else RED)
        }
        if (r != null) LaunchedEffect(Unit) { delay(1200); onDone() }
        return
    }
    val request = received?.state?.requests?.firstOrNull { it.fingerprint == fingerprint }
    if (request == null || !AllowRule.isFresh(received.atMs, SystemClock.elapsedRealtime())) {
        LaunchedEffect(Unit) { onDone() } // decided elsewhere or expired
        return
    }
    val shownAt = remember { SystemClock.uptimeMillis() }
    val secure = remember { context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true }
    val offer = AllowRule.offer(request, received.state.mayAllow, secure)
    val argb = received.state.agents.firstOrNull { it.pillId == request.pillId }?.argb
    val send = { allow: Boolean ->
        if (!sending) {
            sending = true
            PhoneLink.decide(context, request.fingerprint, allow) { ok -> result = ok }
        }
    }
    val list = rememberScalingLazyListState()
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = list) }) {
        ScalingLazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item { MochiFace(BotState.APPROVAL, argb, Modifier.size(52.dp)) }
            item { Text(request.agent, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            item { Text(request.label, textAlign = TextAlign.Center, style = MaterialTheme.typography.body2, modifier = Modifier.padding(horizontal = 8.dp)) }
            item {
                when (offer) {
                    AllowOffer.HOLD -> HoldToAllow(shownAt) { send(true) }
                    else -> Text(
                        stringResource(when (offer) {
                            AllowOffer.OFF_ON_PHONE -> R.string.allow_off
                            AllowOffer.NO_LOCK -> R.string.allow_no_lock
                            else -> R.string.allow_on_computer
                        }),
                        color = DIM, style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            item { DenyChip(shownAt) { send(false) } }
        }
    }
}

@Composable
private fun DenyChip(shownAt: Long, onDeny: () -> Unit) {
    Chip(
        onClick = { if (!AllowRule.isEarly(shownAt, SystemClock.uptimeMillis())) onDeny() },
        label = { Text(stringResource(R.string.deny), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Allow is never a tap: the bar fills while the finger stays down ([AllowRule.HOLD_MS]) and lifting it early cancels. A press in the first
 * [AllowRule.EARLY_TAP_MS] is ignored. With TalkBack, the long-press action does the same.
 */
@Composable
private fun HoldToAllow(shownAt: Long, onAllow: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.hold_to_allow)
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(GREEN.copy(alpha = 0.25f))
            .semantics {
                contentDescription = label
                role = Role.Button
                onLongClick(label) { onAllow(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (AllowRule.isEarly(shownAt, SystemClock.uptimeMillis())) return@detectTapGestures
                    val fill = scope.launch {
                        progress.animateTo(1f, tween(AllowRule.HOLD_MS, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAllow()
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) {
                        fill.cancel()
                        scope.launch { progress.animateTo(0f, tween(150)) }
                    }
                })
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(GREEN))
        Text(label, Modifier.align(Alignment.Center), color = Color.White, style = MaterialTheme.typography.button)
    }
}
