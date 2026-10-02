"""Explainer: turn vs side-step, seen from above."""
from PIL import Image, ImageDraw, ImageFont
import math
import mock3 as px3

F = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BG = (24, 21, 20); TILE = (55, 49, 45); TILE2 = (48, 43, 40); LINE = (34, 30, 28)
BONE = (226, 214, 190); RED = (206, 58, 30); GHOST = (120, 108, 96); VIEW = (244, 140, 40)

def button_icon(name, pressed=True, scale=6):
    """The real deck key, rendered with the same pixel art as the app."""
    img = Image.new("RGB", (24, 24), px3.D1)
    if pressed:
        px3.bevel_box(img, (2, 3, 21, 22), px3.R2, px3.R3, px3.R1, inset=True)
        px3.blit_center(img, name, 12, 13, px3.OR2)
    else:
        px3.bevel_box(img, (2, 2, 21, 21), px3.D4, px3.D5, px3.D2)
        px3.blit_center(img, name, 12, 12, px3.BONE)
    return img.resize((24 * scale, 24 * scale), Image.NEAREST)

def marker(d, cx, cy, size, ang, fill, outline=None, cone=False):
    """Player seen from above: an arrowhead pointing where they face (ang in radians, 0 = up)."""
    def rot(x, y):
        return (cx + x * math.cos(ang) - y * math.sin(ang), cy + x * math.sin(ang) + y * math.cos(ang))
    if cone:
        pts = [rot(-size * 0.45, -size * 1.15), rot(-size * 1.25, -size * 2.6), rot(size * 1.25, -size * 2.6), rot(size * 0.45, -size * 1.15)]
        d.polygon(pts, fill=(118, 72, 34, 255))
    pts = [rot(0, -size), rot(size * 0.75, size * 0.75), rot(0, size * 0.35), rot(-size * 0.75, size * 0.75)]
    d.polygon(pts, fill=fill, outline=outline)

def grid(d, x0, y0, n, cell):
    for r in range(n):
        for c in range(n):
            col = TILE if (r + c) % 2 == 0 else TILE2
            d.rectangle([x0 + c * cell, y0 + r * cell, x0 + (c + 1) * cell - 2, y0 + (r + 1) * cell - 2], fill=col)

def curved_arrow(d, cx, cy, r, left):
    start, end = (200, 330) if not left else (210, 340)
    box = [cx - r, cy - r, cx + r, cy + r]
    if left:
        d.arc(box, 220, 330, fill=BONE, width=5)
        a = math.radians(220)
        tip = (cx + r * math.cos(a), cy + r * math.sin(a))
        d.polygon([(tip[0] - 4, tip[1] + 14), (tip[0] - 12, tip[1] - 6), (tip[0] + 10, tip[1] - 2)], fill=BONE)
    else:
        d.arc(box, 210, 320, fill=BONE, width=5)
        a = math.radians(320)
        tip = (cx + r * math.cos(a), cy + r * math.sin(a))
        d.polygon([(tip[0] + 4, tip[1] + 14), (tip[0] + 12, tip[1] - 6), (tip[0] - 10, tip[1] - 2)], fill=BONE)

def dashed_line(d, a, b, dash=10, gap=7, width=4, col=BONE):
    (x0, y0), (x1, y1) = a, b
    L = math.hypot(x1 - x0, y1 - y0); ux, uy = (x1 - x0) / L, (y1 - y0) / L
    t = 0
    while t < L:
        e = min(L, t + dash)
        d.line([x0 + ux * t, y0 + uy * t, x0 + ux * e, y0 + uy * e], fill=col, width=width)
        t = e + gap
    # head
    hx, hy = x1, y1
    d.polygon([(hx, hy), (hx - ux * 16 - uy * 9, hy - uy * 16 + ux * 9), (hx - ux * 16 + uy * 9, hy - uy * 16 - ux * 9)], fill=col)

def panel(kind):
    W, H = 420, 640
    img = Image.new("RGBA", (W, H), BG + (255,))
    d = ImageDraw.Draw(img, "RGBA")
    titles = {"turnL": ("TURN LEFT", "Same square. You now face left."),
              "turnR": ("TURN RIGHT", "Same square. You now face right."),
              "stepL": ("L: SIDE-STEP LEFT", "Move one square left. Still facing forward."),
              "stepR": ("R: SIDE-STEP RIGHT", "Move one square right. Still facing forward.")}
    t, sub = titles[kind]
    icon = {"turnL": "turnL", "turnR": "turnR", "stepL": "stepL", "stepR": "stepR"}[kind]
    img.paste(button_icon(icon), (W // 2 - 72, 24))
    d.text((W / 2, 196), t, fill=BONE, font=ImageFont.truetype(F, 26), anchor="mm")
    cell, n = 110, 3
    gx, gy = (W - cell * n) // 2, 236
    grid(d, gx, gy, n, cell)
    ccx, ccy = gx + cell * 1.5 - 1, gy + cell * 1.5 - 1
    # "before": ghost marker in the centre facing up
    if kind in ("turnL", "turnR"):
        marker(d, ccx, ccy, 26, 0, GHOST + (255,))
        ang = -math.pi / 2 if kind == "turnL" else math.pi / 2
        marker(d, ccx, ccy, 30, ang, RED + (255,), outline=BONE, cone=True)
        curved_arrow(d, ccx, ccy - 6, 70, kind == "turnL")
    else:
        marker(d, ccx, ccy, 26, 0, GHOST + (255,))
        tx = ccx - cell if kind == "stepL" else ccx + cell
        dashed_line(d, (ccx + (-34 if kind == "stepL" else 34), ccy + 40), (tx + (30 if kind == "stepL" else -30), ccy + 40))
        marker(d, tx, ccy, 30, 0, RED + (255,), outline=BONE, cone=True)
    # legend line
    f = ImageFont.truetype(FR, 18)
    words, line, lines = sub.split(), "", []
    for w_ in words:
        test = (line + " " + w_).strip()
        if d.textlength(test, font=f) > W - 40: lines.append(line); line = w_
        else: line = test
    lines.append(line)
    for i, l in enumerate(lines):
        d.text((W / 2, 596 + i * 24), l, fill=(190, 180, 160), font=f, anchor="mm")
    return img

def legend():
    W, H = 1760, 70
    img = Image.new("RGBA", (W, H), (245, 245, 247, 255))
    d = ImageDraw.Draw(img, "RGBA")
    f = ImageFont.truetype(FR, 20)
    x = 40
    marker(d, x + 14, 36, 14, 0, GHOST + (255,)); d.text((x + 40, 36), "where you were (facing up = forward)", fill=(40, 40, 46), font=f, anchor="lm")
    x = 520
    marker(d, x + 14, 36, 14, 0, RED + (255,), outline=(60, 50, 40)); d.text((x + 40, 36), "where you end up", fill=(40, 40, 46), font=f, anchor="lm")
    x = 820
    d.polygon([(x + 4, 50), (x + 10, 30), (x + 18, 30), (x + 24, 50)], fill=(118, 72, 34, 255)); d.text((x + 44, 36), "what you see after pressing", fill=(40, 40, 46), font=f, anchor="lm")
    x = 1220
    d.text((x, 36), "Seen from above. One tile = one square of the map.", fill=(90, 90, 100), font=f, anchor="lm")
    return img

if __name__ == "__main__":
    ps = [panel(k) for k in ("turnL", "turnR", "stepL", "stepR")]
    W = 4 * 420 + 5 * 20
    head = Image.new("RGBA", (W, 70), (245, 245, 247, 255))
    hd = ImageDraw.Draw(head)
    hd.text((W / 2, 40), "Turn vs. side-step", fill=(30, 30, 36), font=ImageFont.truetype(F, 32), anchor="mm")
    sheet = Image.new("RGBA", (W, 70 + 640 + 20 + 70 + 20), (245, 245, 247, 255))
    sheet.alpha_composite(head, (0, 0))
    for i, p in enumerate(ps):
        sheet.alpha_composite(p, (20 + i * 440, 70))
    sheet.alpha_composite(legend(), (0, 70 + 640 + 20))
    sheet.convert("RGB").save("/mnt/user-data/outputs/turn-vs-sidestep.png")
    sheet.convert("RGB").resize((W // 2, sheet.height // 2), Image.LANCZOS).save("/tmp/turn-prev.png")
    print("ok")
