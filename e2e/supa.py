"""Supabase access for the E2E harness.

Reads the project URL and publishable key from secrets/supabase.properties and
the admin secret from secrets/e2e-admin.properties. The admin secret unlocks
the e2e-admin edge function, which is the only privileged path — no service
key ever lives on this machine.
"""

import json
import pathlib
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]


def _props(name):
    out = {}
    for line in (ROOT / "secrets" / name).read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k] = v
    return out


_P = _props("supabase.properties")
_A = _props("e2e-admin.properties")
URL = _P["supabase.url"]
KEY = _P["supabase.publishableKey"]
ADMIN_SECRET = _A["e2e.adminSecret"]


def _request(url, body, headers, method):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            text = r.read().decode()
            return r.status, json.loads(text) if text else {}
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        try:
            return e.code, json.loads(text) if text else {}
        except json.JSONDecodeError:
            return e.code, {"raw": text}


def admin(action, **kw):
    """Call the e2e-admin edge function."""
    status, body = _request(
        f"{URL}/functions/v1/e2e-admin",
        {"action": action, **kw},
        {"Content-Type": "application/json", "x-admin-secret": ADMIN_SECRET},
        "POST",
    )
    if status != 200:
        body["_status"] = status
    return body


def auth(path, body=None, token=None, method=None):
    """Call GoTrue as the app would: publishable key, optional user JWT."""
    headers = {"apikey": KEY, "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return _request(f"{URL}/auth/v1{path}", body, headers, method)


def rest(path, body=None, token=None, method=None, prefer=None):
    """Call PostgREST as the app would."""
    headers = {"apikey": KEY, "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if prefer:
        headers["Prefer"] = prefer
    return _request(f"{URL}/rest/v1{path}", body, headers, method)
