# Weight Tracker

An Android weight-tracking app: log your weight, set a goal, and watch the line
come down. Free with a single banner ad; one payment unlocks the home-screen
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
- **Offline by default.** Everything works with no account and no network;
  Android cloud backup is switched off. An optional email account adds cloud
  backup of your plan and history — uploads happen automatically while it is
  on, restore only ever by hand, and one tap clears the cloud copy for a fresh
  start.
- **A finish line.** Crossing your target weight earns the trophy screen, once
  per plan.

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
| Widgets | Glance, six providers |
| Scheduling | WorkManager for the daily sync, AlarmManager for the reminder |
| Payments | Play Billing, one non-consumable product |
| Ads | One 320×50 banner, home screen only |
| Accounts | Supabase email + password, verified with a 6-digit code, optional |
| Backup | One JSON snapshot per account under row-level security |

## Configuration you have to supply

Three integrations need IDs from your own accounts. The app builds and runs
without them — it just says so plainly rather than failing.

**`app/src/main/res/values/config.xml`**

- `billing_product_id` — the Play Console in-app product ID for the widget
  unlock. Defaults to `widgets_unlock`.
- `supabase_url` / `supabase_publishable_key` — the Supabase project behind
  accounts and backup. The publishable key is made to ship in clients;
  row-level security is what protects the data. Empty values disable accounts,
  and the app says so plainly.

The Supabase project itself is code too: `supabase/` holds the config, the
schema migrations and one edge function (`e2e-admin`, for testing only — see
`docs/production-checklist.md`). Account email is delivered for real through
SMTP configured on the project; the credentials live in `secrets/smtp.env` and
`e2e/config-push.sh` is the only sanctioned way to push config. `e2e/verify_backend.py`
proves every auth and backup flow against the live project. The suite never reads an inbox: it asks
GoTrue to mint the same verification code the user was emailed, and addresses
every test account to Resend's delivery simulator, so it sends nothing real.

**`secrets/keystore.properties`** (optional, never committed)

- `storeFile`, `storePassword`, `keyAlias`, `keyPassword` — the release signing
  key. Without it a release build falls back to the debug key and says so; Play
  will not accept that APK.

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
data/account/ Supabase client, email auth and the backup snapshot service
data/repo/   The only door to stored data; the sync merge rules live here
data/health/ Health Connect client and the sync service
data/billing/ Play Billing for the one-time unlock
data/ads/    The single banner
ui/          Compose screens, the chart, and the design tokens of section 12
widget/      The Glance widgets and the bitmap painter for rings and sparklines
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

The seeded series runs from 2026-07-01 to the device's current date, ending on
the specification's sample numbers (79.2 kg today, or 80.3 behind).

## End-to-end tests

Twenty-three scenarios drive the real UI on an emulator against the real Supabase
project — accounts (sign-up with the emailed code, login, password reset and
change, email change, deletion), the backup round trip, Health Connect in both
directions, manual logging, widget placement (in both status colours), deleting all data,
and the plan verdicts including the trophy screen — plus the paths people hit by
accident: a wrong password, a wrong code, a resend, and signing up with an
address that already has an account. Each scenario captures screenshots as it
goes, and every account it creates is deleted afterwards even if it fails.

```
python e2e/run.py                   # build, install, run everything
python e2e/run.py --skip-build      # reuse the installed APKs
python e2e/run.py backup restore    # a subset
```

Needs a running emulator, and `secrets/` populated (see `e2e/supa.py`) so the
runner can hand the admin secret to the tests at run time — it is never baked
into an APK. The report with screenshots lands at `e2e/report/report.html`;
the committed one is from the latest full run. `e2e/verify_backend.py` checks
the server contract alone, with no device involved.

## Website

`website/` is the product site, published by `.github/workflows/pages.yml` to
https://idct.tech/weight-tracker/ as a GitHub Pages project site under the org's
apex domain (Settings → Pages → Source: GitHub Actions, custom domain left
blank). It is a Jekyll site: `_layouts/default.html` is the one layout, the
landing page is `index.html`, and the privacy policy, terms, account-deletion
page and contact form each live in their own folder. Screenshots under
`assets/img/shots/` were taken on the emulator from the debug fixture; the icon
set and the Play Store assets in `assets/store/` are derived from
`assets/icon.png`.

```
cd website && bundle install && bundle exec jekyll serve   # http://127.0.0.1:4000/weight-tracker/
```

The workflow builds on pull requests too and fails on anything that would be a
silent problem live: a missing legal page, a canonical or sitemap mismatch, a
dead in-page anchor, an undeclared third-party host, a cleartext URL, or an
em-dash (house style for the site is plain English without them).

## Before a public release

`docs/production-checklist.md` lists what is configured for development
convenience and has to change first — the testing-only edge functions, real SMTP
in place of the captured-mail hook, a signing key, and Play's account-deletion
and privacy requirements.

## Still open

The five questions in section 14 of the specification are unresolved and the
code takes no position on them beyond what is documented in `CHANGELOG.md`.
