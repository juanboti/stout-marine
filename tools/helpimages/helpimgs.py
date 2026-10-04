"""Builds the annotated pictures for the in-app Help screen from the app's real deck renders."""
from PIL import Image, ImageDraw, ImageFont
import json, math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mock4

T = os.environ.get("DECKTEST", "/tmp/decktest")
OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/helpimgs"
os.makedirs(OUT, exist_ok=True)
FB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
ORANGE = (255, 170, 40); DARK = (24, 18, 14); BG = (24, 21, 20); BONE = (226, 214, 190)

def badge(d, x, y, n, r=30):
    d.ellipse([x - r - 3, y - r - 3, x + r + 3, y + r + 3], fill=DARK)
    d.ellipse([x - r, y - r, x + r, y + r], fill=ORANGE)
    d.text((x, y + 1), str(n), fill=DARK, font=ImageFont.truetype(FB, int(r * 1.25)), anchor="mm")

def arrow(d, a, b, col=ORANGE, w=8, head=26):
    (x0, y0), (x1, y1) = a, b
    L = math.hypot(x1 - x0, y1 - y0); ux, uy = (x1 - x0) / L, (y1 - y0) / L
    bx, by = x1 - ux * head, y1 - uy * head
    for c, ww in ((DARK, w + 6), (col, w)):
        d.line([x0, y0, bx, by], fill=c, width=ww)
    pts = [(x1, y1), (bx - uy * head * 0.6, by + ux * head * 0.6), (bx + uy * head * 0.6, by - ux * head * 0.6)]
    d.polygon(pts, fill=col, outline=DARK)

def deck_full(name):
    j = json.load(open(f"{T}/{name}.json"))
    art = Image.open(f"{T}/{name}_art.png").convert("RGB")
    s = j["artPx"]
    im = art.resize((round(art.width * s), round(art.height * s)), Image.NEAREST)
    return im, j

def by_key(j, k, kind=None):
    for b in j["buttons"]:
        if b["key"] == k and (kind is None or b["kind"] == kind):
            return b

# ------------------------------------------------------------ 1. the deck, numbered
def help_controls():
    im, j = deck_full("p")
    top = int(j["game"][3])
    im = im.crop((0, top, im.width, im.height)).convert("RGB")
    d = ImageDraw.Draw(im)
    def at(b, dx, dy): return (b["cx"] + dx, b["cy"] - top + dy)
    menu, gear, wl, wr = by_key(j, -6), by_key(j, -21), by_key(j, 42), by_key(j, 55)
    keys, mp, wait, fire = by_key(j, 0), by_key(j, -7), by_key(j, 57), by_key(j, -5)
    cells = [b for b in j["buttons"] if b["kind"] == 0]
    cx0 = min(b["x0"] for b in cells); cy0 = min(b["y0"] for b in cells) - top
    cw = cells[0]["x1"] - cells[0]["x0"]
    sl = [b for b in j["buttons"] if b["kind"] == 5]
    marks = [
        (1, at(menu, -18, 52)),
        (2, at(gear, 30, 46)),
        (3, (wl["x0"] + 10, (wl["y0"] + wl["y1"]) / 2 - top + 52)),
        (4, at(keys, -46, 42)),
        (5, at(mp, 40, 44)),
        (6, (cx0 + cw / 2, cy0 + cw / 2)),
        (8, at(wait, 0, 100)),
        (9, at(fire, 96, -110)),
    ] + [(7, (b["x0"] + 36 if b["key"] == 49 else b["x1"] - 36, b["y1"] - top + 32)) for b in sl]
    for n, (x, y) in marks:
        badge(d, x, y, n)
    return im

# ------------------------------------------------------------ 2. turn vs side-step (2 x 2)
def help_moves():
    ps = [mock4.panel(k) for k in ("turnL", "turnR", "stepL", "stepR")]
    W = 2 * 420 + 3 * 16; H = 2 * 640 + 3 * 16
    im = Image.new("RGB", (W, H), (12, 10, 10))
    for i, p in enumerate(ps):
        im.paste(p.convert("RGB"), (16 + (i % 2) * 436, 16 + (i // 2) * 656))
    return im

# ------------------------------------------------------------ 3. gestures on the game picture
def help_gestures():
    W, H = 900, 1200
    im = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(im)
    gx0, gy0, gx1, gy1 = 30, 30, W - 30, H - 30
    # neutral stand-in for the game picture (not game art)
    mock4_bg = (36, 40, 46)
    d.rectangle([gx0, gy0, gx1, gy1], fill=mock4_bg)
    cx, cy = (gx0 + gx1) / 2, gy0 + (gy1 - gy0) * 0.42
    iw, ih = 230, 190; fl = gy1 - 120
    d.polygon([(gx0, fl), (gx1, fl), (cx + iw / 2, cy + ih / 2), (cx - iw / 2, cy + ih / 2)], fill=(56, 52, 48))
    d.polygon([(gx0, gy0), (gx1, gy0), (cx + iw / 2, cy - ih / 2), (cx - iw / 2, cy - ih / 2)], fill=(70, 72, 78))
    d.polygon([(gx0, gy0), (cx - iw / 2, cy - ih / 2), (cx - iw / 2, cy + ih / 2), (gx0, fl)], fill=(58, 66, 74))
    d.polygon([(gx1, gy0), (cx + iw / 2, cy - ih / 2), (cx + iw / 2, cy + ih / 2), (gx1, fl)], fill=(48, 56, 64))
    d.rectangle([cx - iw / 2, cy - ih / 2, cx + iw / 2, cy + ih / 2], fill=(28, 32, 38))
    # the game's own bottom bar with its two words
    d.rectangle([gx0, fl, gx1, gy1], fill=(44, 42, 44))
    f = ImageFont.truetype(FB, 34); fs = ImageFont.truetype(FB, 30)
    for (x, txt) in ((gx0 + 110, "Menu"), (gx1 - 110, "Map")):
        d.rounded_rectangle([x - 95, fl + 18, x + 95, gy1 - 18], radius=14, outline=ORANGE, width=6)
        d.text((x, (fl + gy1) / 2), txt, fill=BONE, font=f, anchor="mm")
        d.text((x, fl - 30), "tap", fill=ORANGE, font=fs, anchor="mm")
    # swipe arrows
    arrow(d, (cx, cy + 170), (cx, cy - 220)); d.text((cx + 26, cy - 200), "swipe up: forward", fill=ORANGE, font=fs, anchor="lm")
    arrow(d, (cx, cy + 230), (cx, cy + 440)); d.text((cx + 26, cy + 420), "swipe down: back", fill=ORANGE, font=fs, anchor="lm")
    arrow(d, (cx - 60, cy + 40), (gx0 + 60, cy + 40)); d.text((gx0 + 50, cy - 10), "turn left", fill=ORANGE, font=fs, anchor="lm")
    arrow(d, (cx + 60, cy + 40), (gx1 - 60, cy + 40)); d.text((gx1 - 50, cy - 10), "turn right", fill=ORANGE, font=fs, anchor="rm")
    # tap ring
    tx, ty = cx, cy + 40
    for r, w in ((54, 8), (30, 6)):
        d.ellipse([tx - r, ty - r, tx + r, ty + r], outline=ORANGE, width=w)
    d.text((tx - 70, ty + 90), "tap: fire / use", fill=ORANGE, font=fs, anchor="rm")
    return im

# ------------------------------------------------------------ 4. keypad open: how to close it
def help_keypad():
    pim, pj = deck_full("pk")
    top = int(pj["game"][3])
    p = pim.crop((0, top - 90, pim.width, pim.height)).convert("RGB")
    lim, lj = deck_full("lk")
    l = lim.convert("RGB")
    # scale landscape to the portrait crop's width
    sc = p.width / l.width
    l = l.resize((p.width, round(l.height * sc)), Image.LANCZOS)
    f = ImageFont.truetype(FB, 40)
    W = p.width; H = 70 + p.height + 90 + l.height + 30
    im = Image.new("RGB", (W, H), (12, 10, 10))
    d = ImageDraw.Draw(im)
    d.text((W / 2, 38), "PORTRAIT", fill=BONE, font=f, anchor="mm")
    im.paste(p, (0, 70))
    d.text((W / 2, 70 + p.height + 48), "LANDSCAPE", fill=BONE, font=f, anchor="mm")
    ly = 70 + p.height + 90
    im.paste(l, (0, ly))
    d = ImageDraw.Draw(im)
    # portrait marks
    k = by_key(pj, 0)
    kx, ky = k["cx"], k["cy"] - (top - 90) + 70
    arrow(d, (kx - 120, ky - 150), (kx - 40, ky - 62))
    d.text((kx - 130, ky - 160), "glows while open: tap to close", fill=ORANGE, font=ImageFont.truetype(FB, 30), anchor="rb")
    close = [b for b in pj["keypad"] if b["key"] == -100][0]
    cxm, cym = (close["x0"] + close["x1"]) / 2, (close["y0"] + close["y1"]) / 2 - (top - 90) + 70
    badge(d, close["x1"] - 40, cym, "X", r=26)
    # landscape marks
    k = by_key(lj, 0)
    kx, ky = k["cx"] * sc, k["cy"] * sc + ly
    arrow(d, (kx + 170, ky + 70), (kx + 40, ky + 18))
    close = [b for b in lj["keypad"] if b["key"] == -100][0]
    badge(d, close["x1"] * sc - 34, (close["y0"] + close["y1"]) / 2 * sc + ly, "X", r=22)
    return im

# ------------------------------------------------------------ 5. handheld mode (square screen)
def help_handheld(name="hh"):
    im, j = deck_full(name)
    im = im.convert("RGB")
    d = ImageDraw.Draw(im)
    g = [round(v) for v in j["game"]]
    d.rectangle(g, fill=(36, 40, 46))
    d.text(((g[0] + g[2]) / 2, (g[1] + g[3]) / 2), "GAME", fill=BONE, font=ImageFont.truetype(FB, 40), anchor="mm")
    W = im.width * 2 // 2
    out = Image.new("RGB", (im.width, im.height), BG)
    out.paste(im, (0, 0))
    return out.resize((im.width * 5 // 4, im.height * 5 // 4), Image.NEAREST)

def save(im, name, maxw=900):
    if im.width > maxw:
        im = im.resize((maxw, round(im.height * maxw / im.width)), Image.LANCZOS)
    im = im.convert("P", palette=Image.ADAPTIVE, colors=128)
    im.save(os.path.join(OUT, name), optimize=True)
    print(name, im.size, os.path.getsize(os.path.join(OUT, name)))

save(help_controls(), "help_controls.png")
save(help_moves(), "help_moves.png")
save(help_gestures(), "help_gestures.png")
save(help_keypad(), "help_keypad.png")
save(help_handheld(), "help_handheld.png")
save(help_handheld("hhk"), "help_bigkeypad.png")
