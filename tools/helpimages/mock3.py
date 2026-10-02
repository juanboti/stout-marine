"""Pixel-art control deck: drawn at low resolution with a small palette, scaled up with hard pixel edges."""
from PIL import Image, ImageDraw, ImageFont
import random

P = 4  # one art pixel = 4 mockup pixels
random.seed(3)

# palette (original, limited)
K = (8, 7, 7)          # outline
D1 = (26, 23, 22)      # deep shadow
D2 = (40, 35, 33)      # panel dark
D3 = (55, 49, 45)      # panel
D4 = (78, 70, 63)      # panel light
D5 = (112, 101, 90)    # bevel highlight
BONE = (226, 214, 190)  # icon
BONE2 = (160, 148, 128)
R1 = (92, 18, 12)      # red dark
R2 = (150, 30, 18)     # red
R3 = (206, 58, 30)     # red light
OR = (244, 140, 40)    # orange highlight
OR2 = (255, 200, 90)

def bm(rows):
    return [r for r in rows]

ICONS = {
    "up": bm([
        "....#....",
        "...###...",
        "..#####..",
        ".#######.",
        "...###...",
        "...###...",
        "...###...",
        "...###...",
    ]),
    "down": None,  # filled below (flip)
    "turnL": bm([
        "...#####...",
        "..#######..",
        ".##.....##.",
        "##.......##",
        "##.......##",
        "######.....",
        ".####......",
        "..##.......",
    ]),
    "turnR": None,
    "stepL": bm([
        "...#.....#",
        "..##.....#",
        ".#######.#",
        "########.#",
        ".#######.#",
        "..##.....#",
        "...#.....#",
    ]),
    "stepR": None,
    "cross": bm([
        "......#......",
        "......#......",
        "...#######...",
        "..##..#..##..",
        ".##.......##.",
        ".#.........#.",
        "####..#..####",
        ".#.........#.",
        ".##.......##.",
        "..##..#..##..",
        "...#######...",
        "......#......",
        "......#......",
    ]),
    "hour": bm([
        "#######",
        ".#...#.",
        "..#.#..",
        "...#...",
        "..#.#..",
        ".#.#.#.",
        "#######",
    ]),
    "gear": bm([
        "...#.#...",
        ".#######.",
        ".##...##.",
        "###...###",
        ".##...##.",
        ".#######.",
        "...#.#...",
    ]),
    "map": bm([
        "###......",
        "#.####...",
        "#.#..####",
        "#.#..#..#",
        "#.#..#..#",
        "####.#..#",
        "...####.#",
        "......###",
    ]),
    "keys": bm([
        "#.#.#",
        ".....",
        "#.#.#",
        ".....",
        "#.#.#",
    ]),
    "gun": bm([
        "...########..",
        ".############",
        ".##########..",
        ".####.#......",
        "####.........",
        "###..........",
    ]),
    "chevL": bm(["..#", ".#.", "#..", ".#.", "..#"]),
    "chevR": bm(["#..", ".#.", "..#", ".#.", "#.."]),
}
# turnL drawn with arrowhead on the left end; build the others by mirroring / flipping
def hflip(b): return [r[::-1] for r in b]
def vflip(b): return b[::-1]
ICONS["down"] = vflip(ICONS["up"])
ICONS["turnR"] = hflip(ICONS["turnL"])
ICONS["stepR"] = hflip(ICONS["stepL"])

def blit(img, name, x, y, col, shadow=True):
    b = ICONS[name]
    px = img.load()
    for j, row in enumerate(b):
        for i, ch in enumerate(row):
            if ch == "#":
                if shadow and 0 <= x + i + 1 < img.width and 0 <= y + j + 1 < img.height:
                    if px[x + i + 1, y + j + 1] != col:
                        px[x + i + 1, y + j + 1] = K
    for j, row in enumerate(b):
        for i, ch in enumerate(row):
            if ch == "#":
                px[x + i, y + j] = col

def blit_center(img, name, cx, cy, col, shadow=True):
    b = ICONS[name]
    blit(img, name, cx - len(b[0]) // 2, cy - len(b) // 2, col, shadow)

# 3x5 pixel font for the few labels
F35 = {
    "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#"], "I": ["###", ".#.", ".#.", ".#.", "###"],
    "D": ["##.", "#.#", "#.#", "#.#", "##."], "L": ["#..", "#..", "#..", "#..", "###"],
    "E": ["###", "#..", "##.", "#..", "###"], "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "N": ["#..#", "##.#", "#.##", "#..#", "#..#"], " ": ["...", "...", "...", "...", "..."],
}
def text35(img, s, x, y, col):
    px = img.load()
    cx = x
    for ch in s:
        g = F35[ch]
        for j, row in enumerate(g):
            for i, c in enumerate(row):
                if c == "#":
                    px[cx + i, y + j] = col
        cx += len(g[0]) + 1

# ---------------------------------------------------------------- building blocks
def panel(img, box):
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    d.rectangle(box, fill=D3)
    px = img.load()
    for y in range(y0, y1 + 1):          # ordered-dither grime
        for x in range(x0, x1 + 1):
            n = random.random()
            if n < 0.10: px[x, y] = D2
            elif n < 0.14: px[x, y] = D4
    # plate seams every 24 px
    for y in range(y0 + 30, y1, 40):
        d.line([x0, y, x1, y], fill=D1); d.line([x0, y + 1, x1, y + 1], fill=D4)
    # top bevel + red trim
    d.line([x0, y0, x1, y0], fill=D5); d.line([x0, y0 + 1, x1, y0 + 1], fill=D4)
    d.line([x0, y0 + 2, x1, y0 + 2], fill=R2); d.line([x0, y0 + 3, x1, y0 + 3], fill=R1)
    d.line([x0, y0 + 4, x1, y0 + 4], fill=D1)
    # rivets
    for rx, ry in ((x0 + 3, y0 + 8), (x1 - 4, y0 + 8), (x0 + 3, y1 - 4), (x1 - 4, y1 - 4)):
        rivet(img, rx, ry)

def rivet(img, x, y):
    px = img.load()
    px[x, y] = D5; px[x + 1, y] = D4; px[x, y + 1] = D4; px[x + 1, y + 1] = D1

def bevel_box(img, box, face, hi, lo, inset=False):
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    d.rectangle([x0 - 1, y0 - 1, x1 + 1, y1 + 1], outline=K)
    d.rectangle(box, fill=face)
    a, b = (lo, hi) if inset else (hi, lo)
    d.line([x0, y0, x1, y0], fill=a); d.line([x0, y0, x0, y1], fill=a)
    d.line([x0, y1, x1, y1], fill=b); d.line([x1, y0, x1, y1], fill=b)
    # corner pixels knocked off for a chunky rounded look
    px = img.load()
    for cx, cy in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        px[cx, cy] = K

def well(img, box):
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    d.rectangle(box, fill=D1)
    d.line([x0, y0, x1, y0], fill=K); d.line([x0, y0, x0, y1], fill=K)
    d.line([x0, y1, x1, y1], fill=D4); d.line([x1, y0, x1, y1], fill=D4)

def pix_disc(img, cx, cy, r, face, hi, lo, outline=K):
    d = ImageDraw.Draw(img)
    d.ellipse([cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1], fill=outline)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=lo)
    d.ellipse([cx - r, cy - r, cx + r - 1, cy + r - 1], fill=face)
    d.ellipse([cx - r + 2, cy - r + 1, cx + r - 3, cy - 1], fill=hi)
    d.ellipse([cx - r + 2, cy - r + 3, cx + r - 3, cy + r - 3], fill=face)

def move_block(img, cx, cy, cell, gap, pressed=None):
    names = [["turnL", "up", "turnR"], ["stepL", "down", "stepR"]]
    total_w = cell * 3 + gap * 2
    total_h = cell * 2 + gap
    x0, y0 = cx - total_w // 2, cy - total_h // 2
    well(img, (x0 - 3, y0 - 3, x0 + total_w + 2, y0 + total_h + 2))
    for r in range(2):
        for c in range(3):
            bx, by = x0 + c * (cell + gap), y0 + r * (cell + gap)
            on = names[r][c] == pressed
            if on:
                bevel_box(img, (bx, by + 1, bx + cell - 1, by + cell), R2, R3, R1, inset=True)
                col = OR2
            else:
                bevel_box(img, (bx, by, bx + cell - 1, by + cell - 1), D4, D5, D2)
                col = BONE
            blit_center(img, names[r][c], bx + cell // 2, by + cell // 2 + (1 if on else 0), col)

def fire_button(img, cx, cy, r, pressed=False):
    if pressed:
        pix_disc(img, cx, cy + 1, r, R3, OR, R2)
        blit_center(img, "cross", cx, cy + 1, OR2, shadow=False)
    else:
        pix_disc(img, cx, cy, r, R2, R3, R1)
        blit_center(img, "cross", cx, cy, BONE, shadow=False)

def small_disc(img, cx, cy, r, icon):
    pix_disc(img, cx, cy, r, D4, D5, D2)
    blit_center(img, icon, cx, cy, BONE)

def weapon_slot(img, cx, cy, w):
    h = 13
    well(img, (cx - w // 2, cy - h // 2, cx + w // 2, cy + h // 2))
    blit_center(img, "gun", cx, cy, BONE)
    blit_center(img, "chevL", cx - w // 2 + 5, cy, R3)
    blit_center(img, "chevR", cx + w // 2 - 5, cy, R3)

def small_key(img, cx, cy, icon):
    bevel_box(img, (cx - 9, cy - 6, cx + 9, cy + 6), D4, D5, D2)
    blit_center(img, icon, cx, cy, BONE)

def game_px(img, box):
    """Low-res corridor stand-in (not game art)."""
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    d = ImageDraw.Draw(img)
    d.rectangle(box, fill=(20, 22, 26))
    cx, cy = x0 + w // 2, y0 + int(h * 0.44)
    iw, ih = int(w * 0.28), int(h * 0.22)
    fl = y0 + int(h * 0.84)
    d.polygon([(x0, fl), (x1, fl), (cx + iw // 2, cy + ih // 2), (cx - iw // 2, cy + ih // 2)], fill=(56, 52, 48))
    d.polygon([(x0, y0), (x1, y0), (cx + iw // 2, cy - ih // 2), (cx - iw // 2, cy - ih // 2)], fill=(74, 76, 82))
    d.polygon([(x0, y0), (cx - iw // 2, cy - ih // 2), (cx - iw // 2, cy + ih // 2), (x0, fl)], fill=(60, 68, 76))
    d.polygon([(x1, y0), (cx + iw // 2, cy - ih // 2), (cx + iw // 2, cy + ih // 2), (x1, fl)], fill=(50, 58, 66))
    d.rectangle([cx - iw // 2, cy - ih // 2, cx + iw // 2, cy + ih // 2], fill=(30, 34, 40))
    d.rectangle([x0, fl, x1, y1], fill=(44, 42, 44))

# ---------------------------------------------------------------- screens
def portrait():
    W, H = 135, 300
    img = Image.new("RGB", (W, H), K)
    gh = 180
    game_px(img, (0, 0, W - 1, gh - 1))
    panel(img, (0, gh, W - 1, H - 1))
    top = gh + 13
    small_disc(img, 13, top, 7, "gear")
    weapon_slot(img, W // 2, top, 44)
    small_disc(img, W - 31, top, 7, "keys")
    small_disc(img, W - 13, top, 7, "map")
    cy = gh + 62
    move_block(img, 38, cy, 20, 2, pressed="up")
    fire_button(img, W - 27, cy + 8, 17)
    small_disc(img, W - 48, cy - 23, 8, "hour")
    text35(img, "MIDLET MARINE", 6, H - 9, BONE2)
    return img.resize((W * P, H * P), Image.NEAREST)

def landscape():
    W, H = 300, 135
    img = Image.new("RGB", (W, H), K)
    gw = 101; gx = (W - gw) // 2
    panel(img, (0, 0, gx - 1, H - 1))
    panel(img, (gx + gw, 0, W - 1, H - 1))
    game_px(img, (gx, 0, gx + gw - 1, H - 1))
    lx, rx = gx // 2, gx + gw + (W - gx - gw) // 2
    small_disc(img, 14, 15, 7, "gear")
    small_disc(img, 32, 15, 7, "keys")
    small_disc(img, W - 14, 15, 7, "map")
    weapon_slot(img, rx - 6, 32, 44)
    move_block(img, lx, H - 34, 20, 2)
    fire_button(img, rx + 14, H - 32, 17, pressed=True)
    small_disc(img, rx - 14, H - 56, 8, "hour")
    return img.resize((W * P, H * P), Image.NEAREST)

def caption(img, text):
    w, h = img.size
    out = Image.new("RGB", (w, h + 56), (245, 245, 247))
    out.paste(img, (0, 0))
    d = ImageDraw.Draw(out)
    d.text((w / 2, h + 28), text, fill=(30, 30, 36),
           font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22), anchor="mm")
    return out

if __name__ == "__main__":
    a = caption(portrait(), "F  Pixel deck, portrait (forward pressed)")
    b = caption(landscape(), "G  Pixel deck, landscape (fire pressed)")
    sheet = Image.new("RGB", (a.width + b.width + 60, max(a.height, b.height) + 40), (245, 245, 247))
    sheet.paste(a, (20, 20))
    sheet.paste(b, (a.width + 40, 20 + (a.height - b.height) // 2))
    sheet.save("/mnt/user-data/outputs/controls-mockup-pixel.png")
    # close-up of the controls at 2x for detail
    p = portrait()
    crop = p.crop((0, 180 * P, 135 * P, 300 * P))
    crop.resize((crop.width * 2, crop.height * 2), Image.NEAREST).save("/tmp/pixel-closeup.png")
    print("ok")
