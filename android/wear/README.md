# Coucou for Wear OS (Galaxy Watch)

The watch side of [Coucou for Android](../README.md): Mochi on your wrist, your agents with their state, and Allow or Deny for a
permission request. Built for the Samsung Galaxy Watch 7 (Wear OS 5) and any watch with Wear OS 3 or later.

It is a companion, not a standalone app: it needs Coucou for Android on the phone the watch is paired with, and that phone paired with
Coucou on Windows or Linux. The watch never talks to the computer and has no network of its own.

## What it does

- **App**: Mochi in the state of your most urgent agent (in that agent's colour), the phone's headline, the requests waiting for you
  (orange) and every agent with its state word. Swipe right to go back.
- **Request**: the agent and what it wants to do in words ("Running a command · npm"), never the command. **Deny** is a tap. **Allow** is
  *press and hold* for one second (the bar fills; lifting early cancels), and only when all three are true:
  1. on the phone, Settings > Permission requests > **Allow from my watch** is on (off by default);
  2. the watch has a screen lock (a Galaxy Watch locks itself when you take it off);
  3. the computer sent the whole command (a cut command is allowed on the computer, as on the phone).

  Otherwise the screen says which one is missing. A press in the first 600 ms is ignored, and a request is not offered more than 120 s after
  the watch heard of it. The phone then checks the decision like one of its own taps (`AppModel.decide`).
- **Tile** (swipe left from the watch face): the headline, how many requests wait and the first two agents, with Open. It is redrawn only
  when the phone sends something new; it never shows a request's label.
- **Notifications**: the phone's own notifications reach the watch as before (Android bridges them); the watch app adds none.
- Languages: English and French for the watch's few own words. Everything the phone sends (headline, state words, labels) is already in the
  phone app's language.

## The phone link

Google's Wearable API (`play-services-wearable`), which only connects apps with the same application id (`com.coucou.android`) signed
with the same key. Only **messages to nearby nodes** are used (Bluetooth, or the same Wi-Fi): no Data Layer items, which Google may sync
through its cloud. The phone sends nothing at all unless a watch with Coucou is nearby. On a phone without Google Play services the link
is simply not there and the phone app is unchanged.

| Path | Direction | Body |
|---|---|---|
| `/coucou/hello` | watch → phone | empty: the watch app opened, send the state now |
| `/coucou/state` | phone → watch | `WatchState` as JSON, sent when what the Widgets show changes |
| `/coucou/decide` | watch → phone | `{"v":1,"fp":"<fingerprint>","allow":true}` |

The format is `app/src/main/kotlin/com/coucou/android/core/WatchState.kt` (tested in `WatchStateTest`); the phone builds it from the
Widgets' content (`WatchStateBuilder`) and sends it from `app/WatchBridge.kt`. Capabilities: the watch announces `coucou_watch`, the phone
`coucou_phone` (`res/values/wear.xml` in each module).

## Build and run

From `android/`:

```bash
./gradlew :wear:test :wear:assembleDebug lint
adb -s <watch> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

Mochi's drawing (`mochi/`), his easing (`core/Anim.kt`), `core/WatchState.kt` and the icon are the phone app's own files, copied into
`wear/build/generated/phone` at build time (`shareFromPhone` in `build.gradle.kts`): there is no second copy in git.

On a Galaxy Watch: Settings > Developer options > ADB debugging and Debug over Wi-Fi, then `adb connect <watch IP>:<port>`. Install the
phone app's debug build from the same machine: both debug builds are signed with that machine's debug key, which the link requires. A
release build reads the same signing values as the phone (`COUCOU_KEYSTORE_FILE` and the rest) and must be signed with the same key.
