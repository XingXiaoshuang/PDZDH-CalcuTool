#!/usr/bin/env python3
"""Launcher icon for 配电工具箱 (Power Distribution Toolbox).

Style follows the two Chinese grid utilities: deep navy field (CSG blue), a light
toolbox outline and a national-grid green bolt. Geometry is authored in the 108x108
adaptive-icon viewport and rasterised with 4x supersampling for legacy densities.

Run:  python tools/gen_icon.py
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

BG_INNER = (0x10, 0x33, 0x54)
BG_OUTER = (0x05, 0x0F, 0x1A)
GRID = (0xFF, 0xFF, 0xFF, 0x12)
BODY = (0xEA, 0xF4, 0xFF)
BLUE = (0x1E, 0x76, 0xD9)
GREEN = (0x00, 0xB4, 0x5A)
GREEN_HI = (0x4C, 0xE3, 0x8F)

BODY_RECT = (28.0, 41.0, 80.0, 76.0)
BODY_R = 7.0
STROKE = 3.4
HANDLE = (45.0, 30.0, 63.0, 42.0, 2.8)
BOLT = [(57.5, 43.5), (47.5, 58.5), (53.5, 58.5), (50.5, 72.0),
        (62.5, 56.5), (56.0, 56.5)]


def background(size):
    low = Image.new("RGB", (108, 108))
    px = low.load()
    cx, cy = 50, 40
    for y in range(108):
        for x in range(108):
            d = math.hypot(x - cx, y - cy) / 96.0
            k = min(1.0, max(0.0, d)) ** 0.95
            px[x, y] = tuple(int(BG_INNER[i] + (BG_OUTER[i] - BG_INNER[i]) * k) for i in range(3))
    return low.resize((size, size), Image.BICUBIC)


def grid_lines(d, k, w, h):
    for i in range(1, 5):
        x = i * 108 / 5.0 * k
        d.line([(x, 0), (x, h)], fill=GRID, width=max(1, int(0.7 * k)))
    for i in range(1, 4):
        y = i * 108 / 4.0 * k
        d.line([(0, y), (w, y)], fill=GRID, width=max(1, int(0.7 * k)))


def render(size, content_scale=1.0, mask=None):
    ss = 4
    s = size * ss
    img = background(s)
    d = ImageDraw.Draw(img, "RGBA")
    k = s / 108.0 * content_scale
    ox = (s - 108.0 * k) / 2.0
    oy = (s - 108.0 * k) / 2.0

    def K(v):
        return ox + v * k

    def KY(v):
        return oy + v * k

    grid_lines(d, k, s, s)

    bx0, by0, bx1, by1 = BODY_RECT
    hx0, hy0, hx1, hy1, hr = HANDLE

    # 蓝色底座（南网蓝）
    d.rounded_rectangle([K(bx0), KY(60), K(bx1), KY(by1)], radius=BODY_R * k,
                        fill=BLUE + (255,))
    # 箱体白描边
    d.rounded_rectangle([K(bx0), KY(by0), K(bx1), KY(by1)], radius=BODY_R * k,
                        outline=BODY + (255,), width=int(round(STROKE * k)))
    # 提手
    d.line([(K(hx0), KY(50)), (K(hx0), KY(hy0 + hr))], fill=BODY + (255,),
           width=int(round(STROKE * k)))
    d.arc([K(hx0), KY(hy0), K(hx1), KY(hy0 + hr * 2)], 180, 360,
          fill=BODY + (255,), width=int(round(STROKE * k)))
    d.line([(K(hx1), KY(hy0 + hr)), (K(hx1), KY(50))], fill=BODY + (255,),
           width=int(round(STROKE * k)))
    # 闪电（国网绿）
    d.polygon([(K(x), KY(y)) for x, y in BOLT], fill=GREEN + (255,))
    d.line([(K(BOLT[0][0]), KY(BOLT[0][1])), (K(BOLT[1][0]), KY(BOLT[1][1]))],
           fill=GREEN_HI + (255,), width=max(1, int(1.2 * k)))

    if mask is not None:
        img = apply_mask(img, mask(s))
    return img.resize((size, size), Image.LANCZOS)


def rrect_mask(size, radius_ratio=0.235):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1],
                                           radius=int(size * radius_ratio), fill=255)
    return mask


def circle_mask(size):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def apply_mask(img, mask):
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def write_pngs():
    for name, px in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        render(px, 1.30, rrect_mask).save(os.path.join(folder, "ic_launcher.png"))
        render(px, 1.30, circle_mask).save(os.path.join(folder, "ic_launcher_round.png"))
    os.makedirs(os.path.join(ROOT, "icon"), exist_ok=True)
    render(512, 1.06).convert("RGB").save(os.path.join(ROOT, "icon", "icon-512.png"))


def bolt_path():
    return " ".join("M%.2f,%.2f" % (x, y) for x, y in BOLT) + " Z"


def rr_path(x0, y0, x1, y1, r, bottom_only=False):
    """圆角矩形路径（bottom_only 时只圆下面两角）。"""
    if bottom_only:
        return (f"M{fmt(x0)},{fmt(y0)} L{fmt(x1)},{fmt(y0)} L{fmt(x1)},{fmt(y1 - r)} "
                f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x1 - r)},{fmt(y1)} "
                f"L{fmt(x0 + r)},{fmt(y1)} "
                f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x0)},{fmt(y1 - r)} Z")
    return (f"M{fmt(x0 + r)},{fmt(y0)} L{fmt(x1 - r)},{fmt(y0)} "
            f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x1)},{fmt(y0 + r)} "
            f"L{fmt(x1)},{fmt(y1 - r)} "
            f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x1 - r)},{fmt(y1)} "
            f"L{fmt(x0 + r)},{fmt(y1)} "
            f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x0)},{fmt(y1 - r)} "
            f"L{fmt(x0)},{fmt(y0 + r)} "
            f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x0 + r)},{fmt(y0)} Z")


def fmt(v):
    return ("%.2f" % v).rstrip("0").rstrip(".")


def vector_foreground():
    bx0, by0, bx1, by1 = BODY_RECT
    hx0, hy0, hx1, hy1, hr = HANDLE
    parts = []

    base = rr_path(bx0, 60, bx1, by1, BODY_R, bottom_only=True)
    body = rr_path(bx0, by0, bx1, by1, BODY_R)
    handle = (f"M{fmt(hx0)},50 L{fmt(hx0)},{fmt(hy0 + hr)} "
              f"A{fmt(hr)},{fmt(hr)} 0 0 1 {fmt(hx1)},{fmt(hy0 + hr)} "
              f"L{fmt(hx1)},50")
    bolt = " ".join(f"M{fmt(x)},{fmt(y)}" for x, y in BOLT) + " Z"

    parts.append("    <!-- 蓝色底座（南网蓝） -->\n"
                 "    <path\n"
                 "        android:fillColor=\"#1E76D9\"\n"
                 f"        android:pathData=\"{base}\" />")
    parts.append("    <!-- 箱体 -->\n"
                 "    <path\n"
                 "        android:fillColor=\"@android:color/transparent\"\n"
                 "        android:strokeColor=\"#EAF4FF\"\n"
                 f"        android:strokeWidth=\"{fmt(STROKE)}\"\n"
                 "        android:strokeLineJoin=\"round\"\n"
                 f"        android:pathData=\"{body}\" />")
    parts.append("    <!-- 提手 -->\n"
                 "    <path\n"
                 "        android:fillColor=\"@android:color/transparent\"\n"
                 "        android:strokeColor=\"#EAF4FF\"\n"
                 f"        android:strokeWidth=\"{fmt(STROKE)}\"\n"
                 "        android:strokeLineCap=\"round\"\n"
                 f"        android:pathData=\"{handle}\" />")
    parts.append("    <!-- 闪电（国网绿） -->\n"
                 "    <path\n"
                 "        android:fillColor=\"#00B45A\"\n"
                 f"        android:pathData=\"{bolt}\" />")

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
          '        android:centerX="0.46"\n'
          '        android:centerY="0.37"\n'
          '        android:gradientRadius="86dp"\n'
          '        android:startColor="#103354"\n'
          '        android:endColor="#050F1A" />\n'
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
