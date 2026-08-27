# Weight Tracker

An Android weight-tracking app: log your weight, set a goal, and watch the line
come down. Free with a single banner ad; one payment unlocks five home-screen
widgets and removes the ad permanently.

Built to the *Weight Tracker — build specification* (Rev 2026-08-27) and its
clickable prototype. Section numbers in the source comments refer to that
document.

## What it does

- **A chart worth opening the app for.** Five layers in order — gridlines,
  tolerance band, dashed plan line, dotted trend projection, and your actual
  weights — with drag-to-scrub, tap, and pinch-to-zoom (1× to 8×).
- **One plan at a time**, fixed by a date, by a weekly pace, or open-ended.
- **Health Connect sync** that fills gaps only: your manual entries always win,
  and a deleted day is never re-imported.
- **Six widgets**: 2×2 ring, 4×2 bar, 4×2 chart, 4×4 chart + stats, and the
  lock-screen glance in a wide (4×1, with a progress bar) and a compact (2×1) width.
- **A daily reminder** carrying real numbers, with inline logging.
- **Offline by default.** Every feature except backup works with no account and
  no network.

## Building

Nothing beyond a JDK 17 and the Android SDK is needed; the Gradle wrapper
fetches the rest.

```
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:installDebug           # build and install
./gradlew :app:testDebugUnitTest      # plan maths and unit conversion
./gradlew :app:assembleRelease        # minified, shrunk release APK
```

Point `local.properties` at your SDK if `ANDROID_HOME` is not set:

```
sdk.dir=/path/to/android-sdk
```

| | |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 with flat styling |
| Min / target SDK | 26 / 36 |
| Storage | Room, one local database, always the source of truth |
| Health data | Health Connect client, `WeightRecord` read and write |
| Widgets | Glance, five providers |
| Scheduling | WorkManager for the daily sync, AlarmManager for the reminder |
| Payments | Play Billing, one non-consumable product |
| Ads | One 320×50 banner, home screen only |
| Sign-in | Credential Manager with Google, optional and skippable |

## Configuration you have to supply

Three integrations need IDs from your own accounts. The app builds and runs
without them — it just says so plainly rather than failing.

**`app/src/main/res/values/oauth.xml`**

- `google_server_client_id` — the Google Cloud OAuth 2.0 **Web application**
  client ID. Empty by default, which disables Google sign-in; the rest of the
  app is unaffected, since sign-in only adds backup.
- `billing_product_id` — the Play Console in-app product ID for the widget
  unlock. Defaults to `widgets_unlock`.

**`app/src/main/AndroidManifest.xml`**

- `com.google.android.gms.ads.APPLICATION_ID` — currently Google's public test
  app ID.

**`data/ads/AdBanner.kt`**

- `Ads.BANNER_UNIT_ID` — currently Google's public test banner unit. Swap it and
  the manifest app ID together.

## Layout

```
domain/      Plan maths, units, models — no Android dependencies, fully tested
data/db/     Room entities and DAOs: entry, plan, settings, entitlement, tombstone
data/repo/   The only door to stored data; the sync merge rules live here
data/health/ Health Connect client and the sync service
data/billing/ Play Billing for the one-time unlock
data/ads/    The single banner
ui/          Compose screens, the chart, and the design tokens of section 12
widget/      Five Glance widgets and the bitmap painter for rings and sparklines
work/        The daily sync worker and the reminder alarm
```

## Testing fixtures (debug builds only)

The debug build carries a fixture loader so the screens can be exercised without
typing in two months of weights. It is never compiled into a release build.

```bash
PKG=tech.idct.weighttracker.debug
CMP=$PKG/tech.idct.weighttracker.debug.DebugSeedReceiver

# The worked example from section 5: 82.4 kg on 2026-07-01 → 75.0 by 2026-11-30
adb shell am broadcast -a tech.idct.weighttracker.debug.SEED -n $CMP

adb shell am broadcast -a tech.idct.weighttracker.debug.SEED -n $CMP --ez behind true
adb shell am broadcast -a tech.idct.weighttracker.debug.SEED -n $CMP --ez unlock true
adb shell am broadcast -a tech.idct.weighttracker.debug.SEED -n $CMP --ez clear true

# Write records into Health Connect, then clear those days locally, so a sync
# has to fill them back in
adb shell am broadcast -a tech.idct.weighttracker.debug.SEED -n $CMP --ez hcwrite true

# Fire the daily reminder without waiting for the alarm
adb shell am broadcast -a tech.idct.weighttracker.SHOW_REMINDER \
  -n $PKG/tech.idct.weighttracker.work.ReminderReceiver
```

The seeded dates assume the device clock reads 2026-08-27, which is day 57 of
the specification's sample plan.

## Still open

The five questions in section 14 of the specification are unresolved and the
code takes no position on them beyond what is documented in `CHANGELOG.md`.
