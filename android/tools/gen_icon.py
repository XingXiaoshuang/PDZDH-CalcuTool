#!/usr/bin/env python3
"""Generate the launcher icon set for the Android build.

Design: the three phase phasors of the app itself (A up / B lower-left / C lower-right,
120 degrees apart) over a dark violet radial field, with a faint reference ring and a
thin triangle linking the tips. Geometry is authored in a 108x108 adaptive-icon viewport,
rasterised with 4x supersampling for legacy densities.

Run:  python tools/gen_icon.py
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

CENTER = (54.0, 54.0)
LEN = 24.4
HEAD_L = 6.8
HEAD_W = 4.4
STROKE = 4.4
RING_R = 32.6

PHASES = [
    (90.0, (0xFF, 0xD2, 0x4A)),   # A - amber
    (210.0, (0x53, 0xDA, 0xAF)),  # B - teal
    (330.0, (0xFF, 0x7A, 0x6B)),  # C - coral
]

BG_INNER = (0x1E, 0x1A, 0x38)
BG_OUTER = (0x0A, 0x09, 0x0F)
RING = (0x2E, 0x2A, 0x46)
LINK = (0xFF, 0xFF, 0xFF, 0x1A)


def tip(deg):
    rad = math.radians(deg)
    return (CENTER[0] + LEN * math.cos(rad), CENTER[1] - LEN * math.sin(rad))


def arrow(deg):
    """Return (line_start, line_end, [triangle points]) for one phasor."""
    rad = math.radians(deg)
    d = (math.cos(rad), -math.sin(rad))
    t = tip(deg)
    base = (t[0] - HEAD_L * d[0], t[1] - HEAD_L * d[1])
    p = (-d[1] * HEAD_W, d[0] * HEAD_W)
    tri = [t, (base[0] + p[0], base[1] + p[1]), (base[0] - p[0], base[1] - p[1])]
    return CENTER, base, tri


def rrect_mask(size, radius_ratio=0.235):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1],
                                           radius=int(size * radius_ratio), fill=255)
    return mask


def circle_mask(size):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def background(size):
    """Radial gradient, low-res gradient sampled then upscaled."""
    low = Image.new("RGB", (108, 108))
    px = low.load()
    cx, cy = 54 * 0.5, 108 * 0.35
    for y in range(108):
        for x in range(108):
            d = math.hypot(x - cx, y - cy) / 92.0
            k = min(1.0, max(0.0, d)) ** 0.92
            px[x, y] = tuple(int(BG_INNER[i] + (BG_OUTER[i] - BG_INNER[i]) * k) for i in range(3))
    return low.resize((size, size), Image.BICUBIC)


def render(size, content_scale=1.0, mask=None):
    ss = 4
    s = size * ss
    img = background(s)
    d = ImageDraw.Draw(img, "RGBA")
    k = s / 108.0 * content_scale
    cx, cy = s / 2.0, s / 2.0

    # reference ring
    rr = RING_R * k
    d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=RING + (255,), width=max(1, int(1.2 * k)))

    # linking triangle between tips
    pts = [(cx + (tip(deg)[0] - CENTER[0]) * k, cy + (tip(deg)[1] - CENTER[1]) * k)
           for deg, _ in PHASES]
    d.line(pts + [pts[0]], fill=LINK, width=max(1, int(1.1 * k)), joint="curve")

    # phasors
    for deg, color in PHASES:
        _, base, tri = arrow(deg)
        bx, by = cx + (base[0] - CENTER[0]) * k, cy + (base[1] - CENTER[1]) * k
        d.line([(cx, cy), (bx, by)], fill=color + (255,), width=int(round(STROKE * k)))
        d.polygon([(cx + (p[0] - CENTER[0]) * k, cy + (p[1] - CENTER[1]) * k) for p in tri],
                  fill=color + (255,))
        # round the line start so the origin stays clean
        d.ellipse([cx - STROKE * k / 2, cy - STROKE * k / 2,
                   cx + STROKE * k / 2, cy + STROKE * k / 2], fill=color + (255,))

    # dark hub keeps the three tails visually separated at the neutral point
    hr = 3.9 * k
    d.ellipse([cx - hr, cy - hr, cx + hr, cy + hr], fill=(0x12, 0x10, 0x1B, 255))

    if mask is not None:
        img = apply_mask(img, mask(s))
    return img.resize((size, size), Image.LANCZOS)


def apply_mask(img, mask):
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def write_pngs():
    for name, px in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        render(px, 1.42, rrect_mask).save(os.path.join(folder, "ic_launcher.png"))
        render(px, 1.42, circle_mask).save(os.path.join(folder, "ic_launcher_round.png"))
    # store / README preview
    os.makedirs(os.path.join(ROOT, "icon"), exist_ok=True)
    render(512, content_scale=1.06).convert("RGB").save(os.path.join(ROOT, "icon", "icon-512.png"))


def f(v):
    return ("%.2f" % v).rstrip("0").rstrip(".")


def vector_foreground():
    parts = []
    parts.append('    <!-- reference ring -->')
    parts.append('    <path\n'
                 '        android:fillColor="#00000000"\n'
                 '        android:strokeColor="#2E2A46"\n'
                 '        android:strokeWidth="1.2"\n'
                 '        android:pathData="M%.2f,%.2f a%.2f,%.2f 0 1,0 %.2f,0 a%.2f,%.2f 0 1,0 -%.2f,0" />'
                 % (CENTER[0] - RING_R, CENTER[1], RING_R, RING_R, RING_R * 2, RING_R, RING_R, RING_R * 2))
    tri = " ".join("L%.2f,%.2f" % tip(deg) for deg, _ in PHASES)
    parts.append('    <!-- tip triangle -->')
    parts.append('    <path\n'
                 '        android:fillColor="#00000000"\n'
                 '        android:strokeColor="#1AFFFFFF"\n'
                 '        android:strokeWidth="1.1"\n'
                 '        android:pathData="M%.2f,%.2f %s Z" />' % (tip(PHASES[0][0]) + (tri,)))
    for deg, color in PHASES:
        _, base, tri_pts = arrow(deg)
        hexc = "#FF%02X%02X%02X" % color
        parts.append('    <!-- %.0f deg phasor -->' % deg)
        parts.append('    <path\n'
                     '        android:fillColor="@android:color/transparent"\n'
                     '        android:strokeColor="%s"\n'
                     '        android:strokeWidth="4.6"\n'
                     '        android:strokeLineCap="round"\n'
                     '        android:pathData="M%.2f,%.2f L%.2f,%.2f" />'
                     % (hexc, CENTER[0], CENTER[1], base[0], base[1]))
        parts.append('    <path\n'
                     '        android:fillColor="%s"\n'
                     '        android:pathData="M%.2f,%.2f L%.2f,%.2f L%.2f,%.2f Z" />'
                     % (hexc, tri_pts[0][0], tri_pts[0][1], tri_pts[1][0], tri_pts[1][1],
                        tri_pts[2][0], tri_pts[2][1]))
    parts.append('    <!-- neutral hub -->')
    parts.append('    <path\n'
                 '        android:fillColor="#12101B"\n'
                 '        android:pathData="M%.2f,54 a3.9,3.9 0 1,0 7.8,0 a3.9,3.9 0 1,0 -7.8,0 Z" />'
                 % (CENTER[0] - 3.9))

    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="108dp"\n'
           '    android:height="108dp"\n'
           '    android:viewportWidth="108"\n'
           '    android:viewportHeight="108">\n'
           + "\n".join(parts) + "\n</vector>\n")
    with open(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), "w",
              encoding="utf-8") as fh:
        fh.write(xml)


def adaptive_xml():
    bg = ('<?xml version="1.0" encoding="utf-8"?>\n'
          '<shape xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:shape="rectangle">\n'
          '    <gradient\n'
          '        android:type="radial"\n'
          '        android:centerX="0.5"\n'
          '        android:centerY="0.35"\n'
          '        android:gradientRadius="84dp"\n'
          '        android:startColor="#1E1A38"\n'
          '        android:endColor="#0A090F" />\n'
          '</shape>\n')
    with open(os.path.join(RES, "drawable", "ic_launcher_background.xml"), "w",
              encoding="utf-8") as fh:
        fh.write(bg)

    for name in ("ic_launcher", "ic_launcher_round"):
        xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
               '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
               '    <background android:drawable="@drawable/ic_launcher_background" />\n'
               '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
               '</adaptive-icon>\n')
        with open(os.path.join(RES, "mipmap-anydpi-v26", name + ".xml"), "w",
                  encoding="utf-8") as fh:
            fh.write(xml)


if __name__ == "__main__":
    write_pngs()
    vector_foreground()
    adaptive_xml()
    print("icons written to", RES)
