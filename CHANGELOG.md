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

### Since SMTP was configured

- The E2E suite no longer depends on the mail-capture hook. It still prefers the
  captured code — that is the one the app's own send produced — but falls back to
  asking GoTrue to mint the same OTP, which sends nothing. `--generated-codes`
  runs the suite as though the hook did not exist, so the hook can be retired
  without breaking anything.
- `supabase/config.toml` no longer carries SMTP credentials. It held a
  placeholder host, which meant any `config push` silently replaced the
  project's working SMTP settings with a dead one; the values now come from
  `secrets/smtp.env` and `e2e/config-push.sh` refuses to push without them.

### Known gaps

- Play Billing and AdMob still need IDs from a real account before they do
  anything; the paywall reports that Play is unreachable and the banner shows
  Google's test creative.
- Auth emails are currently captured server-side for testing rather than
  delivered (see the README); production needs real SMTP and the hook turned
  off.
- Lock-screen placement for the glance widget depends on the launcher; it is
  declared for both the keyguard and home-screen categories.
