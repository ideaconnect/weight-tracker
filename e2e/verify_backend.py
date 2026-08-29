"""Proves every auth and backup flow against the real Supabase project.

Run before touching app code, and any time the backend is in doubt:

    python e2e/verify_backend.py

Each step prints PASS/FAIL; a non-zero exit means the backend contract the app
relies on does not hold.
"""

import sys
import time

import supa

STAMP = str(int(time.time()))
EMAIL = f"delivered+e2e.verify.{STAMP}@resend.dev"
EMAIL2 = f"delivered+e2e.verify.{STAMP}.new@resend.dev"
OTHER = f"delivered+e2e.other.{STAMP}@resend.dev"
PW1, PW2 = "first-password-1", "second-password-2"

failures = []


def check(name, ok, detail=""):
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"  ({detail})" if detail and not ok else ""))
    if not ok:
        failures.append(name)


def code_for(action_type, email, new_email=None):
    """Account mail is delivered for real now, so there is no inbox to read.
    GoTrue mints the same OTP on request, without sending."""
    kw = {"type": action_type, "email": email}
    if new_email:
        kw["new_email"] = new_email
    return supa.admin("generate_otp", **kw).get("email_otp")


# --- signup with verification ------------------------------------------------
s, b = supa.auth("/signup", {"email": EMAIL, "password": PW1})
check("signup accepted", s == 200, f"{s} {b}")
check("signup does not hand out a session before verification", not b.get("access_token"), str(b)[:120])

token = code_for("signup", EMAIL) or ""
check("a signup code can be obtained", len(token) == 6, token)
check("code is six digits", len(token) == 6 and token.isdigit(), token)

s, b = supa.auth("/verify", {"type": "signup", "email": EMAIL, "token": token})
check("verify with the emailed code signs the user in", s == 200 and b.get("access_token"), f"{s} {b}")

# --- password login and refresh ----------------------------------------------
s, b = supa.auth("/token?grant_type=password", {"email": EMAIL, "password": PW1})
check("password login", s == 200 and b.get("access_token"), f"{s} {str(b)[:120]}")
access, refresh = b.get("access_token", ""), b.get("refresh_token", "")

s, b = supa.auth("/token?grant_type=refresh_token", {"refresh_token": refresh})
check("token refresh", s == 200 and b.get("access_token"), f"{s} {str(b)[:120]}")
access = b.get("access_token", access)

s, b = supa.auth("/token?grant_type=password", {"email": EMAIL, "password": "wrong"})
check("wrong password rejected", s in (400, 401), f"{s}")

# --- password reset via emailed code ------------------------------------------
s, b = supa.auth("/recover", {"email": EMAIL})
check("recover request accepted", s == 200, f"{s} {b}")
recovery_code = code_for("recovery", EMAIL) or ""
check("recovery code obtained", len(recovery_code) == 6, recovery_code)
s, b = supa.auth("/verify", {"type": "recovery", "email": EMAIL, "token": recovery_code})
check("recovery code yields a session", s == 200 and b.get("access_token"), f"{s} {str(b)[:120]}")
recovery_access = b.get("access_token", "")

s, b = supa.auth("/user", {"password": PW2}, token=recovery_access, method="PUT")
check("password updated after recovery", s == 200, f"{s} {str(b)[:120]}")
s, b = supa.auth("/token?grant_type=password", {"email": EMAIL, "password": PW2})
check("login with the new password", s == 200 and b.get("access_token"), f"{s}")
access = b.get("access_token", access)

# --- password change while signed in ------------------------------------------
s, b = supa.auth("/user", {"password": PW1}, token=access, method="PUT")
check("password change while signed in", s == 200, f"{s} {str(b)[:120]}")
s, b = supa.auth("/token?grant_type=password", {"email": EMAIL, "password": PW1})
check("login with the changed password", s == 200 and b.get("access_token"), f"{s}")
access = b.get("access_token", access)

# --- email change, confirmed at the new address only ---------------------------
s, b = supa.auth("/user", {"email": EMAIL2}, token=access, method="PUT")
check("email change request accepted", s in (200, 201), f"{s} {str(b)[:120]}")
change_code = code_for("email_change_new", EMAIL, new_email=EMAIL2) or ""
check("email-change code obtained for the new address", len(change_code) == 6, change_code)
s, b = supa.auth("/verify", {"type": "email_change", "email": EMAIL2, "token": change_code})
check("email-change code verifies", s == 200, f"{s} {str(b)[:120]}")
access = b.get("access_token", access)
s, b = supa.auth("/user", token=access, method="GET")
check("user now carries the new address", b.get("email") == EMAIL2, str(b.get("email")))

# --- backups table under row-level security -----------------------------------
uid = b.get("id", "")
payload = {"version": 1, "entries": [{"date": "2026-08-28", "kg": 82.4}], "plan": {"targetKg": 75.0}}
s, b = supa.rest(
    "/backups?on_conflict=user_id",
    [{"user_id": uid, "payload": payload}],
    token=access,
    method="POST",
    prefer="resolution=merge-duplicates,return=representation",
)
check("backup upsert", s in (200, 201), f"{s} {str(b)[:160]}")

s, b = supa.rest("/backups?select=payload,updated_at", token=access, method="GET")
check("backup readable by its owner", s == 200 and len(b) == 1 and b[0]["payload"]["version"] == 1, f"{s} {str(b)[:160]}")
check("updated_at is server time", bool(b[0].get("updated_at")) if s == 200 and b else False)

payload["entries"].append({"date": "2026-08-29", "kg": 82.1})
s, b = supa.rest(
    "/backups?on_conflict=user_id",
    [{"user_id": uid, "payload": payload}],
    token=access,
    method="POST",
    prefer="resolution=merge-duplicates,return=representation",
)
check("backup upsert over existing row", s in (200, 201) and b and len(b[0]["payload"]["entries"]) == 2, f"{s} {str(b)[:160]}")

r = supa.admin("create_user", email=OTHER, password=PW1)
check("admin create_user", "id" in r, str(r)[:120])
s, b = supa.auth("/token?grant_type=password", {"email": OTHER, "password": PW1})
other_access = b.get("access_token", "")
s, b = supa.rest("/backups?select=user_id", token=other_access, method="GET")
check("another user cannot see the backup", s == 200 and b == [], f"{s} {str(b)[:120]}")
s, b = supa.rest(f"/backups?user_id=eq.{uid}", token=other_access, method="DELETE", prefer="return=representation")
check("another user cannot delete the backup", b == [], f"{s} {str(b)[:120]}")

s, b = supa.rest(f"/backups?user_id=eq.{uid}", token=access, method="DELETE", prefer="return=representation")
check("owner clears the backup", s == 200 and len(b) == 1, f"{s} {str(b)[:120]}")
s, b = supa.rest("/backups?select=user_id", token=access, method="GET")
check("backup row gone after clear", s == 200 and b == [], f"{s} {str(b)[:120]}")

# --- account deletion ----------------------------------------------------------
s, b = supa.rest("/rpc/delete_user", {}, token=access, method="POST")
check("delete_user rpc", s in (200, 204), f"{s} {str(b)[:120]}")
s, b = supa.auth("/token?grant_type=password", {"email": EMAIL2, "password": PW1})
check("deleted account cannot sign in", s in (400, 401), f"{s} {str(b)[:120]}")

# --- the admin function is narrow ----------------------------------------------
# The suite must survive the capture hook being retired.
r = supa.admin("generate_otp", type="recovery", email=OTHER)
check("codes can be minted without an inbox", len(r.get("email_otp") or "") == 6, str(r)[:160])

r = supa.admin("sql", query="select 1")
check("the arbitrary-SQL action is gone", r.get("_status") == 400, str(r)[:120])

r = supa.admin("delete_user", email="someone.real@gmail.com")
check("non-test addresses are refused", r.get("_status") == 400, str(r)[:160])

r = supa.admin("generate_otp", type="recovery", email="someone.real@gmail.com")
check("codes for non-test addresses cannot be minted", r.get("_status") == 400, str(r)[:160])

# --- cleanup -------------------------------------------------------------------
supa.admin("delete_user", email=OTHER)

print()
if failures:
    print(f"{len(failures)} failure(s): {failures}")
    sys.exit(1)
print("backend contract holds")
