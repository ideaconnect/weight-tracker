"""Runs the E2E suite on the connected emulator and writes the report.

    python e2e/run.py                # build, install, run everything
    python e2e/run.py --skip-build   # reuse the APKs already installed
    python e2e/run.py backup restore # just these scenarios

Every scenario is one instrumented test driving the real UI against the real
Supabase project. Screenshots land in e2e/report/screenshots/<scenario>/ and
e2e/report/report.html tells the whole story.
"""

import pathlib
import re
import shutil
import subprocess
import sys
import time

import supa

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPORT = ROOT / "e2e" / "report"
PKG = "tech.idct.weighttracker.debug"
RUNNER = f"{PKG}.test/androidx.test.runner.AndroidJUnitRunner"
DEVICE_SHOTS = f"/storage/emulated/0/Android/data/{PKG}/files/e2e"
# Every account this run creates carries it, so two runs never collide and no
# scenario can read a verification code left behind by an earlier one.
RUN_ID = str(int(time.time()) % 1_000_000)

SCENARIOS = [
    ("signup", "AccountTest#signup",
     "Create an account in the app, verify it with the emailed 6-digit code, land signed in."),
    ("login", "AccountTest#login",
     "Sign in to an existing account with email and password."),
    ("password-reset", "AccountTest#passwordReset",
     "Forgot password: request a code, set a new password, then prove it by signing in with it."),
    ("password-change", "AccountTest#passwordChange",
     "Change the password while signed in, sign out, sign back in with the new one."),
    ("email-change", "AccountTest#emailChange",
     "Move the account to a new address, verified by a code sent to that address."),
    ("account-removal", "AccountTest#accountRemoval",
     "Delete the account from the app; the server forgets it, the phone keeps its data."),
    ("wrong-password", "AccountErrorsTest#wrongPassword",
     "A wrong password is refused in the app's own words, and the panel that can fix it stays put."),
    ("wrong-code", "AccountErrorsTest#wrongCode",
     "A wrong verification code is rejected, and the real one still works afterwards."),
    ("resend-code", "AccountErrorsTest#resendCode",
     "“Send a new code” really sends a second one, and that one verifies."),
    ("duplicate-signup", "AccountErrorsTest#signUpWithAnAddressThatAlreadyExists",
     "Signing up with an address that already has an account says so, instead of promising a code that never comes."),
    ("backup", "BackupTest#backup",
     "Turn backup on: the seeded entries and the plan appear server-side, and a newly logged weight follows by itself."),
    ("restore", "BackupTest#restore",
     "A fresh phone, the same account: restore brings everything back, then clearing the cloud copy leaves the phone alone."),
    ("backup-conflict", "BackupTest#conflict",
     "A second phone turning backup on is asked which copy to keep, instead of silently overwriting the first phone's history."),
    ("celebration-once", "BackupTest#celebrationDoesNotReplayAfterRestore",
     "Restoring a finished plan onto a new phone does not replay the trophy."),
    ("delete-all-data", "TrackingTest#deleteAllData",
     "Delete all data empties the phone, keeps the purchase, and does not restart onboarding."),
    ("manual-entry", "TrackingTest#manualEntry",
     "Log the first weight by hand; logging again the same day replaces the value."),
    ("hc-sync-in", "HealthConnectTest#syncFromHealthConnect",
     "Records written into Health Connect appear after app open; the earliest reading of a day wins."),
    ("hc-sync-out", "HealthConnectTest#syncToHealthConnect",
     "A manually logged weight is written back into Health Connect."),
    ("widgets", "WidgetsTest#multipleWidgets",
     "Place the ring, the bar and both lock-screen glances from the gallery; all four end up on the launcher."),
    ("widgets-behind", "WidgetsTest#widgetsBehindPlan",
     "The same widgets over the behind fixture: every ring, bar and percentage on the launcher turns amber."),
    ("on-track", "TrackingTest#onTrack",
     "The sample plan, on schedule: green, and ahead."),
    ("behind", "TrackingTest#behind",
     "The same plan losing too slowly: the status colour turns amber and says behind."),
    ("finish-plan", "TrackingTest#finishPlan",
     "Logging the target weight earns the trophy screen, once."),
]


def sh(*args, timeout=600):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout).stdout


def adb(*args, timeout=600):
    return sh("adb", *args, timeout=timeout)


def build_and_install():
    print("building and installing app + test APKs …")
    gradle = str(ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew"))
    r = subprocess.run(
        [gradle, ":app:installDebug", ":app:installDebugAndroidTest", "-q"],
        cwd=ROOT, capture_output=True, text=True, timeout=1200,
    )
    if r.returncode != 0:
        print(r.stdout[-4000:])
        print(r.stderr[-4000:])
        sys.exit("install failed")


def run_scenario(name, target, description):
    adb("shell", "rm", "-rf", DEVICE_SHOTS)
    adb("shell", "am", "force-stop", PKG)
    started = time.time()
    out = adb(
        "shell", "am", "instrument", "-w",
        "-e", "class", f"tech.idct.weighttracker.e2e.{target.replace('#', '#')}",
        "-e", "runId", RUN_ID,
        "-e", "supabaseUrl", supa.URL,
        "-e", "adminSecret", supa.ADMIN_SECRET,
        RUNNER,
        timeout=900,
    )
    duration = time.time() - started
    ok = bool(re.search(r"OK \(\d+ test", out)) and "FAILURES" not in out

    shots_dir = REPORT / "screenshots" / name
    shots_dir.mkdir(parents=True, exist_ok=True)
    adb("pull", DEVICE_SHOTS + "/.", str(shots_dir))
    if not ok:
        # whatever was on screen when it died is the best clue there is
        png = adb_exec_screencap()
        if png:
            (shots_dir / "99-at-failure.png").write_bytes(png)
    for png in shots_dir.glob("*.png"):
        downscale(png)
    shots = sorted(p.name for p in shots_dir.glob("*.png"))
    return {
        "name": name, "target": target, "description": description,
        "ok": ok, "duration": duration, "shots": shots,
        "log": "" if ok else tail_failure(out),
    }


def downscale(path, width=540):
    """A full-resolution phone screenshot is ~500 KB and the report holds sixty of
    them; every committed run used to add megabytes to the repository forever.
    Half width is still comfortably readable in the report."""
    try:
        from PIL import Image
    except ImportError:
        return
    try:
        with Image.open(path) as im:
            if im.width <= width:
                return
            im = im.convert("RGB")
            im.thumbnail((width, width * 4), Image.LANCZOS)
            im.save(path, "PNG", optimize=True)
    except Exception:
        pass


def adb_exec_screencap():
    r = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60)
    return r.stdout if r.returncode == 0 else None


def tail_failure(out):
    lines = out.splitlines()
    for i, line in enumerate(lines):
        if "There was 1 failure" in line or "FAILURES!!!" in line or "INSTRUMENTATION_STATUS: stack=" in line:
            return "\n".join(lines[i:i + 60])
    return "\n".join(lines[-40:])


def render(results, fingerprint):
    total = len(results)
    passed = sum(1 for r in results if r["ok"])
    stamp = time.strftime("%Y-%m-%d %H:%M")
    rows = "\n".join(
        f'<tr><td><a href="#{r["name"]}">{r["name"]}</a></td>'
        f'<td class="{"pass" if r["ok"] else "fail"}">{"PASS" if r["ok"] else "FAIL"}</td>'
        f'<td>{r["duration"]:.0f}s</td><td>{r["description"]}</td></tr>'
        for r in results
    )
    sections = "\n".join(
        f'<section id="{r["name"]}"><h2>{r["name"]} '
        f'<span class="{"pass" if r["ok"] else "fail"}">{"PASS" if r["ok"] else "FAIL"}</span></h2>'
        f'<p>{r["description"]} <em>({r["target"]}, {r["duration"]:.0f}s)</em></p>'
        + (f'<pre>{r["log"]}</pre>' if r["log"] else "")
        + '<div class="shots">'
        + "".join(
            f'<figure><img src="screenshots/{r["name"]}/{s}" loading="lazy">'
            f'<figcaption>{s[3:-4].replace("-", " ")}</figcaption></figure>'
            for s in r["shots"]
        )
        + "</div></section>"
        for r in results
    )
    html = f"""<!doctype html>
<meta charset="utf-8">
<title>Weight Tracker · E2E report</title>
<style>
 body {{ font: 15px/1.5 system-ui, sans-serif; margin: 40px auto; max-width: 1100px; padding: 0 20px; color: #222; }}
 h1 {{ font-weight: 300; font-size: 34px; }} h2 {{ margin-top: 48px; }}
 table {{ border-collapse: collapse; width: 100%; }}
 td, th {{ border-bottom: 1px solid #e4e4e4; padding: 8px 10px; text-align: left; vertical-align: top; }}
 .pass {{ color: #2e9a5e; font-weight: 600; }} .fail {{ color: #a9720f; font-weight: 600; }}
 .shots {{ display: flex; flex-wrap: wrap; gap: 14px; }}
 figure {{ margin: 0; width: 240px; }}
 img {{ width: 240px; border: 1px solid #e4e4e4; border-radius: 10px; }}
 figcaption {{ font-size: 12px; color: #6b6b6b; text-align: center; }}
 pre {{ background: #f6f6f6; border: 1px solid #e4e4e4; border-radius: 8px; padding: 12px; overflow-x: auto; font-size: 12px; }}
 .meta {{ color: #6b6b6b; }}
</style>
<h1>Weight Tracker · E2E report</h1>
<p class="meta">{stamp} · {passed}/{total} passed · every scenario drives the real UI on the emulator
against the real Supabase project ({supa.URL}) · device {fingerprint}</p>
<table><tr><th>Scenario</th><th>Result</th><th>Time</th><th>What it proves</th></tr>{rows}</table>
{sections}
"""
    (REPORT / "report.html").write_text(html, encoding="utf-8")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    skip_build = "--skip-build" in sys.argv
    chosen = [s for s in SCENARIOS if not args or s[0] in args]
    if not chosen:
        sys.exit(f"no scenario matches {args}")

    if not skip_build:
        build_and_install()
    for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        adb("shell", "settings", "put", "global", setting, "0")

    if not args:
        if (REPORT / "screenshots").exists():
            shutil.rmtree(REPORT / "screenshots")
        # A full run starts from a clean home screen, or every past run's pinned
        # widgets pile up in the launcher screenshots.
        adb("shell", "pm", "clear", "com.google.android.apps.nexuslauncher")
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
    fingerprint = adb("shell", "getprop", "ro.build.fingerprint").strip()

    results = []
    for name, target, description in chosen:
        print(f"{name:16s} … ", end="", flush=True)
        r = run_scenario(name, target, description)
        results.append(r)
        print(("PASS" if r["ok"] else "FAIL") + f"  {r['duration']:.0f}s  {len(r['shots'])} shots")

    render(results, fingerprint)
    passed = sum(1 for r in results if r["ok"])
    print(f"\n{passed}/{len(results)} passed · report: e2e/report/report.html")
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
