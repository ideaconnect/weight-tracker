// Test-support admin endpoint for the E2E suite. Every request must carry the
// x-admin-secret header matching the E2E_ADMIN_SECRET function secret, which
// lives only in the repository's git-ignored secrets/ folder and is handed to
// the instrumented tests at run time — never baked into an APK.
//
// This function exists so that no service-role or database credential ever has
// to leave Supabase: the CLI deploys it, and the platform injects the keys.

import postgres from "npm:postgres@3.4.5";

const URL = Deno.env.get("SUPABASE_URL")!;
const KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const SECRET = Deno.env.get("E2E_ADMIN_SECRET");

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2 });

async function gotrueAdmin(path: string, init: RequestInit = {}) {
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
    body = JSON.parse(text);
  } catch {
    body = { raw: text };
  }
  return { status: res.status, body };
}

Deno.serve(async (req) => {
  if (!SECRET || req.headers.get("x-admin-secret") !== SECRET) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }
  const p = await req.json().catch(() => ({}));
  try {
    switch (p.action) {
      case "sql": {
        const rows = await sql.unsafe(String(p.query));
        return Response.json({ rows });
      }
      case "create_user": {
        const r = await gotrueAdmin("/admin/users", {
          method: "POST",
          body: JSON.stringify({
            email: p.email,
            password: p.password,
            email_confirm: p.confirm !== false,
          }),
        });
        return Response.json(r.body, { status: r.status });
      }
      case "delete_user": {
        const rows = await sql`delete from auth.users where email = ${p.email} returning id`;
        return Response.json({ deleted: rows.length });
      }
      case "generate_otp": {
        // Generates without sending; returns the code the user would be emailed.
        const r = await gotrueAdmin("/admin/generate_link", {
          method: "POST",
          body: JSON.stringify({
            type: p.type,
            email: p.email,
            new_email: p.new_email,
            password: p.password,
          }),
        });
        const b = r.body as Record<string, unknown>;
        return Response.json(
          { email_otp: b?.email_otp, hashed_token: b?.hashed_token, verification_type: b?.verification_type, error: b?.error ?? b?.msg },
          { status: r.status },
        );
      }
      case "last_mail": {
        const rows = p.action_type
          ? await sql`select * from public.auth_mail where (email = ${p.email} or new_email = ${p.email}) and action_type = ${p.action_type} order by id desc limit 1`
          : await sql`select * from public.auth_mail where email = ${p.email} or new_email = ${p.email} order by id desc limit 1`;
        return Response.json({ mail: rows[0] ?? null });
      }
      case "clear_mail": {
        await sql`delete from public.auth_mail`;
        return Response.json({ ok: true });
      }
      case "get_backup": {
        const rows = await sql`
          select b.payload, b.updated_at from public.backups b
          join auth.users u on u.id = b.user_id
          where u.email = ${p.email}`;
        return Response.json({ backup: rows[0] ?? null });
      }
      default:
        return Response.json({ error: `unknown action ${p.action}` }, { status: 400 });
    }
  } catch (e) {
    return Response.json({ error: String(e) }, { status: 500 });
  }
});
