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
- Verified by rendering all six widgets at four cell sizes each, at the default
  text size and at 1.3x: `WidgetSizingTest` composes each widget at an explicit
  dp cell and writes a PNG, which is the whole of what a grid change does to a
  composition without needing a launcher that offers the grid. Every cell the
  repo's screenshots were taken at renders byte-identically to before the fix.
  `WidgetCellTest` pins the arithmetic under it, and `SeedData.resizeWidgets`
  (`--ei cellw --ei cellh`) drives the real `APPWIDGET_UPDATE_OPTIONS` path on a
  device.

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

### Known gaps

- Play Billing and AdMob still need IDs from a real account before they do
  anything; the paywall reports that Play is unreachable and the banner shows
  Google's test creative.
- Auth emails are currently captured server-side for testing rather than
  delivered (see the README); production needs real SMTP and the hook turned
  off.
- Lock-screen placement for the glance widget depends on the launcher; it is
  declared for both the keyguard and home-screen categories.
