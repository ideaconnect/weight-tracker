# Changelog

## 1.0 — unreleased

First build, to the *Weight Tracker — build specification* (Rev 2026-08-27).

### Assumptions this build makes

These are choices, not neutral defaults. Section 4 of the specification asks for
the first one to be recorded here; the rest are noted for the same reason.

- **Several Health Connect records on the same day: the earliest one wins.** The
  assumption is a morning weigh-in, before breakfast, which is the reading most
  people intend to track. A scale that syncs an evening reading too will have it
  discarded. Section 14 lists confirming this against real scale behaviour as
  still open.
- **A new plan pins its start to your most recent entry**, not to the beginning
  of your history. The plan line therefore begins the day you set the goal.
  Editing an existing plan never moves the start.
- **Backdated entries written to Health Connect are stamped 08:00 local**, for
  the same morning-weigh-in reason. Today's entry is stamped with the actual
  time.
- **Writing manual entries back to Health Connect defaults on** whenever the
  write permission has been granted, which section 14 lists as still open.
- **Background sync is in the free tier**, not behind the widget unlock, which
  section 14 also lists as still open.
- **A trend projection more than ten years out is hidden** rather than shown, on
  top of the specification's rule that the trend must run in the right direction
  and have at least two entries behind it.

### Since then

- Google sign-in is gone. Accounts are email + password on Supabase, verified
  with a 6-digit emailed code, with password reset, password change, email
  change and self-serve account deletion.
- Cloud backup exists behind the account: automatic uploads of entries, plan
  and deleted-day tombstones while the switch is on; restore and
  clear-the-cloud-copy only ever by hand. "Delete all data" signs out first so
  the wipe can never auto-upload an empty snapshot over a good backup.
- Reaching the target weight shows the trophy screen — the one moment the app
  congratulates, once per plan.
- Sixteen end-to-end scenarios drive the real UI against the real backend —
  the whole account lifecycle, the backup round trip, Health Connect both
  ways, widget placement, and the plan verdicts — with a screenshot report
  committed under e2e/report/. The suite found and fixed a real bug: a
  successful email change left the user stranded on the code panel.
- The widget gallery no longer offers a mock home screen. Section 7's
  placement preview drew a fake launcher — wallpaper, blank icons, a drop
  zone — so a widget could be seen in place without leaving the app; the
  real home screen is one tap away and shows the real thing. Adding a
  widget from inside the app still works, from the tile's own dialog, and
  now comes back to the gallery instead of to the mock.

### Fixed on a Xiaomi 15 Ultra

Five faults reported from a real phone on a five-column, nine-row home screen.

- **The widgets stop believing the launcher about their own size.** HyperOS
  writes a widget's declared minimum into its options and never revises it, so
  a 4x2 occupying 274x137 dp went on calling itself 250x110 for as long as it
  was placed. The old fit read that figure in both directions, which is the
  single cause of all three symptoms at once: faint small type, a chart using
  half its tile, and a margin above and below the 4x2. The design is now never
  drawn smaller than itself whatever it is told — every widget fits the cell its
  provider declares as a minimum, so a report can never be a reason to shrink —
  and the parts with give, the chart and the ring and the progress bar, fill the
  cell with Glance weights instead of arithmetic over a figure that may be a
  fiction. The progress bar was measured against the reported width too, so a
  finished plan drew a bar that stopped short of the end. `SizeMode.Responsive`
  was tried and reverted: letting the host choose between declared rungs does
  defeat the bad report, but the rung it picks is never the cell, so every
  bitmap ends up stretched to the difference on every device. Instead the rows
  around each chart are given the heights they were counted as, so the picture
  is drawn at exactly the box it is put in, and what scaling is left over is
  uniform rather than a stretch.
- **Both charts always draw both axes, and the lowest weight sits on the X
  axis.** Gridlines and labels without a corner to hang on are a set of
  free-floating rules; §6 puts the weights in a left gutter and the dates
  underneath, and now there is an axis under each of them, on the home screen and
  in every widget that carries a chart. And the plot's floor is now the lowest
  weight it actually draws — the goal, on a loss plan — so the plan line ends on
  the axis and the goal's own figure is the bottom label, instead of both
  hovering over a strip of empty plot. The floor used to carry 0.8 kg of padding
  under it, and the tolerance band reaches half a kilogram lower still; the
  padding is gone and the band is clipped at the floor rather than allowed to
  push it down. `ChartScale.axis` hands back that floor together with the ticks,
  because the two cannot be chosen apart: pick the ticks first and the lowest
  floats above the axis, pick the floor first and it is not a round weight.
- **The widget's chart is the home screen's chart.** Side by side the two were
  plainly different drawings, and most of it was one line: the widget set its
  figures in `Typeface.MONOSPACE`, whatever the platform happens to call
  monospace, while the app sets them in the bundled Roboto Mono. They now share
  the face, the hairline gridlines in the fainter grid grey rather than the
  outline colour, every stroke weight and dash, the 9% band, and the vertical
  hairline on today that tells the drawn half of the plan from the rest. Axis
  figures also stop scaling with the cell — the app sets them at a flat 9.5 sp
  however big the chart is, and type that tracked the box made a widget's scales
  three times the weight of the same scales in the app. Holding them still also
  buys back the room to carry as many figures: the number of weights and dates is
  now read off the plot, the way the app reads it, so a 4x4 that had three of each
  now carries five.
- **Both lines are dated where they reach the goal.** With the goal on the axis,
  the plan meets it on the target date and the trend on the date the weights are
  actually heading for, and each of those crossings now carries its date on the
  axis, in its own line's colour. The projection also gets a dot there; the plan's
  crossing already had the hollow goal ring. The two are a fortnight apart on the
  sample plan and their labels are wider than that, so the second drops to a row
  of its own — but only where the plot can spare the height, which a 4x2 on a
  dense grid cannot. There the projection keeps the row, because a dot on the axis
  with no date beside it explains nothing, while the goal ring explains itself.
- **The widget charts show the whole plan, and read like the home screen's.**
  Same layers in the same order — gridlines, tolerance band, plan line, trend
  projection, the weights and a point per weigh-in — from the day the plan
  started to its target date, with the goal marked where the plan reaches it
  and calendar-aligned dates on a real axis.
- **The trend projection is blue.** Section 6 spends amber on one thing only,
  so the projection could not borrow it; drawn in a second grey it was
  indistinguishable from the dashed plan line it exists to be compared with.
  Chart and widgets both, legend included.
- **Account email carried a link to localhost and no code.** The project was
  still on GoTrue's stock templates, which print `{{ .ConfirmationURL }}` and
  nothing else, built out of a `site_url` that was still the CLI's
  `http://127.0.0.1:3000` placeholder — while the app waited on the code panel
  for six digits that were never sent. Sign-up, password reset and email change
  now have templates of their own that print the code, and
  `e2e/verify_backend.py` checks the mail as well as the flow: minting a code
  was proved, posting one was not.
- **The Home tab is reachable again.** The bar reaches both edges of the
  screen, and on gesture navigation the outer strip of each edge belongs to the
  system's back swipe, so a tap on the leftmost tab was swallowed and came back
  as a back gesture. The bar now claims its own area with
  `systemGestureExclusion`. Separately, the tabs were saving and restoring a
  back stack each, so the Settings tab reopened the Widgets screen that had
  last been on top of it.

### Fixed after a blind-spot review (issues #14–#34)

- **Backup can no longer be clobbered.** An upload that would replace a row this
  device did not write raises a conflict and asks which copy to keep, so signing
  in on a new phone and flipping the switch can no longer overwrite the old
  phone's history with an empty one. Restore runs in a single transaction, off
  the screen's own coroutine scope, and refuses a payload it cannot fully read
  rather than silently restoring part of it.
- **A session that dies on its own is noticed.** Settings used to keep saying
  "backup on" indefinitely after a revoked token stopped every upload.
- **Signing up with an address that already has an account says so**, instead of
  promising a code the server was never going to send.
- **Changing the password signs other devices out.**
- Verification panels survive process death; auth errors are the app's own words
  rather than raw server text; the code field accepts a paste and every field is
  labelled for TalkBack.
- The reminder notification no longer outlives the setting that posted it, and
  can no longer write into a wiped database.
- Gain plans read forwards in History and on the Plan screen.
- The trophy no longer replays after restoring a finished plan.
- The debug fixture is anchored to the plan line rather than to fixed dates, so
  it cannot drift into the wrong status as the calendar moves.
- The backup payload actually carries its version number. Serialization omits a
  field equal to its default, so the compatibility guard had nothing to read.
- The E2E suite grew to 23 scenarios — the error paths, deleting all data, both
  lock-screen widgets, the backup conflict and the once-only trophy — and cleans
  up every account it creates even when a scenario fails.

### Real account email

- **Account email is delivered for real.** Verification, password reset and
  email change all send through SMTP configured on the project. The capture hook
  that stood in for it is gone — disabled, deleted, and its `auth_mail` table
  dropped, so live codes are no longer stored anywhere.
- The E2E suite stopped needing an inbox: it asks GoTrue to mint the same code,
  which sends nothing, and addresses every test account to the mail provider's
  delivery simulator so a run generates no real mail and no bounces.
- `supabase/config.toml` no longer carries SMTP credentials. It held a
  placeholder host, which meant any `config push` silently replaced the
  project's working SMTP settings with a dead one; the values now come from
  `secrets/smtp.env` and `e2e/config-push.sh` refuses to push without them.

### The daily reminder, reviewed

Thirty-eight findings from an adversarial review of section 9, of which these
changed behaviour:

- **`USE_EXACT_ALARM` is gone.** Google Play allows it only to alarm-clock,
  timer and calendar apps; a weight tracker declaring it is refused. The app
  keeps `SCHEDULE_EXACT_ALARM`, which Android 14+ denies by default: the
  reminder then arrives within the hour after the chosen time, the Reminder
  screen says so and offers the "Alarms & reminders" toggle, and the alarm is
  re-armed exact the moment the grant arrives.
- **Snooze works.** It shared the daily alarm's PendingIntent, so any process
  start — a widget refresh, the sync job, opening the app — replaced a pending
  snooze with tomorrow's alarm. It has its own now.
- **The daily chain is armed before the broadcast finishes**, and before the
  notification is posted. It used to be launched fire-and-forget after
  `PendingResult.finish()`, from a scope whose first exception would have killed
  the process from the background — with no alarm for tomorrow.
- **A missed reminder is delivered late rather than dropped.** An inexact alarm
  still pending at 08:20, or one the phone slept through, was moved to tomorrow
  by the next process start. Within two hours of its time it now fires at once —
  across midnight too, so a 23:30 reminder missed by an hour still arrives.
- **One `MainActivity`.** A notification tap while the app was in the background
  stacked a second instance — a second view model, billing client and session —
  on top of the first; `onNewIntent` was dead code. The activity is `singleTop`
  and the intent says so. The route is also no longer replayed on every
  recreation, so the log sheet a reminder opened does not come back after a
  dark-mode flip or a return from Recents.
- **"On" means on.** With notifications blocked in system settings the switch
  stayed on for good while every morning's alarm woke the process to post
  nothing. The Reminder screen and the Settings row now say "blocked" and open
  the settings page.
- **The body is true today.** "Yesterday you were …" was said of an entry days
  old; "0.0 kg ahead" was printed before the schedule had begun; an entry
  already logged today was ignored. The wording comes from `Format.reminderBody`
  now, shared with the preview and pinned by unit tests, and once today is
  logged the inline field is dropped so it cannot replace the day by accident.
- **The inline reply is honest both ways.** A refused number says so and keeps
  the field; a saved one is confirmed in place ("Logged 80.1 kg"), which is also
  what stops the shade's sending spinner. The parser accepts a comma, an Arabic
  separator and any digit script, and refuses anything else instead of
  repairing it ("7٫9" used to become 79).
- **An inline log is a full log**: it is written back to Health Connect like the
  sheet's, and uploaded to the backup by a one-off job rather than at the next
  app open.
- Logging in the app dismisses a reminder still in the shade; changing the unit
  or the quick-log switch rebuilds it; the background sync rebuilds it with the
  merged numbers; the reminder syncs Health Connect, briefly, before it posts.
- Also: the switch component has switch semantics for TalkBack and tests; the
  big time no longer mirrors under RTL locales and its quick options wrap at
  large font sizes; `updateSettings` is transactional; the theme mirror follows
  Delete-all-data; the boot receiver no longer re-enqueues the sync job against
  the setting.
- Three E2E scenarios cover the screen, the notification with its inline reply
  and snooze, and the deep link; three unit-test files pin the body wording,
  the parser and the scheduling decision.

### The tracking algorithm and the charts, reviewed

- **Widgets move on to the new day.** Every derived number hangs off today's
  date — the plan asks for less each morning — yet nothing refreshed a widget at
  midnight, so a placed widget kept "0.4 kg ahead" in green for as long as the
  app stayed closed. An inexact, non-waking alarm a minute past local midnight
  now redraws them and any reminder still in the shade; a clock or time-zone
  change does the same. In the app, "today" is its own flow that re-emits after
  midnight, so a screen left open overnight no longer keeps yesterday's target.
- **"At current pace" is the pace since the plan began.** The trend was fitted
  through the whole history, so a year of pre-plan gain either hid the
  projection or diluted two months of loss into a finish date years away. The
  §5 worked example measures the plan period, and so does the code now.
- A Health Connect record for the plan's start day re-pins the start weight the
  way a manual entry on that day does.
- **Every Health Connect sync reads a full year** (further back still when the
  plan is older). After the first sync the window used to be a fortnight, so
  a record that arrived late or was corrected in another app more than two
  weeks back never landed; and the first sync with a plan in place started at
  the plan rather than importing the history a scale already held.
- **The chart has a real scale.** Weight gridlines sit on round numbers in the
  display unit (74, 76, 78 … or 170, 175, 180 lb) and the dates underneath fall
  on calendar boundaries — days, Mondays, month starts — chosen to fit the
  width; the old labels were whatever value a quarter of the range happened to
  be. Labels before the plan's start are real dates rather than the start date
  repeated.
- **Pinch zooms about the fingers, and two fingers pan.** Zoom was anchored to
  today and there was no way to reach earlier data once zoomed in. The window
  is bounded by the plan and the history, the range chips reset it, and the
  hint says so. The data layers are clipped to the plot, which they used to
  overrun into the gutter when zoomed; readings get their own dots once the
  window is narrow enough to tell them apart; the scrubber snaps only to
  readings in view; the plan line continues flat at the target past the target
  date, as the maths treats it. The chart describes its visible date range for
  accessibility, and the E2E scenario reads the gestures back from it.
- **Widget sparklines share the chart's scale**, with the same round weights and
  calendar dates, and the in-app previews are painted by the widget painter
  itself rather than by a Compose copy of it that had drifted.

### Widgets fit the cell they are given

- **A denser home-screen grid no longer shrinks a widget's contents into the
  corner of it.** The fit was one-way: it grew a design into a cell larger than
  the prototype's, but its scale was floored at the design size and read height
  alone, so a cell *smaller* than the design got the design drawn at full size
  and the launcher clipped whatever hung over. On a five-column, nine-row grid
  that is most of them. The 4x2 chart lost the date axis under its plot, the
  2x2's ring lost its top and bottom, and at the very sizes the two lock-screen
  widgets declare in `res/xml` as their own minimum the type was cut through the
  middle. The fit is now symmetric and two-dimensional, with nothing floored at
  the design size: above the design size it damps exactly as before, and below
  it type tracks the cell one for one down to a 9 sp legibility floor, at which
  point §12 drops a line rather than print one too small to read.
- **The inset follows the cell.** A constant 14 dp all round is right for a cell
  of the prototype's size and takes a third of a nine-row grid's 4x1 before
  anything is drawn. Both insets still saturate at 14 dp, so a cell of the size
  the screenshots were taken at is untouched.
- **Every remainder is a remainder.** `coerceAtLeast(72f)` on a chart height is
  a floor *above* the space left, so it did not make room, it pushed the axis
  off the bottom. The 4x2 and the 4x4 now drop the energy line to find the room
  and then take what is left; the 2x2's ring and both lock-screen rings are
  sized against what their labels leave rather than against a constant.
- **Type budgets count the reader's text size.** A column measured in bare sp
  against a dp box overruns by exactly the system font scale, which is the same
  clipping arriving by a different door. `Cell.lineH` now counts it, and the ring
  captions that are sized to fit a dp box ask for it in sp rather than declaring
  a dp quantity as sp. At the default setting every number is what it was.
- **Rows count the reader's text size too.** `Cell.lineH` fixed that for columns;
  the same bug was still standing in every decision about whether a row of labels
  fits. Those were em counts written as dp — `c.width >= c.text(190f)` on the 4x2,
  `subSp * 13f + weightSp * 2.8f` on the 4x1 — and type is asked for in sp, which
  the host multiplies by the font scale before laying it out, so at 1.3x a row
  overran its budget by exactly that factor. The 4x2's header ran "79.2 kg", the
  energy figure and the percentage together into one word, and the 2x1's
  "43% of plan" was ellipsised on the strips that had the most room for it.
  `WidgetPainter.textWidthDp` measures the face Glance lays out, at the size it
  will really be, and the three budgets now ask it instead of estimating. The 4x2's
  old test was degenerate as well as blind: where the width is what limits the
  scale, `c.text(190f)` inverts back to exactly `c.width`, so it answered yes at
  the one size with nothing to spare.
- **The 2x1's type is sized by its width, not its height.** It is the only widget
  where the two disagree: the caption sits in whatever the ring leaves it, and a
  taller strip grows the type without growing that column. Whether the caption
  survived came down to how the density happened to round it — the same 110x40 dp
  cell fitted "43% of plan" at 480 dpi and cut it at 420.
- Verified by rendering all six widgets at four cell sizes each, at the default
  text size and at 1.3x: `WidgetSizingTest` composes each widget at an explicit
  dp cell and writes a PNG, which is the whole of what a grid change does to a
  composition without needing a launcher that offers the grid. Every cell the
  repo's screenshots were taken at renders byte-identically to before the fix.
  `WidgetCellTest` pins the arithmetic under it, and `SeedData.resizeWidgets`
  (`--ei cellw --ei cellh`) drives the real `APPWIDGET_UPDATE_OPTIONS` path on a
  device.
- `e2e/widget_sizing.py` runs that test across a matrix instead of once: every
  connected device, at several densities and text sizes, 144 cells in a few
  minutes, with the magenta each cell left showing measured rather than eyeballed
  and the lot written to a contact sheet. Density is the variable worth moving —
  dp become pixels at the display's density, so it decides how large every bitmap
  the widgets draw actually is, and the pixel budget only bites at 3.5x. Both row
  bugs above were found by it and by nothing else: each appeared at some densities
  and text sizes and not others, on cells that had been rendered and looked at
  many times at one.

### Time itself is now under test

Every scenario used to see a single day, so it could only ever check the
arithmetic once — and almost nothing this app says is a function of the data
alone. Three scenarios now move the emulator's own clock (`cmd alarm set-time`,
which needs no root) and then only watch: no extra fixtures, no day-change
broadcast, no reminder posted by hand. The fixture is pinned to the section 5
worked example rather than to "today", so the plan really does start on
2026-07-01 at 82.4 kg and aim at 75.0 kg by 2026-11-30.

- **`temporal-plan`** walks day 57, 64, 71 and 92 without logging anything. The
  same 79.2 kg reads 0.4 kg ahead, 0.1 ahead, 0.3 behind and 1.3 behind; "lost
  so far" and "left to go" stay frozen while the rate the plan asks for climbs
  from 0.04 to 0.07 kg a day and the projected finish slides from 2026-11-10 to
  2026-12-15, past the deadline. The clock moves while the app is in the
  background, so it also proves the day is re-read on resume. A weigh-in on the
  last day puts it back on the good side.
- **`temporal-widgets`** places the ring and the bar on day 57 and then only
  moves the clock. Both turn amber for day 71 by themselves, with the launcher's
  own pixels and the widget's own words read back — the bar's figure rises from
  370 to 440 kcal a day, because there are fourteen fewer days left to do the
  same 4.2 kg in.
- **`temporal-reminder`** runs the alarm chain for four days without posting
  anything itself. Each morning's reminder arrives on its own after 08:00 and
  carries that day's arithmetic: "0.4 kg ahead ... yesterday you were 79.2 kg",
  then "0.3 kg ahead ... last logged on 2026-08-27" once the weigh-in is older
  than yesterday, then "0.3 kg behind" eleven days later off the same fixture.

Two defects came out of writing them:

- **A widget that outlived midnight kept yesterday's day.** Redrawing was not
  enough: Glance recomposes a running session rather than calling
  `provideGlance` again, and the widget's data is a combine over four Room
  flows, none of which emits merely because it is tomorrow. So the midnight
  refresh recomposed the same snapshot, date and all, and only a phone whose app
  process had died overnight — the common case, which is why this survived —
  came back right. `DayChange` now publishes the day as a `StateFlow` and the
  widgets combine over it.
- **The log sheet's keypad could be typed wrong by a test.** Keys were found by
  the digit on them, and with a "7" already on the display the merged node
  covering the whole sheet carried the text "7" and a click action too — so
  typing "77.0" clicked the middle of the sheet and produced "75.0". The keys
  carry test tags now. Only repeated digits were affected, which no scenario had
  typed before.

### The launcher wears the real icon

- **The app icon is now the artwork, not a stand-in.** `assets/icon.png` — the
  figure on a mesh gradient that the Play Store icon and the website already use
  — was drawn for an iOS-style plate: one flat picture, 768 px square, with its
  own rounded corners baked in and the figure running nearly edge to edge. Used
  whole as an adaptive icon it would have been rounded twice, and the launcher's
  mask, which shows only the centre 72 dp of the 108 dp canvas, would have taken
  the head and the hands. So the plate is taken apart and rebuilt to that
  geometry: the gradient becomes the background layer, bled past the mask on
  every side, and the figure becomes the foreground, sized so the mask crops it
  below the hands and through the hips — where the store icon is already cropped.
  The two now show the same picture. `assets/make_launcher_icon.py` does the
  work and is the thing to edit; the PNGs under `res/mipmap-*dpi` are its output.
- **The chart motif stays on the notification.** The bars and trend lines in the
  source sit at the plate's edge, which is exactly what the mask eats, and
  unmixed off the gradient they carry an alpha of about 0.03 — invisible at
  48 dp, and at the mask edge only clipped fragments of themselves. Boosting
  them read as dirt rather than as a chart, so the foreground carries the figure
  alone. `ic_notification` is unchanged and still carries the chart.
- **Themed icons get a real monochrome layer.** It used to be the old green
  chart glyph doing double duty as foreground and monochrome both; it is now the
  figure's silhouette, so an Android 13 themed icon is recognisably the same
  mark. The pinholes that a colour icon hides — white on white — had to be
  closed for it, since a tint turns each one into a speck.
- Cutting the figure off the gradient it was painted on is what the script
  mostly does. The white body alone leaves a hole at the neck, because the
  shadow under the chin is a blue-grey the "is this white?" test rejects; a test
  loose enough to keep it also swallows the drop shadow around the figure, which
  is the same colour. So the loose test counts only inside a morphological
  closing of the tight one: the neck fills, the outside does not, and the
  windows between arm and torso stay open onto the background.

### The chart opens on the road ahead, and sync keeps up with the scale

Five things asked for after living with the app on a phone.

- **The plan now lands on the X axis whatever the goal is.** The plot's floor was
  the round weight at or below the lowest weight drawn, which is the goal itself
  on a loss plan — so a goal of 74.5 kg put the floor at 74.0 and left the plan
  line and the hollow goal ring hanging half a kilogram above the axis, while the
  projection's landing dot, which is drawn *on* the axis by construction, sat
  correctly on it. Two marks of the same finish, on two different lines. The floor
  is now the lowest weight itself and is a label in its own right, with round
  weights carrying the grid above it; where the goal is already round — 75.0 with
  a whole-kilogram step — the axis is exactly what it always was. A round label
  close enough to the bottom one to read as a smudge is dropped rather than
  printed. What lands on that floor is a point — the goal ring, the dot on the
  lowest reading — and points are drawn under a clip of their own that reaches a
  little past the axis, in both charts: a clip stopping at the axis drew them as
  half moons. The band and the lines keep the clip that stops there, which is what
  keeps them out of the strip the dates live in.
- **The chart opens on what is left of the plan.** The Plan chip used to open on
  the whole span, so two months of weighed-in history squeezed the part the reader
  is actually asking about — where the plan line, the band and the projection
  converge — into the right-hand third. It now opens with today at the left edge
  and the target date at the right — or at the last weigh-in, when that is older
  than today, because a window anchored on today alone opens with nothing of the
  reader's own in it as soon as they miss a few days: no line, no latest dot, and a
  projection rising out of a point off the left edge. Nothing is thrown away: the
  bounds still reach back to the plan's first day, so a pan or a pinch-out walks
  into July, and the 7d, 30d and 90d chips are unchanged. The tighter window also buys the scale back
  — a plot that ran 75 to 83 to hold the history now runs 75 to 81.
- **The widget's three figures are centred.** Left, Per day and Finish sat against
  the left edge of tiles wider than they were, and against the top of tiles taller
  than they were, so a row of three figures under a centred chart read as three
  paragraphs. They are centred both ways now, in the widget and in the gallery
  preview that mirrors it.
- **Background sync runs every half hour, not once a day.** A weight a scale wrote
  at seven in the morning that a widget picks up tomorrow has, to the person
  looking at the widget, not synced at all: they weigh themselves, glance at the
  home screen, see yesterday's figure, and open the app — which is the one thing
  background sync exists to make unnecessary. Half an hour is close to the floor
  WorkManager allows for periodic work, and the work itself is a few hundred rows
  read over IPC and a merge that usually writes nothing. `DailySyncWorker` is
  `HealthSyncWorker`, and it cancels the old job by its old name so no install
  keeps both.
- **Whether a phone can read in the background is now asked, not assumed.** The
  gate was `SDK_INT >= 35`, and the screen told everyone below it that their phone
  could not do this. Background reads ship in the Health Connect module, not in the
  platform: the version map in the Jetpack SDK puts the feature at API 34 with U
  extension 13, which an Android 14 phone gets from a module update. It now asks
  `HealthConnectFeatures.getFeatureStatus`, which is what Google's own guidance
  says to do, and the copy points at Health Connect's version rather than at
  Android's.
- **Three faults the review of all this turned up.** The ring's caption gate,
  `captionPaint.textSize >= 7f`, compared a pixel size against a number shaped like
  dp, so it really asked "is the ring 68.6 px across": 23 dp on a three-times
  screen, where it kept a caption two dp tall, and 46 dp on a one-and-a-half-times
  one, where it dropped a legible one — a fault that hides on whichever screen you
  happen to look at. The chart's calendar dates were pinned to the second row under
  the axis, which is right below the two landings and wrong when neither landing is
  in view, as on every 7d, 30d and 90d window: they hung a line low with an empty
  row above them. And a row that has no room left below the axis — a reader's 2x
  text leaves none — is no longer offered, because a Canvas does not clip and the
  figures were painting over the chips. Widgets reached from an intent now arrives
  the way the bottom bar arrives, not stacked on whatever screen the app was last
  left on; the row in Settings still pushes, because that is a drill-down and back
  from it belongs on Settings.
- **An hourly heartbeat behind everything the app derives.** Ahead or behind, the
  projected finish, the percentage and the status colour are `PlanMath` read
  against today's date, not rows in the database, so no data trigger fires when
  they change. `DayChange` arms one alarm for a minute past midnight to carry that
  over, and that alarm was the whole of it: miss one — doze, a force-stop, a vendor
  battery policy — and a widget shows yesterday's verdict until somebody opens the
  app. `PlanRefreshWorker` redraws every hour and re-arms the midnight alarm as it
  goes, so the chain repairs itself. It needs no grant of any kind, so unlike
  background sync it runs for everybody.

### Known gaps

- Play Billing and AdMob still need IDs from a real account before they do
  anything; the paywall reports that Play is unreachable and the banner shows
  Google's test creative.
- Auth emails are currently captured server-side for testing rather than
  delivered (see the README); production needs real SMTP and the hook turned
  off.
- Lock-screen placement for the glance widget depends on the launcher; it is
  declared for both the keyguard and home-screen categories.
