# -*- coding: utf-8 -*-
"""Turn the family dog into the app icon and TV banner.

A photograph is mush at 108dp, so this is not a photo -- it is an orange
silhouette with the photo's own shading carried in the alpha channel. Dark pixels
(the dog) are opaque orange; light pixels (the wall behind him, and the bright
iris of his eye) go transparent and let the icon background show through. That
inversion is what makes it work: the one bright thing on a black dog is his eye,
so it punches through dark, which is exactly where a face needs contrast.

Alpha rather than a baked dark background, because an adaptive icon is masked to
whatever shape the launcher wants -- a circle on Google TV -- and a baked square
shows its own edge inside that circle.
"""
import os, sys
import numpy as np
from PIL import Image, ImageFilter

sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(ROOT, "art", "dog.jpg")
OUT = os.path.join(ROOT, "tv-app", "app", "src", "main", "res", "drawable-xxxhdpi")
os.makedirs(OUT, exist_ok=True)

ORANGE = (244, 117, 33)
BG = (23, 23, 34)          # must match drawable/ic_launcher_background.xml


# The whole face, in fractions of the source. The photo already cuts off the back
# of his head at the left edge, so the box runs to it; the padding below gives the
# nose some air on the right instead of leaving it jammed against the frame.
FACE = (0.00, 0.042, 1.00, 0.915)     # left, top, right, bottom
PAD_LEFT, PAD_RIGHT = 0.10, 0.06      # as a fraction of the face box width


def silhouette(size, lo, hi, gamma):
    im = Image.open(SRC).convert("RGB")
    W, H = im.size
    box = (int(FACE[0] * W), int(FACE[1] * H), int(FACE[2] * W), int(FACE[3] * H))
    face = im.crop(box)
    fw, fh = face.size

    # Squared off by padding rather than by cropping tighter, so the whole face
    # survives. White, because the duotone below maps light to transparent — so
    # the padding simply is not there in the output.
    pl, pr = int(fw * PAD_LEFT), int(fw * PAD_RIGHT)
    side = max(fw + pl + pr, fh)
    square = Image.new("RGB", (side, side), (255, 255, 255))
    square.paste(face, (pl + (side - (fw + pl + pr)) // 2, (side - fh) // 2))
    im = square.resize((size, size), Image.LANCZOS)
    # Fur is high-frequency noise that turns to mud once contrast is stretched
    # this hard.
    im = im.filter(ImageFilter.GaussianBlur(radius=size / 400.0))

    lum = np.asarray(im.convert("L"), dtype=float) / 255.0
    # Stretched around where the fur actually sits, not across the whole range,
    # or he comes out one flat block of orange with no face in it.
    t = np.clip((lum - lo) / (hi - lo), 0.0, 1.0) ** gamma

    alpha = 1.0 - t

    # Feather to nothing at the edges. The source photo cuts off the back of his
    # head, so the fur runs straight into the frame border -- without this the
    # silhouette ends in a hard vertical line that reads as a rendering bug
    # rather than a crop. A radial falloff turns it into a vignette instead.
    yy, xx = np.mgrid[0:size, 0:size]
    r = np.sqrt(((xx - size / 2) / (size / 2)) ** 2 + ((yy - size / 2) / (size / 2)) ** 2)
    # Starts past the inscribed circle so the vignette only really bites in the
    # corners the launcher mask throws away anyway — otherwise it eats his muzzle.
    fade = np.clip((1.12 - r) / 0.42, 0.0, 1.0)
    alpha *= fade * fade * (3 - 2 * fade)      # smoothstep

    out = np.zeros((size, size, 4), dtype=np.uint8)
    out[..., 0], out[..., 1], out[..., 2] = ORANGE
    out[..., 3] = (alpha * 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


# --- adaptive icon foreground -------------------------------------------------
# Only the middle 66 of 108 is guaranteed to survive the launcher's mask, so the
# dog is scaled into that and the rest is bleed.
dog = silhouette(1024, lo=0.05, hi=0.60, gamma=0.85)
canvas = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
safe = int(1024 * 52 / 108)
canvas.paste(dog.resize((safe, safe), Image.LANCZOS), ((1024 - safe) // 2,) * 2)
canvas.resize((432, 432), Image.LANCZOS).save(
    os.path.join(OUT, "ic_launcher_foreground.png"), optimize=True)   # 108dp at xxxhdpi

# --- TV banner ----------------------------------------------------------------
# 320x180 in the leanback row, drawn at 4x. Landscape, so the dog sits left and
# the name beside him.
from PIL import ImageDraw, ImageFont
banner = Image.new("RGBA", (1280, 720), BG + (255,))
face = silhouette(560, lo=0.05, hi=0.60, gamma=0.85)
banner.alpha_composite(face, (60, 80))

draw = ImageDraw.Draw(banner)
face_right = 60 + 560
for path in (r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf"):
    if not os.path.exists(path):
        continue
    # Sized to the room that is actually left rather than guessed — the first
    # guess ran the name off the end of the banner.
    for pt in range(120, 40, -4):
        font = ImageFont.truetype(path, pt)
        if draw.textlength("CrunchyList", font=font) <= 1280 - face_right - 110:
            break
    draw.text((face_right + 40, (720 - pt) // 2 - 8), "CrunchyList",
              font=font, fill=(255, 255, 255, 255))
    break
banner.convert("RGB").save(os.path.join(OUT, "banner.png"), optimize=True)   # 320x180dp at xxxhdpi

print("wrote ic_launcher_foreground.png and banner.png to", OUT)
