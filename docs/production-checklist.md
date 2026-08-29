# Before this ships

The app is feature-complete against the specification, but several things are
configured for development convenience in ways that are wrong for a public
release. Each item below is a decision or a credential someone has to supply —
none of them can be settled from inside the repository.

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
- [x] **Real SMTP is configured on the project** (2026-08-29).
- [ ] **Turn the capture hook off, so that SMTP is actually used.** While
      `[auth.hook.send_email]` is enabled GoTrue calls the hook *instead of*
      sending, so no account email leaves the project no matter what SMTP says.
      Set `enabled = false` on the hook and push with `e2e/config-push.sh`.

      The E2E suite no longer stands in the way: it reads codes from the hook
      when one exists and asks GoTrue to mint them otherwise, and every
      code-dependent scenario is verified both ways. Prove it for yourself first:

      ```
      python e2e/run.py --generated-codes
      ```

- [ ] **Put the SMTP values in `secrets/smtp.env`** (`SMTP_HOST`, `SMTP_USER`,
      `SMTP_PASS`, `SMTP_ADMIN_EMAIL`), matching what is configured on the
      project. `supabase/config.toml` no longer carries them, and
      `e2e/config-push.sh` refuses to push without them — previously the file
      held a placeholder host, so any push silently replaced working SMTP
      settings with a dead one.
- [ ] **Drop `public.auth_mail`** once the hook is off. While the hook is on,
      every verification and password-reset code is stored there in plain text.
      Rows are pruned after 15 minutes, which is a development safeguard, not a
      reason to keep the table where real users exist. Nothing else reads it.
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
- [ ] **Real AdMob and Play Billing IDs.** Both are still Google's public test
      values (see the README).

## Known residual behaviour

Not blockers, but worth deciding on deliberately:

- **Two devices sharing an account** are protected against silent clobbering —
  an upload that would replace a row this device did not write raises a conflict
  and the user chooses — but there is no merge. Whoever chooses "replace" wins.
- **Backup restores wholesale.** There is no partial or per-day recovery, and no
  history of previous backups: the row holds one snapshot.
