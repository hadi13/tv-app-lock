<p><b>English</b> · <a href="ARCHITECTURE.ar.md">العربية</a></p>

# How the app works inside

## Overview

The app needs no root and no Device Owner. Everything is built on an **Accessibility Service** (`AccessibilityService`), because it is the only official way for a normal app to:

1. Know which app is on screen right now.
2. Open a screen on top of any app without background-launch restrictions.
3. Capture the screen (`takeScreenshot`, Android 11+) without a consent prompt every time.
4. Show a banner over everything (`TYPE_ACCESSIBILITY_OVERLAY`) without the "display over other apps" permission.
5. Read on-screen text (the channel name on Live TV).

```
┌──────────────┐   event: new window    ┌───────────────┐
│ any app      │ ─────────────────────▶ │ LockService   │
└──────────────┘                        │  • Rules      │──▶ LockActivity (PIN screen)
                                        │  • Usage      │──▶ countdown banner
                ┌───────────────┐       │  • Events     │──▶ ntfy.sh (optional)
  phone ──HTTP─▶│ WebServer:8787│◀─────▶│  • screenshot │
                └───────────────┘       └───────────────┘
```

## Lock flow

1. `LockService.onAccessibilityEvent` receives `TYPE_WINDOW_STATE_CHANGED` with the package name.
2. It ignores the keyboard, `systemui` and our own app.
3. `Rules.blocked(pkg)`:
   - Package can't be locked (home launcher, our app) → no.
   - A PIN unlock is active (`Store.isUnlocked`) → no. **A PIN unlock overrides every rule.**
   - PIN-locked, or bedtime, or daily limit reached → yes.
4. If blocked: open `LockActivity` (`singleInstance`, excluded from recents).
5. After a correct PIN and a duration, `Store.unlock(pkg, until)` and return to the app.

### Unlock states (`until`)

| Value | Meaning |
|---|---|
| `0` | Locked |
| time in ms | Open until then |
| `-1` (`FOREVER`) | Open until locked manually |
| `-2` (`ONCE`) | Open until the foreground app changes (`relockOnceExcept`); cleared on restart |

## Every 10 seconds (`tick`)

- `flushUsage()`: adds time to the foreground app (only while the screen is on, max 2 minutes per batch so sleep isn't counted).
- **Bedtime start:** `relockAll()` once (flag `bed_applied`) + alert.
- `check()`: if an unlock expired or the daily limit ran out → lock screen.
- `countdown()`: `Rules.timeLeft` = the nearest of (unlock end, remaining daily limit, bedtime start). At 5 minutes and 1 minute a yellow banner shows for 15 seconds.
- Log the channel if it stayed the same for a minute.

## Home screen (`HomeActivity`)

An activity with `MAIN` + `HOME` + `DEFAULT`. `windows/6-set-home.bat` makes it the default with `cmd package set-home-activity` and disables the stock launchers and the Google TV setup wizard (`com.google.android.tungsten.setupwraith`), which claims Home with a higher priority than any third-party launcher. `7-restore-google-home.bat` re-enables them.

- Language and direction come from `Locale.getDefault()`: Arabic text and RTL, or English and LTR.
- "Most used" = the top 3 apps by watch time over 7 days (at least 1 minute); the rest go in a 5-column grid. Apps in `Store.hidden` are left out.
- Tiles use the app's TV banner (`getApplicationBanner` / `getActivityBanner`) with `CENTER_CROP`; without one, the icon sits on a dark tint computed from the icon's own colors.
- Focus: scale + elevation + the name fading in under the tile. Parents have `clipChildren`/`clipToPadding` off so a lifted tile is never cut.
- When our home is in front, `LockService` sets `current` to our package (detected by the event's class name) so a previous blocked app isn't shown again, and "this time only" unlocks are relocked.

## Usage log

`SharedPreferences` named `tvlock_usage`, keys `yyyyMMdd|package` → milliseconds. Entries older than 14 days are removed. Screen off (`ACTION_SCREEN_OFF`) pauses counting.

## Screen capture

`takeScreenshot` returns a `HardwareBuffer`. We convert it to a normal bitmap three ways and use the first that isn't black (some TVs return black with one method), remembering the one that worked:

1. `Bitmap.wrapHardwareBuffer(colorSpace).copy(ARGB_8888)`
2. Same with explicit `sRGB`
3. GPU draw into an `ImageReader` and read the pixels back

The image is scaled to 960 px wide and compressed as JPEG quality 65; the minimum gap between captures is 700 ms (the system limit is 333 ms).

**Why is Live TV black?** Tuner video is shown on a separate hardware layer (sideband/overlay) that doesn't go through SurfaceFlinger, so no software capture (not even `adb screencap`) can see it.

## Channel name

With `canRetrieveWindowContent` and `flagRetrieveInteractiveWindows`, on every `TYPE_WINDOW_CONTENT_CHANGED` from a channels package (`livetv`, `tvinput`, `tuner`...) we walk every window and collect visible text (up to 300 nodes). Selection:

1. View id containing `channel` + `num/no` → channel number.
2. View id containing `channel` and non-numeric text → channel name.
3. View id containing `program/event/epg` → program.
4. Fallback: first short number, first text that isn't a time.

The raw text is available at `/debug/info` (`live dump`) so the reader can be tuned for any TV.

## Saved settings (`Store`)

| Key | Description |
|---|---|
| `pin_hash` | SHA-256 of the PIN with a fixed salt |
| `locked` | Set of PIN-locked packages |
| `until_<pkg>` | Unlock state |
| `limit_<pkg>` | Daily limit in minutes |
| `bed_on/bed_start/bed_end` | Bedtime (minutes after midnight) |
| `web_on` / `web_token` | Phone panel and session token (renewed when the PIN changes) |
| `ntfy_on` / `ntfy_topic` | ntfy notifications |
| `hidden` | Packages hidden from the home screen |

## Why `targetSdkVersion 29`?

It avoids newer platform restrictions on starting activities and services, and doesn't need `QUERY_ALL_PACKAGES` to list apps. The app is compiled against API 34 to use `takeScreenshot` and friends, checking `Build.VERSION.SDK_INT` before any newer API.

## Building without Gradle

`aapt2` (resources) → `javac --release 8` → `dx` (dex) → `zipalign` → `jarsigner` (v1) → `scripts/v2sign.py` (APK Signature Scheme v2, written in Python). The code is Java 8 without lambdas so it works with `dx`.
