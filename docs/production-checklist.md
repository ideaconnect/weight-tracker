# Before this ships

The app is feature-complete against the specification, but several things are
configured for development convenience in ways that are wrong for a public
release. Each item below is a decision or a credential someone has to supply —
none of them can be settled from inside the repository.

## Store identity

- Package name: `tech.idct.weighttracker` (already the release
  `applicationId`; the debug build appends `.debug` and never ships).
- Play listing name: **Weight tracker with widgets**. The launcher label stays
  the shorter "Weight Tracker" on purpose — a label that long is ellipsized
  under the icon; the listing name and the label do not have to match.

## Supabase

**Use a second project for the E2E suite.** Everything in this section follows
from a single choice: whether the project holding real users is also the one the
tests drive. It should not be.

- [ ] **`e2e-admin` must not exist on the production project.** It holds the
      service role and is gated by one static header. It has been narrowed — no
      arbitrary SQL, and every action refuses any address that is not
      `e2e.*@example.com` — but it is still a privileged endpoint that exists
      only for testing. Deploy it to the E2E project only.
- [ ] **Rotate `E2E_ADMIN_SECRET`** if it has ever been shared, and keep the
      copy in `secrets/` backed up somewhere the team can reach: nothing else
      can recover it.
- [x] **Real SMTP is configured on the project** (Resend, 2026-08-29), and
      account email is genuinely delivered: verification, password reset and
      email change all send. Verified end to end — the credentials authenticate,
      a test message reached the admin address, and `e2e/verify_backend.py`
      drives all three flows against the live project.
- [x] **The send-email capture hook is gone** — disabled, then the function
      deleted and its config block removed. Nothing intercepts account mail.
- [x] **`public.auth_mail` is dropped** (migration
      `20260829120000_drop_auth_mail.sql`). Live verification and reset codes are
      no longer stored anywhere.
- [x] **SMTP values live in `secrets/smtp.env`**, not in the committed
      `config.toml`, and `e2e/config-push.sh` refuses to push without them —
      the file used to carry a placeholder host, so any push would have replaced
      working SMTP settings with a dead one.
- [ ] **Decide where the E2E suite runs.** It no longer needs a mail hook (it
      asks GoTrue to mint codes) and it only ever addresses Resend's delivery
      simulator, so it sends nothing real and generates no bounces. What remains
      is `e2e-admin`: a service-role function, narrow and address-guarded, but
      still privileged. Deploying it to a project that holds real users is a
      choice to make deliberately — see the E2E section above.
- [ ] **Check the email rate limits** in `[auth.rate_limit]`. They are currently
      raised to 720/hour so the test suite can run; production wants something
      closer to the default.

## Google Play

- [ ] **A web account-deletion URL.** In-app deletion exists (Account → Delete
      account), which Play requires — but it also requires a publicly reachable
      page where someone can request deletion *without* installing the app.
- [ ] **A privacy policy URL**, and its text has to match what the app says:
      weights stay on the device unless cloud backup is switched on; an email
      address is stored for the account; both are deletable by the user.
- [ ] **Data Safety declaration**: email address collected; weight history and
      plan uploaded only when backup is on; both deletable; nothing shared with
      third parties. The AdMob banner's own disclosures apply too.

## The build

- [ ] **A real signing key.** `secrets/keystore.properties` supplies
      `storeFile`, `storePassword`, `keyAlias` and `keyPassword`; without it the
      release build falls back to the debug key and logs a warning. Play will
      not accept a debug-signed APK, and the key cannot be changed later without
      losing the listing.
- [x] **Smoke-test the release build on a device.** Done on 2026-08-28: the
      minified release APK signed in against the real project and uploaded a
      backup, so R8 does not break serialization or the HTTP path. Worth
      repeating whenever the proguard rules or the payload change — it is how
      the missing payload `version` field was caught.
- [x] **Real AdMob IDs.** Supplied by `secrets/admob.env` (`APP_ID`, `AD_ID`)
      since 2026-08-29: release builds carry them, debug builds keep Google's
      test units as AdMob policy asks. Without the file the release falls back
      to test values and logs a warning.
- [ ] **Real Play Billing product ID.** Still a placeholder in
      `app/src/main/res/values/config.xml` (see the README).

## Known residual behaviour

Not blockers, but worth deciding on deliberately:

- **Two devices sharing an account** are protected against silent clobbering —
  an upload that would replace a row this device did not write raises a conflict
  and the user chooses — but there is no merge. Whoever chooses "replace" wins.
- **Backup restores wholesale.** There is no partial or per-day recovery, and no
  history of previous backups: the row holds one snapshot.
