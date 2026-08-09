# -*- coding: utf-8 -*-
"""Build the app icon and the Google TV banner.

Source art: `art/labrador-head.png` — "Labrador head" by Delapouite,
game-icons.net, CC BY 3.0. That licence requires attribution wherever the icon
travels, so the credit is in the README *and* on the Settings screen: the README
covers the repo, the Settings line covers the APK, which is distributed on its
own from the Releases page.

A flat mark rather than a photograph, after trying both. The family dog's actual
photo is still in `art/dog.jpg`; it looked good at 256px and was a dark blob at
48px, which is the size that decides whether a kid can find the app.

The artwork is a white shape on opaque black, so luminance is used as the alpha
mask — otherwise the black would come through as a black square sitting inside
the launcher's circular mask.
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(ROOT, "art", "labrador-head.png")
OUT = os.path.join(ROOT, "tv-app", "app", "src", "main", "res", "drawable-xxxhdpi")
os.makedirs(OUT, exist_ok=True)

# The `local` flavour renames the app for a private build. The name is drawn into
# the banner artwork rather than laid over it by the launcher, so that flavour
# needs its own file — generated here from whatever name it declares, so this
# script never has to know what that name is. src/local/ is gitignored.
LOCAL = os.path.join(ROOT, "tv-app", "app", "src", "local", "res")


def local_app_name():
    strings = os.path.join(LOCAL, "values", "strings.xml")
    if not os.path.exists(strings):
        return None
    import re
    text = open(strings, encoding="utf-8").read()
    m = re.search(r'<string\s+name="app_name"\s*>([^<]+)</string>', text)
    return m.group(1).strip() if m else None

ORANGE = (244, 117, 33)
CHARCOAL = (23, 23, 34)      # matches drawable/ic_launcher_background.xml

# Adaptive icons guarantee only a 66dp circle out of the 108dp canvas. The ears
# stick out at roughly the widest point of that circle, so the mark is fitted to
# it rather than to the square — cropped ears would be the one thing you notice.
SAFE_FRACTION = 0.60


def mark(size, colour, fraction=SAFE_FRACTION):
    """The dog, in `colour`, on a transparent canvas."""
    src = Image.open(SRC).convert("L")
    a = np.asarray(src)

    # Trim to the artwork itself; the source has uneven margins, and centring the
    # canvas instead of the shape leaves it visibly off to one side.
    ys, xs = np.where(a > 24)
    shape = src.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))

    target = int(size * fraction)
    w, h = shape.size
    s = target / max(w, h)
    shape = shape.resize((max(1, int(w * s)), max(1, int(h * s))), Image.LANCZOS)

    alpha = Image.new("L", (size, size), 0)
    alpha.paste(shape, ((size - shape.width) // 2, (size - shape.height) // 2))

    out = Image.new("RGBA", (size, size), colour + (0,))
    out.putalpha(alpha)
    return out


# --- adaptive icon foreground -------------------------------------------------
# Transparent surround, so the charcoal background layer shows through and the
# launcher can mask it to a circle, a squircle or whatever it likes.
mark(1024, ORANGE).resize((432, 432), Image.LANCZOS).save(
    os.path.join(OUT, "ic_launcher_foreground.png"), optimize=True)

# --- TV banners ---------------------------------------------------------------
# 320x180dp in the leanback app row, drawn at 4x. One per flavour, because the
# name is drawn into the artwork rather than laid over it by the launcher.
def build_banner(name, path_out):
    banner = Image.new("RGBA", (1280, 720), CHARCOAL + (255,))
    d = 620
    banner.alpha_composite(mark(d, ORANGE, fraction=0.90), (60, (720 - d) // 2))

    draw = ImageDraw.Draw(banner)
    right = 60 + d
    for font_path in (r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf"):
        if not os.path.exists(font_path):
            continue
        # Sized to the room that is actually left rather than guessed, since the
        # local flavour's name can be any length.
        for pt in range(150, 40, -4):
            font = ImageFont.truetype(font_path, pt)
            if draw.textlength(name, font=font) <= 1280 - right - 90:
                break
        draw.text((right + 20, (720 - pt) // 2 - 8), name, font=font, fill=(255, 255, 255))
        break
    banner.convert("RGB").save(path_out, optimize=True)
    return path_out


print("wrote", build_banner("CrunchyList", os.path.join(OUT, "banner.png")))
print("wrote", os.path.join(OUT, "ic_launcher_foreground.png"))

name = local_app_name()
if name:
    out = os.path.join(LOCAL, "drawable-xxxhdpi")
    os.makedirs(out, exist_ok=True)
    print("wrote", build_banner(name, os.path.join(out, "banner.png")), "(local flavour)")
else:
    print("no src/local/res/values/strings.xml — skipping the local banner")
