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
- Redrawing a widget does not move it on to a new day. Glance recomposes a
  running session instead of calling `provideGlance` again, and `WidgetData.flow`
  is a combine over Room flows that emit nothing merely because it is tomorrow.
  `DayChange.today` is the fifth source that makes the day itself an event; drop
  it and the midnight refresh silently redraws yesterday.
- The size a launcher reports for a widget is a floor, not a measurement. HyperOS
  writes the provider's declared minimum into the options bundle and never
  revises it, so a 4x2 filling 274x137 dp goes on answering 250x110. Two rules
  cope with that without trusting the figure: `Cell.scale` never falls below 1,
  because every design fits the cell its provider declares as a minimum
  (`WidgetCellTest` checks that, so the floor stays safe); and anything that has
  to fill the cell is a Glance weight, never a dp computed from `LocalSize`.
- `SizeMode.Responsive` looks like the answer to that and is not. It ships a
  layout per declared rung and lets the host choose by the bounds it measured
  itself, which no launcher can get wrong — but the host picks the largest rung
  that *fits*, and a rung is never the cell. A 4x2 at 386x213 composes for a
  330x160 rung, so every bitmap is drawn for a box two thirds the height of the
  one it is put in and the ImageView stretches the difference. It reads as
  squashed axis figures, on every device, in exchange for one launcher's fault.
  `Exact` is right for the same reason: where the launcher is honest the pictures
  are drawn at the size they are put in and nothing is scaled at all.
- The widget sparkline and the home screen's chart are meant to be the same
  drawing, and drift between them is a bug. `WidgetPainter` takes its face from
  `WidgetPainter.mono(context)` — the bundled Roboto Mono, not
  `Typeface.MONOSPACE` — and keeps the app's stroke weights, dashes, grid colour
  and 9% band. Axis figures are a flat 9.5 sp in both: scaling them with the cell
  is what made the widget look like a different product, and it starves the plot
  of the room to carry its scales.
- A chart's floor is the lowest weight it draws, and that floor is its own lowest
  label. `ChartScale.axis` returns the two together because they cannot be chosen
  apart. The floor is that weight exactly, not the round number under it: rounding
  it down put a 74.5 kg goal 0.5 kg above the axis while the projection's landing
  dot — drawn at `ay + ah`, on the axis by construction — sat correctly on it, and
  two marks of the same finish on two different lines is the chart being wrong
  about its own subject. So a floor that is already round keeps the plain ladder it
  always had, and one that is not is a label in its own right with the round
  weights carrying the grid above it, the first of them dropped when it would sit
  close enough to the floor's label to smudge it. Whatever lands on that floor is a
  point — the goal ring, the dot on the lowest reading — and points are drawn under
  a clip of their own that reaches a few dp past the axis. Both charts learned that
  the same way: a clip stopping at the axis draws them as half moons. Neither `computeYDomain` nor the widget
  sparkline pads below that floor — padding there is what left the plan line
  ending in mid-air — and the tolerance band, which reaches TOLERANCE_KG lower, is
  clipped to the plot instead of being allowed to push the floor down. Both charts
  therefore clip their data layers at the plot's bottom; without it the band paints
  down into the strip the dates live in.
  The step for the floor is picked for tightness and the label budget only thins
  out which weights get printed — deriving the step from the budget instead is
  what makes a three-label widget drop 74.4 all the way to 70.
- A picture that has to match its box needs the rows around it to have imposed
  heights, not measured ones. Glance text comes out a few dp under what
  `Cell.lineH` budgets, and with the chart on `defaultWeight()` that difference
  lands entirely on the chart, which the ImageView then scales. The 4x2 and 4x4
  give every row but the chart an explicit `height()` equal to what they were
  counted as, so the weight left over is the height the bitmap was drawn at and
  the scaling is the identity. The charts are `ContentScale.Fit` rather than
  `FillBounds` for what is left: where a launcher does understate its cell, Fit
  spends the difference on a band above and below rather than on stretching the
  axis figures, and a chart with a margin still reads as a chart.
- A widget cannot ask how wide a label is, so every "does this fit across the
  row" decision is made before the layout runs — and each time one was written as
  a dp budget it was wrong twice over. It cannot see the reader's text setting
  (type is asked for in sp, which the host multiplies before laying it out, so a
  row overruns by exactly that factor at 1.3x), and a budget phrased in the
  design's own terms can be degenerate: `c.width >= c.text(190f)` is an identity
  wherever the width is what limits `Cell.scale`. `WidgetPainter.textWidthDp`
  measures the face Glance actually lays out, at the size it will really be; use
  it, and subtract `GLANCE_TEXT_SLACK` for what Glance's own wrappers take.
- The 2x1 strip is the one widget whose type size is decided by width rather than
  height: the caption sits in what the ring leaves, and a taller cell grows the
  type without growing that column.
- `e2e/widget_sizing.py` is the harness for all of this — every connected device,
  several densities, 1.0x and 1.3x text. Run it after touching a widget layout.
  A bug that appears at 420 dpi and not at 480 is invisible to any number of
  looks at one screen, and two have now been that shape.
- Bottom-bar tabs do not save and restore a back stack each. `popUpTo(start) {
  saveState = true }` with `restoreState = true` is the standard recipe and the
  wrong one here: Widgets is pushed on top of Settings, so popping to Home saved
  that pair and the next tap on Settings restored it, opening Widgets instead.
  The bar also calls `systemGestureExclusion`, or the back-swipe strip at the
  screen edge eats taps on the leftmost tab.
- The home chart opens on the road ahead — `planWindow`, from today or the last
  weigh-in, whichever is older, to the far end of the bounds — while `chartBounds`
  still reaches back to the plan's first day, so the history is a pan away rather
  than gone. The "or the last weigh-in" half is not optional: anchored on today
  alone, a reader who misses three days opens a chart with no line in it (the path
  needs two points and `entriesAround` can only find one), no latest dot, and a
  projection starting off the left edge at a weight `computeYDomain` never saw. The widget sparklines
  keep drawing the whole plan: they cannot be panned, so a window starting at today
  would leave them with no weighed-in line at all.
- A type size is judged in dp, never in the pixels it was computed from. The ring's
  caption gate read `captionPaint.textSize >= 7f` against a size derived from the
  bitmap's pixel width, so the threshold moved with the screen — kept at 3x where it
  should have been dropped, dropped at 1.5x where it should have been kept. Multiply
  the dp figure by the bitmap's own scale (`render.scale`, not the display density,
  which `Render`'s pixel budget may undercut).
- Under the axis there are two rows for dates: the landings take the first, the
  calendar fills in below them — and takes the first itself when no landing is in
  view, which is every 7d, 30d and 90d window and every undated plan. A row with no
  room left below the axis is not offered at all: the strip is a fixed share of the
  plot, the figures in it are sp, and a Canvas does not clip.
- Widgets is reached two ways and they are not the same navigation. From the Settings
  row it is a drill-down (`navigateSingle`), so back returns to Settings. From an
  intent — a widget's own tap, the paywall — it is a bar-carrying screen arriving
  from outside, so it goes through `navigateTab` like Home does: pushed instead, back
  went to whatever screen the app was last left on, and the same gallery was a
  different screen depending on how it was opened.
- Background work is two jobs, and only one of them needs a grant.
  `HealthSyncWorker` reads Health Connect every 30 minutes and is behind the
  background-sync setting; `PlanRefreshWorker` redraws every hour, needs nothing,
  and is enabled for everybody at process start, because what it refreshes is
  `PlanMath` against today's date rather than anybody's data. It re-arms
  `DayChange` as it goes, which is the only thing that repairs a missed midnight.
  Both use `ExistingPeriodicWorkPolicy.UPDATE` with an initial delay well under
  their period: the jobs are re-asserted on every process start, so a delay of a
  whole period can be pushed out for ever on a phone that is picked up often.
  Renaming a worker is not enough to retire it — unique work is addressed by name,
  so `HealthSyncWorker.enable` also cancels `daily-health-sync` by name.
- Whether a phone can read health data in the background is asked of Health
  Connect (`HealthConnectFeatures.getFeatureStatus`), never inferred from
  `SDK_INT`. The feature ships in the Health Connect module, not the platform: its
  version map puts it at API 34 with U extension 13, so an Android 14 phone with an
  updated module has it. The SDK check survives only as `backgroundReadLikely`, the
  guess used for the frame before the real answer arrives.

## Testing on a device

The debug build carries a fixture loader (`DebugSeedReceiver`) — see the README
for the `adb` invocations. It is debug-only and must stay that way.

Verify UI changes on a real emulator rather than from previews; every bug in the
list above was invisible in code and obvious in a screenshot.

Anything that depends on the date is tested by moving the emulator's clock, not
by injecting a fake one: `DeviceClock` in the E2E sources drives `cmd alarm
set-time`, which is the path Settings itself takes, so alarms really fire and
`TIME_SET` really reaches the app. `TemporalTest` seeds on 2026-08-27 — which
makes the fixture the section 5 worked example to the day — and then only
travels. The clock is always given back, in the test's `@After` and again in
`run.py`.

## Configuration

`app/src/main/res/values/config.xml` holds the Play product ID and the
Supabase URL and publishable key. Values may be empty or placeholder; the app
degrades gracefully and says so rather than failing. The Supabase project
lives in `supabase/` (schema migration, auth config, two edge functions);
`e2e/verify_backend.py` proves the backend contract, and `secrets/` (never
committed) holds the admin secret the E2E tooling uses. The real AdMob IDs live
in `secrets/admob.env` (`APP_ID` into the manifest placeholder, `AD_ID` into the
release `BuildConfig`); debug builds and checkouts without that file use
Google's public test values. Do not commit real credentials.

## Copy

Section 12: warm but factual. Numbers do the encouraging; the app never
congratulates or scolds. Dates are ISO, the clock is 24-hour. When adding a
string, check the prototype for existing wording before inventing new wording.
