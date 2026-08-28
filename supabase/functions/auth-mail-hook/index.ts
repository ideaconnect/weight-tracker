// Send-Email auth hook: GoTrue calls this instead of an SMTP server, so account
// verification works for any address and the E2E suite can read the codes it
// would otherwise have to scrape from an inbox. Codes land in public.auth_mail,
// which has row-level security on and no policies — service role only.
//
// THIS MUST NOT RUN ON A PROJECT WITH REAL USERS: it turns every verification and
// password-reset code into a stored row. Before a public release, configure real
// SMTP *and* disable this hook together (see docs/production-checklist.md) —
// disabling it alone leaves GoTrue pointed at the placeholder smtp.invalid host
// and no auth email is delivered at all.

const URL = Deno.env.get("SUPABASE_URL")!;
const KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

Deno.serve(async (req) => {
  const body = await req.json().catch(() => null);
  const user = body?.user;
  const data = body?.email_data;
  if (!user?.email || !data?.token) {
    return Response.json({ error: "unexpected payload" }, { status: 400 });
  }
  const row = {
    email: user.email,
    new_email: user.new_email ?? null,
    action_type: data.email_action_type ?? "unknown",
    token: data.token,
    token_hash: data.token_hash ?? null,
    token_new: data.token_new || null,
  };
  // Retention: a verification code is an account-takeover credential, so no row
  // outlives the code it carries by more than a few minutes. This is a
  // development convenience, not a place to accumulate anything.
  const cutoff = new Date(Date.now() - 15 * 60 * 1000).toISOString();
  await fetch(`${URL}/rest/v1/auth_mail?created_at=lt.${cutoff}`, {
    method: "DELETE",
    headers: { apikey: KEY, Authorization: `Bearer ${KEY}` },
  }).catch(() => {});

  const res = await fetch(`${URL}/rest/v1/auth_mail`, {
    method: "POST",
    headers: {
      apikey: KEY,
      Authorization: `Bearer ${KEY}`,
      "Content-Type": "application/json",
      Prefer: "return=minimal",
    },
    body: JSON.stringify(row),
  });
  if (!res.ok) {
    return Response.json({ error: `store failed: ${res.status}` }, { status: 500 });
  }
  return Response.json({});
});
