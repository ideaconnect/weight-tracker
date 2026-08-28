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

### Known gaps

- Play Billing and AdMob still need IDs from a real account before they do
  anything; the paywall reports that Play is unreachable and the banner shows
  Google's test creative.
- Auth emails are currently captured server-side for testing rather than
  delivered (see the README); production needs real SMTP and the hook turned
  off.
- Lock-screen placement for the glance widget depends on the launcher; it is
  declared for both the keyguard and home-screen categories.
