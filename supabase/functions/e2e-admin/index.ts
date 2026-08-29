// Test-support endpoint for the E2E suite.
//
// This function is deliberately narrow. An earlier version exposed an `sql`
// action running arbitrary statements with the service role, which made a single
// leaked header the keys to the whole project. It now offers only the handful of
// operations the suite actually needs, and every one of them refuses to touch
// anything but a test address (see TEST_ADDRESS).
//
// It still holds the service role, so:
//   * deploy it to the E2E project, never to the one holding real users;
//   * rotate E2E_ADMIN_SECRET if it is ever shared.
// See docs/production-checklist.md.

const URL = Deno.env.get("SUPABASE_URL")!;
const KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const SECRET = Deno.env.get("E2E_ADMIN_SECRET");

/** The only addresses this function will act on. */
const TEST_ADDRESS = /^e2e\.[a-z0-9._+-]+@example\.com$/i;

function assertTestAddress(email: unknown): string {
  if (typeof email !== "string" || !TEST_ADDRESS.test(email)) {
    throw new Error(`refusing to act on a non-test address: ${String(email)}`);
  }
  return email;
}

async function rest(path: string, init: RequestInit = {}) {
  const res = await fetch(`${URL}/rest/v1${path}`, {
    ...init,
    headers: {
      apikey: KEY,
      Authorization: `Bearer ${KEY}`,
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });
  const text = await res.text();
  return { status: res.status, body: text ? JSON.parse(text) : null };
}

async function gotrue(path: string, init: RequestInit = {}) {
  const res = await fetch(`${URL}/auth/v1${path}`, {
    ...init,
    headers: {
      apikey: KEY,
      Authorization: `Bearer ${KEY}`,
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });
  const text = await res.text();
  let body: unknown;
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    body = { raw: text };
  }
  return { status: res.status, body };
}

/** GoTrue has no delete-by-email, so the id is looked up first. */
async function findUser(email: string): Promise<{ id: string } | null> {
  const r = await gotrue(`/admin/users?page=1&per_page=200`);
  const users = (r.body as { users?: Array<{ id: string; email: string }> })?.users ?? [];
  return users.find((u) => u.email?.toLowerCase() === email.toLowerCase()) ?? null;
}

Deno.serve(async (req) => {
  if (!SECRET || req.headers.get("x-admin-secret") !== SECRET) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }
  const p = await req.json().catch(() => ({}));
  try {
    switch (p.action) {
      case "create_user": {
        const email = assertTestAddress(p.email);
        const r = await gotrue("/admin/users", {
          method: "POST",
          body: JSON.stringify({
            email,
            password: p.password,
            email_confirm: p.confirm !== false,
          }),
        });
        return Response.json(r.body, { status: r.status });
      }

      case "delete_user": {
        const email = assertTestAddress(p.email);
        const user = await findUser(email);
        if (!user) return Response.json({ deleted: 0 });
        const r = await gotrue(`/admin/users/${user.id}`, { method: "DELETE" });
        return Response.json({ deleted: r.status < 300 ? 1 : 0 }, { status: r.status });
      }

      case "user_exists": {
        const email = assertTestAddress(p.email);
        return Response.json({ exists: (await findUser(email)) !== null });
      }

      // Codes without an inbox: GoTrue mints the same OTP the user would be sent.
      // This is what lets the suite keep working once the capture hook is retired.
      case "generate_otp": {
        const email = assertTestAddress(p.email);
        const body: Record<string, unknown> = { type: p.type, email };
        if (p.new_email) body.new_email = assertTestAddress(p.new_email);
        if (p.password) body.password = p.password;
        const r = await gotrue("/admin/generate_link", {
          method: "POST",
          body: JSON.stringify(body),
        });
        const b = r.body as Record<string, unknown>;
        return Response.json(
          {
            email_otp: b?.email_otp ?? null,
            verification_type: b?.verification_type ?? null,
            error: b?.error ?? b?.msg ?? null,
          },
          { status: r.status },
        );
      }

      case "last_mail": {
        const email = assertTestAddress(p.email);
        const filter = p.action_type
          ? `&action_type=eq.${encodeURIComponent(String(p.action_type))}`
          : "";
        const q = `/auth_mail?or=(email.eq.${encodeURIComponent(email)},new_email.eq.` +
          `${encodeURIComponent(email)})${filter}&order=id.desc&limit=1`;
        const r = await rest(q);
        return Response.json({ mail: (r.body as unknown[])?.[0] ?? null });
      }

      case "clear_mail": {
        // Only ever the addresses this function is allowed to touch.
        const r = await rest(`/auth_mail?email=like.e2e.*%40example.com`, {
          method: "DELETE",
        });
        return Response.json({ ok: r.status < 300 });
      }

      case "get_backup": {
        const email = assertTestAddress(p.email);
        const user = await findUser(email);
        if (!user) return Response.json({ backup: null });
        const r = await rest(
          `/backups?user_id=eq.${user.id}&select=payload,updated_at`,
        );
        return Response.json({ backup: (r.body as unknown[])?.[0] ?? null });
      }

      default:
        return Response.json({ error: `unknown action ${p.action}` }, { status: 400 });
    }
  } catch (e) {
    return Response.json({ error: String(e) }, { status: 400 });
  }
});
