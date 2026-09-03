"""Renders every section 8 widget at every grid cell, on every device, at several densities.

    python e2e/widget_sizing.py                 # the whole matrix
    python e2e/widget_sizing.py --skip-build    # reuse the installed APKs
    python e2e/widget_sizing.py emulator-5554   # one device

`WidgetSizingTest` composes each widget at an explicit dp cell and paints it over
magenta, so anything the widget fails to cover shows up as magenta. That answers
what a launcher grid does to the composition. It does not answer what a *device*
does to it: dp become pixels at the display's density, the bitmaps the widgets
draw are sized in pixels, and `Render`'s pixel budget only bites on a dense
screen. So the same cells are rendered again at each density below, and the
uncovered fraction of every PNG is measured rather than eyeballed.

Screens are put back the way they were found, in `finally`, the same as the
clock in run.py.
"""

import pathlib
import re
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "e2e" / "report" / "widget-sizing"
PKG = "tech.idct.weighttracker.debug"
RUNNER = f"{PKG}.test/androidx.test.runner.AndroidJUnitRunner"
TEST = "tech.idct.weighttracker.e2e.WidgetSizingTest"
DEVICE_DIR = f"/storage/emulated/0/Android/data/{PKG}/files/widget-sizing"

# name, size (None = the device's own), density, font scale, what it is there to prove.
PROFILES = {
    "emulator-5554": [
        ("native-480", None, 480, 1.0,
         "the device as shipped, 3.0x"),
        ("xxxhdpi-560", "1440x3200", 560, 1.0,
         "3.5x: the densest screen sold, and the only one where Render's pixel budget bites"),
        ("hdpi-320-text130", "1080x2400", 320, 1.3,
         "2.0x with the reader's text at 1.3x: both squeezes at once"),
    ],
    "emulator-5556": [
        ("native-480", None, 480, 1.0,
         "the device as shipped, 3.0x"),
        ("xhdpi-420", "1080x2400", 420, 1.0,
         "2.625x, the commonest phone density there is"),
        ("mdpi-240", "720x1600", 240, 1.0,
         "1.5x: the fewest pixels any of this has to work in"),
    ],
}

# Anything above this much magenta is the widget failing to fill the cell it was
# given, which is the whole fault the harness exists to catch. Rounded corners
# are the honest part of it: a 110x40 strip is nearly a fifth corner by area.
UNCOVERED_LIMIT = 0.06


def adb(serial, *args, timeout=600):
    return subprocess.run(
        ["adb", "-s", serial, *args], capture_output=True, text=True, timeout=timeout
    ).stdout


def display(serial):
    """What the screen is now, so it can be given back exactly."""
    size = adb(serial, "shell", "wm", "size")
    density = adb(serial, "shell", "wm", "density")
    font = adb(serial, "shell", "settings", "get", "system", "font_scale").strip()
    return {
        "size": re.search(r"Physical size: (\S+)", size).group(1),
        "density": re.search(r"Physical density: (\d+)", density).group(1),
        "font": font if font and font != "null" else "1.0",
    }


def apply_display(serial, size, density, font):
    adb(serial, "shell", "wm", "size", size if size else "reset")
    adb(serial, "shell", "wm", "density", str(density))
    adb(serial, "shell", "settings", "put", "system", "font_scale", str(font))
    # A resize restarts the system UI; the instrumentation has to start in a
    # process that already believes the new configuration, not race it.
    for _ in range(40):
        if adb(serial, "shell", "getprop", "sys.boot_completed").strip() == "1":
            if str(density) in adb(serial, "shell", "wm", "density"):
                return
        time.sleep(0.25)


def build_and_install():
    print("building and installing app + test APKs ...")
    gradle = str(ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew"))
    r = subprocess.run(
        [gradle, ":app:installDebug", ":app:installDebugAndroidTest", "-q"],
        cwd=ROOT, capture_output=True, text=True, timeout=1800,
    )
    if r.returncode != 0:
        print(r.stdout[-4000:])
        print(r.stderr[-4000:])
        sys.exit("install failed")


def uncovered(path):
    """The fraction of the cell the widget left showing the wallpaper."""
    from PIL import Image

    with Image.open(path) as im:
        im = im.convert("RGB")
        pixels = im.width * im.height
        counts = im.getcolors(pixels * 2) or []
        magenta = sum(n for n, c in counts if c == (255, 0, 255))
        return magenta / pixels, im.size


def thumbnail(src, dst, width=560):
    from PIL import Image

    with Image.open(src) as im:
        im = im.convert("RGB")
        if im.width > width:
            im.thumbnail((width, width * 4), Image.LANCZOS)
        im.save(dst, "PNG", optimize=True)


def run(serial, profile):
    name, size, density, font, note = profile
    apply_display(serial, size, density, font)
    adb(serial, "shell", "am", "force-stop", PKG)
    adb(serial, "shell", "rm", "-rf", DEVICE_DIR)

    started = time.time()
    out = adb(serial, "shell", "am", "instrument", "-w", "-e", "class", TEST, RUNNER, timeout=900)
    ran = bool(re.search(r"OK \(\d+ test", out)) and "FAILURES" not in out

    shots = OUT / f"{serial}_{name}"
    shots.mkdir(parents=True, exist_ok=True)
    for old in shots.glob("*.png"):
        old.unlink()
    raw = ROOT / "e2e" / "report" / ".raw" / f"{serial}_{name}"
    raw.mkdir(parents=True, exist_ok=True)
    for old in raw.glob("*.png"):
        old.unlink()
    adb(serial, "pull", DEVICE_DIR + "/.", str(raw))

    metrics = re.search(r"density=(\S+) fontScale=(\S+)", out)
    cells = []
    for png in sorted(raw.glob("*.png")):
        frac, dims = uncovered(png)
        thumbnail(png, shots / png.name)
        cells.append({
            "file": png.name,
            "px": f"{dims[0]}x{dims[1]}",
            "uncovered": frac,
            "ok": frac <= UNCOVERED_LIMIT,
        })

    return {
        "serial": serial, "profile": name, "note": note,
        "size": size or "device", "density": density, "font": font,
        "reported": metrics.group(0) if metrics else "",
        "ok": ran and bool(cells) and all(c["ok"] for c in cells),
        "ran": ran, "duration": time.time() - started, "cells": cells,
        "log": "" if ran else "\n".join(out.splitlines()[-30:]),
    }


def render(runs):
    stamp = time.strftime("%Y-%m-%d %H:%M")
    passed = sum(1 for r in runs if r["ok"])
    worst = max((c["uncovered"] for r in runs for c in r["cells"]), default=0)

    rows = "\n".join(
        f'<tr><td><a href="#{r["serial"]}_{r["profile"]}">{r["serial"]} &middot; {r["profile"]}</a></td>'
        f'<td>{r["size"]}</td><td>{r["density"]} dpi</td><td>{r["font"]}x</td>'
        f'<td class="{"pass" if r["ok"] else "fail"}">{"PASS" if r["ok"] else "FAIL"}</td>'
        f'<td>{len(r["cells"])}</td><td>{r["duration"]:.0f}s</td><td>{r["note"]}</td></tr>'
        for r in runs
    )

    sections = []
    for r in runs:
        figures = "".join(
            f'<figure class="{"" if c["ok"] else "bad"}">'
            f'<img src="{r["serial"]}_{r["profile"]}/{c["file"]}" loading="lazy">'
            f'<figcaption>{c["file"][:-4].replace("_", " &middot; ")}<br>'
            f'<span class="px">{c["px"]} px &middot; {c["uncovered"] * 100:.1f}% uncovered</span>'
            f'</figcaption></figure>'
            for c in r["cells"]
        )
        sections.append(
            f'<section id="{r["serial"]}_{r["profile"]}">'
            f'<h2>{r["serial"]} &middot; {r["profile"]} '
            f'<span class="{"pass" if r["ok"] else "fail"}">{"PASS" if r["ok"] else "FAIL"}</span></h2>'
            f'<p>{r["note"]} &mdash; {r["size"]} at {r["density"]} dpi, text {r["font"]}x. '
            f'<em>{r["reported"]}</em></p>'
            + (f'<pre>{r["log"]}</pre>' if r["log"] else "")
            + f'<div class="shots">{figures}</div></section>'
        )

    html = f"""<!doctype html>
<meta charset="utf-8">
<title>Weight Tracker &middot; widget sizing</title>
<style>
 body {{ font: 15px/1.5 system-ui, sans-serif; margin: 40px auto; max-width: 1180px; padding: 0 20px; color: #222; }}
 h1 {{ font-weight: 300; font-size: 34px; }} h2 {{ margin-top: 48px; }}
 table {{ border-collapse: collapse; width: 100%; }}
 td, th {{ border-bottom: 1px solid #e4e4e4; padding: 8px 10px; text-align: left; vertical-align: top; }}
 .pass {{ color: #2e9a5e; font-weight: 600; }} .fail {{ color: #a9720f; font-weight: 600; }}
 .shots {{ display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-end; }}
 figure {{ margin: 0; max-width: 280px; }}
 figure.bad img {{ outline: 3px solid #a9720f; }}
 img {{ max-width: 280px; border: 1px solid #e4e4e4; border-radius: 10px; background: #101010; }}
 figcaption {{ font-size: 12px; color: #6b6b6b; text-align: center; }}
 .px {{ font-size: 11px; color: #9a9a9a; }}
 pre {{ background: #f6f6f6; border: 1px solid #e4e4e4; border-radius: 8px; padding: 12px; overflow-x: auto; font-size: 12px; }}
 .meta {{ color: #6b6b6b; }}
</style>
<h1>Weight Tracker &middot; widget sizing</h1>
<p class="meta">{stamp} &middot; {passed}/{len(runs)} profiles passed &middot; worst uncovered cell
{worst * 100:.1f}% (limit {UNCOVERED_LIMIT * 100:.0f}%, and rounded corners are most of it).<br>
Every widget composed at an explicit dp cell and painted over magenta, so whatever the widget did not
cover is visible as magenta. The same cells are rendered on each device at each density below: dp become
pixels at the display's density, so density decides how large every bitmap the widgets draw actually is.</p>
<table><tr><th>Run</th><th>Resolution</th><th>Density</th><th>Text</th><th>Result</th>
<th>Cells</th><th>Time</th><th>What it adds</th></tr>{rows}</table>
{"".join(sections)}
"""
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "index.html").write_text(html, encoding="utf-8")


def main():
    wanted = [a for a in sys.argv[1:] if not a.startswith("--")]
    serials = [s for s in PROFILES if not wanted or s in wanted]
    if not serials:
        sys.exit(f"no device matches {wanted}; known: {', '.join(PROFILES)}")

    if "--skip-build" not in sys.argv:
        build_and_install()

    runs = []
    for serial in serials:
        was = display(serial)
        print(f"{serial}: found {was['size']} at {was['density']} dpi, text {was['font']}x")
        try:
            for profile in PROFILES[serial]:
                print(f"  {profile[0]:18s} ... ", end="", flush=True)
                r = run(serial, profile)
                runs.append(r)
                bad = sum(1 for c in r["cells"] if not c["ok"])
                print(("PASS" if r["ok"] else "FAIL")
                      + f"  {r['duration']:.0f}s  {len(r['cells'])} cells"
                      + (f"  {bad} not filling the cell" if bad else ""))
        finally:
            apply_display(serial, was["size"], was["density"], was["font"])
            print(f"{serial}: screen given back - {display(serial)}")

    render(runs)
    passed = sum(1 for r in runs if r["ok"])
    print(f"\n{passed}/{len(runs)} profiles passed - sheet: e2e/report/widget-sizing/index.html")
    sys.exit(0 if passed == len(runs) else 1)


if __name__ == "__main__":
    main()
