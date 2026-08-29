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

### Known gaps

- Play Billing and AdMob still need IDs from a real account before they do
  anything; the paywall reports that Play is unreachable and the banner shows
  Google's test creative.
- Auth emails are currently captured server-side for testing rather than
  delivered (see the README); production needs real SMTP and the hook turned
  off.
- Lock-screen placement for the glance widget depends on the launcher; it is
  declared for both the keyguard and home-screen categories.
