# Working in this repository

## What this is

An Android weight tracker built to a written specification and a clickable
prototype (Claude Design project `f0f93347-4984-4731-bc2b-b50e1d129098`, files
`Weight Tracker Spec.dc.html` and `Weight Tracker.dc.html`). The specification is
the contract; the prototype settles anything the specification leaves open —
layout, spacing, wording, and the exact behaviour of each flow.

Source comments reference specification sections by number ("section 4 rule 3").
Keep that habit: it is how a reader checks the code against the contract.

## Build and run

```
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

The toolchain is pinned deliberately: **AGP 8.13.1 with Gradle 9.1.0, Kotlin
2.2.20, KSP 2.2.20-2.0.4, Compose BOM 2025.09.01, compileSdk 36.** Newer
androidx releases (core 1.19, Compose 1.12, lifecycle 2.11, navigation 2.10)
require AGP 9.1+, which removes the Kotlin Gradle plugin in favour of AGP's
built-in Kotlin. Upgrading means moving the whole set at once, not one artifact.

## Where the rules live

`domain/PlanMath.kt` is the single source of every derived number — ahead/behind,
needed per day, progress, projection. The home screen, plan screen, widgets and
reminder all read from it, so they can never disagree. `PlanMathTest` pins the
specification's worked example (82.4 kg → 75.0 kg, day 57 of 152) to the decimal.
If a number on screen looks wrong, fix `PlanMath` and extend that test rather
than adjusting a screen.

`data/repo/WeightRepository.kt` holds the section 4 sync rules. Manual entries
win, Health Connect fills gaps, deleted days keep a tombstone.

Signs: a gain plan uses the same maths with the sign reversed (`direction`), and
"ahead" always means the good side of the plan line.

## The status colour

Section 6 lists exactly what turns amber when the user is behind: the chart line,
band, latest dot, scrub dot, all progress bars, both rings, every widget
sparkline, the percentage labels and the projected finish date. Nothing else.

`WtTheme.accent` is that status colour. `WtTheme.colors.onTrack` is the green
that section 12 also assigns to *primary actions* — chips, switches, the log
button, primary buttons. Reaching for `accent` on a control is almost always
wrong.

## Things that bit once already

- The chart must derive its geometry from the `DrawScope`'s own `size`, not from
  a state-backed measurement, or it misses its first frame.
- Gate the home screen on `state.loading`; otherwise a cold start flashes the
  day-one empty screen before the database answers.
- `ThemePrefs` mirrors the theme choice into SharedPreferences so `MainActivity`
  can paint the launch window before the first frame. Section 12 wants true
  black on AMOLED, and a white flash undoes that. Keep the mirror in step when
  the theme changes.
- The reminder receiver is not exported in release. The debug manifest exports it
  so it can be fired from `adb`; shell broadcasts cannot reach a non-exported
  receiver.

## Testing on a device

The debug build carries a fixture loader (`DebugSeedReceiver`) — see the README
for the `adb` invocations. It is debug-only and must stay that way.

Verify UI changes on a real emulator rather than from previews; every bug in the
list above was invisible in code and obvious in a screenshot.

## Configuration

`app/src/main/res/values/oauth.xml` holds the Google server client ID and the
Play product ID. Both may be empty or placeholder; the app degrades gracefully
and says so rather than failing. AdMob IDs live in the manifest and
`data/ads/AdBanner.kt` and are currently Google's public test values. Do not
commit real credentials.

## Copy

Section 12: warm but factual. Numbers do the encouraging; the app never
congratulates or scolds. Dates are ISO, the clock is 24-hour. When adding a
string, check the prototype for existing wording before inventing new wording.
