#!/usr/bin/env python3
"""Refit assets/icon.png into the launcher's adaptive-icon layers.

    python assets/make_launcher_icon.py

Writes ic_launcher_background/foreground/monochrome.png into every
app/src/main/res/mipmap-*dpi directory.  Requires numpy, scipy and Pillow.

Why the source cannot simply be dropped in
------------------------------------------
assets/icon.png is a 768px iOS-style plate: one flat picture of a mesh gradient
with a white figure standing on it, its own rounded corners baked in.  Android
wants two 108dp layers that the launcher masks to whatever shape the device
uses, showing only the centre 72dp.  Used whole the plate would be rounded
twice and the figure's head and hands would be cropped away, so the plate is
taken apart and rebuilt to that geometry:

  background   the gradient alone, refitted and bled past the mask
  foreground   the figure alone, sized so the mask cuts it where the Play store
               icon is already cropped -- below the hands, through the hips
  monochrome   the same silhouette, alpha only, for Android 13 themed icons

The chart bars and trend lines in the source are deliberately dropped.  Unmixed
off the gradient they carry an alpha of about 0.03; at 48dp they are invisible,
and at the mask edge they survive only as clipped fragments.  The notification
icon (drawable/ic_notification.xml) is what carries the chart motif.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage
from scipy.spatial import ConvexHull

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "assets" / "icon.png"
RES = ROOT / "app" / "src" / "main" / "res"

SRC = 768                            # source is square at this size
PLATE = (28, 43, 740, 712)           # the rounded plate within it, l t r b

# Layout on the 108dp adaptive canvas.  A launcher shows the centre 72dp
# (18dp inset on each side) and masks it; anything outside is parallax margin.
CANVAS_DP = 108.0
PLATE_DP = 78.0                      # dp spanned by the whole source gradient
SPREAD = 1.30                        # squeezes the hue sweep into the mask
SAT = 1.05
HEAD_DP = 27.0                       # top of the head ...
FOOT_DP = 92.0                       # ... to past the bottom of the window

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


# --------------------------------------------------------------------------
# colour helpers (vectorised; colorsys is scalar-only)

def rgb_to_hsv(a):
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(-1), a.min(-1)
    d = mx - mn
    h = np.zeros_like(mx)
    m = (d > 1e-9) & (mx == r); h[m] = ((g - b)[m] / d[m]) % 6
    m = (d > 1e-9) & (mx == g); h[m] = ((b - r)[m] / d[m]) + 2
    m = (d > 1e-9) & (mx == b); h[m] = ((r - g)[m] / d[m]) + 4
    s = np.where(mx > 1e-9, d / np.maximum(mx, 1e-9), 0)
    return np.stack([h / 6.0, s, mx], -1)


def hsv_to_rgb(a):
    h, s, v = a[..., 0] % 1.0, a[..., 1], a[..., 2]
    i = np.floor(h * 6).astype(int)
    f = h * 6 - i
    p, q, t = v * (1 - s), v * (1 - f * s), v * (1 - (1 - f) * s)
    i = i % 6
    return np.stack([np.choose(i, [v, q, p, p, t, v]),
                     np.choose(i, [t, v, v, q, p, p]),
                     np.choose(i, [p, p, t, v, v, q])], -1)


# --------------------------------------------------------------------------
# the gradient

def _basis(nx, ny, deg):
    return [nx ** i * ny ** j for i in range(deg + 1) for j in range(deg + 1) if i + j <= deg]


class Gradient:
    """The plate's mesh gradient, fitted as smooth polynomials in HSV.

    Hue is fitted as a plain scalar: the sweep runs blue to red without crossing
    the end of the wheel, so no unwrapping is needed.  Fitting it as a cos/sin
    pair instead -- the usual trick for periodic data -- puts a hue singularity
    in the middle of the plate, where the two components both cross zero.
    Samples are weighted by saturation so washed-out pixels, whose hue is
    meaningless, cannot drag the fit.
    """

    DEG = {"h": 3, "s": 3, "v": 4}

    def __init__(self, rgb, usable):
        h, s, v = np.moveaxis(rgb_to_hsv(rgb / 255.0), -1, 0)
        H, W = usable.shape
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)
        l, t, r, b = PLATE
        nx = (xx - (l + r) / 2) / ((r - l) / 2)      # normalised to the plate
        ny = (yy - (t + b) / 2) / ((b - t) / 2)

        def fit(field, deg, sel, w):
            A = np.stack([f.ravel() for f in _basis(nx, ny, deg)], 1)[sel.ravel()]
            ww = np.sqrt(w.ravel()[sel.ravel()])[:, None]
            coef, *_ = np.linalg.lstsq(A * ww, field.ravel()[sel.ravel()] * ww[:, 0], rcond=None)
            return coef

        hue_ok = usable & (s > 0.25)
        self.c = {"h": fit(h, self.DEG["h"], hue_ok, s),
                  "s": fit(s, self.DEG["s"], hue_ok, np.ones_like(s)),
                  "v": fit(v, self.DEG["v"], usable, np.ones_like(v))}

    def _ev(self, key, nx, ny):
        return sum(c * t for c, t in zip(self.c[key], _basis(nx, ny, self.DEG[key])))

    def render(self, n):
        """The background layer at n px square."""
        extent = CANVAS_DP / PLATE_DP
        u = np.linspace(-extent, extent, n)
        nx, ny = np.meshgrid(u, u)
        # Clamping holds the end colours out into the parallax margin instead of
        # letting the polynomials run away outside the region they were fitted on.
        cx, cy = np.clip(nx * SPREAD, -1, 1), np.clip(ny * SPREAD, -1, 1)
        hsv = np.stack([np.clip(self._ev("h", cx, cy), 0, 1),
                        np.clip(self._ev("s", cx, cy) * SAT, 0, 1),
                        np.clip(self._ev("v", cx, cy), 0, 1)], -1)
        return Image.fromarray(np.clip(hsv_to_rgb(hsv) * 255, 0, 255).astype(np.uint8)).convert("RGBA")


# --------------------------------------------------------------------------
# the figure

def figure_mask(rgb, alpha):
    """Silhouette of the white figure.

    Two thresholds are needed.  A tight one finds the pure-white body but drops
    the blue-grey shadow under the chin, and the background then shows through
    the neck as a hole.  A loose one keeps that shadow, but would also swallow
    the figure's outer drop shadow, which is the same colour.  So the loose mask
    counts only inside a morphological closing of the tight one: concave pockets
    such as the neck fill in, the convex outside does not.
    """
    h, s, v = np.moveaxis(rgb_to_hsv(rgb / 255.0), -1, 0)
    white = (np.clip((v - 0.80) / 0.14, 0, 1)
             * np.clip((0.16 - s) / 0.12, 0, 1)
             * (alpha / 255.0))

    tight = white > 0.5
    lab, n = ndimage.label(tight)
    sizes = ndimage.sum(tight, lab, range(1, n + 1))
    tight = np.isin(lab, [i + 1 for i in np.argsort(sizes)[::-1][:2]])   # body, head
    tight = ndimage.binary_fill_holes(tight)

    r = 22
    Y, X = np.mgrid[-r:r + 1, -r:r + 1]
    pocket = ndimage.binary_closing(tight, structure=X * X + Y * Y <= r * r)
    loose = (v > 0.84) & (s < 0.34) & (alpha > 200)
    solid = ndimage.binary_closing(tight | (loose & pocket), structure=np.ones((3, 3)))

    # Pinholes and specks are invisible in the colour icon, white on white, but
    # show as dots once the monochrome layer is tinted.  The windows between arm
    # and torso are ~12k px each, far above the threshold, so they stay open.
    MIN = 500
    hl, hn = ndimage.label(~solid)
    for i, sz in enumerate(ndimage.sum(~solid, hl, range(1, hn + 1)), start=1):
        if sz < MIN:
            solid[hl == i] = True
    sl, sn = ndimage.label(solid)
    sizes = ndimage.sum(solid, sl, range(1, sn + 1))
    return np.isin(sl, [i + 1 for i, sz in enumerate(sizes) if sz >= MIN]), white


def figure_layer(rgb, solid, white, gradient_rgb):
    """The figure as standalone RGBA, lifted off the gradient it was painted on."""
    a = np.clip(np.maximum(solid.astype(np.float32),
                           white * ndimage.binary_dilation(solid, iterations=4)), 0, 1)
    a3 = a[..., None]
    inv = np.clip((rgb - gradient_rgb * (1 - a3)) / np.maximum(a3, 0.05), 0, 255)
    # Inverting the antialiased rim, where alpha is small, amplifies noise into
    # colour fringes that show up when the layer is scaled down.  Carry the
    # interior colour outwards instead and let alpha do the blending.
    core = a > 0.6
    idx = ndimage.distance_transform_edt(~core, return_distances=False, return_indices=True)
    return np.dstack([inv[idx[0], idx[1]], a * 255]).astype(np.uint8)


def smallest_circle(solid):
    """Centre and radius of the smallest circle enclosing the silhouette."""
    ys, xs = np.where(solid)
    pts = np.stack([xs, ys], 1).astype(np.float64)
    pts = pts[ConvexHull(pts).vertices]
    c = pts.mean(0)
    for step in (64, 16, 4, 1, 0.25):
        moved = True
        while moved:
            moved = False
            best = np.linalg.norm(pts - c, axis=1).max()
            for d in ((step, 0), (-step, 0), (0, step), (0, -step)):
                r = np.linalg.norm(pts - (c + d), axis=1).max()
                if r < best:
                    c, best, moved = c + d, r, True
    return c, np.linalg.norm(pts - c, axis=1).max()


# --------------------------------------------------------------------------

def foreground(fig_rgba, bbox, n, shadow=True, silhouette=False):
    px = n / CANVAS_DP
    x0, y0, x1, y1 = bbox
    scale = ((FOOT_DP - HEAD_DP) * px) / (y1 - y0)
    small = Image.fromarray(fig_rgba).resize((int(round(SRC * scale)),) * 2, Image.LANCZOS)
    if silhouette:
        band = np.array(small)[..., 3]
        small = Image.fromarray(np.dstack([np.zeros_like(band)] * 3 + [band]))
    off = (int(round(n / 2 - (x0 + x1) / 2 * scale)), int(round(HEAD_DP * px - y0 * scale)))

    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    if shadow:
        cast = Image.new("RGBA", (n, n), (0, 0, 0, 0))
        cast.paste(small, (off[0], off[1] + max(1, n // 180)), small)
        blur = ndimage.gaussian_filter(np.array(cast)[..., 3].astype(np.float32), n / 130.0) * 0.30
        out = Image.alpha_composite(out, Image.fromarray(np.dstack([
            np.full((n, n), 10.0), np.full((n, n), 24.0),
            np.full((n, n), 52.0), blur]).astype(np.uint8)))
    out.paste(small, off, small)
    return out


def main():
    src = np.array(Image.open(SOURCE).convert("RGBA")).astype(np.float32)
    rgb, alpha = src[..., :3], src[..., 3]

    solid, white = figure_mask(rgb, alpha)
    # Fit the gradient on plate pixels the figure and its shadow do not cover.
    usable = (alpha > 250) & ~ndimage.binary_dilation(solid, iterations=16)
    gradient = Gradient(rgb, usable)
    fig_rgba = figure_layer(rgb, solid, white, np.array(gradient.render(SRC)).astype(np.float32)[..., :3])

    ys, xs = np.where(solid)
    bbox = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    centre, radius = smallest_circle(solid)
    print(f"figure {solid.sum()} px, bbox {bbox}, enclosing circle r={radius:.0f}")

    for bucket, n in DENSITIES.items():
        out = RES / f"mipmap-{bucket}"
        out.mkdir(parents=True, exist_ok=True)
        gradient.render(n).save(out / "ic_launcher_background.png")
        foreground(fig_rgba, bbox, n).save(out / "ic_launcher_foreground.png")
        foreground(fig_rgba, bbox, n, shadow=False, silhouette=True).save(
            out / "ic_launcher_monochrome.png")
        print(f"  mipmap-{bucket}: {n}x{n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
