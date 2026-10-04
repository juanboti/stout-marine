package brewemu.doomrpg;

import brewemu.brew.Bar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

/**
 * Pictures from the player's own Doom RPG (BREW) resource file, for Stout Marine's weapon picker: the weapon
 * pickups as they lie on the floor, the dogs, and the status bar's ammo icons. Nothing is stored or shipped;
 * the pictures are decoded from the game file at run time. The formats are the ones documented by the
 * DoomRPG-RE project (mappings.bin, bitshapes.bin, stexels.bin, palettes.bin, the .bmp sheets).
 */
public final class DoomArt {
    /** An ARGB picture (0 = see-through). */
    public static final class Pic {
        public final int w, h; public final int[] px;
        Pic(int w, int h) { this.w = w; this.h = h; px = new int[w * h]; }
    }

    /** Pictures by weapon number (0 axe ... 8 BFG, 9-11 dogs); null where the game has none (the pistol). */
    public final Pic[] weapons = new Pic[12];
    /** Status bar icons by ammo type (0 halon, 1 bullets, 2 shells, 3 rockets, 4 cells); null if missing. */
    public final Pic[] ammo = new Pic[5];

    // sprite tiles of the pickups (entities.db: Axe 1, Fire Ext 2, Shotgun 3, Super Shotgn 4, Chaingun 5,
    // Rocket Lnchr 6, Plasma Gun 7, BFG 8; the pistol is never dropped) and of the three dogs
    private static final int[] WEAPON_TILE = {1, 2, -1, 3, 5, 4, 7, 6, 8, 20, 21, 22};
    // larger_HUD_icon_sheet.bmp, 18 x 18 each, top to bottom: health, armor, axe, halon, bullets, shells, rockets, cells, bone
    private static final int[] AMMO_ICON = {3, 4, 5, 6, 7};

    /** Decodes what it can; a picture that can't be read stays null. Never throws. */
    public static DoomArt load(byte[] barFile) {
        DoomArt a = new DoomArt();
        try {
            HashMap<String, byte[]> files = files(new Bar(barFile));
            try { a.sprites(files); } catch (RuntimeException e) { /* keep what was decoded */ }
            try { a.ammoIcons(files.get("larger_HUD_icon_sheet.bmp")); } catch (RuntimeException e) { /* none */ }
        } catch (RuntimeException e) { /* not a Doom RPG resource file */ }
        return a;
    }

    /** The game's packed files (gzip resources, by their stored file names). */
    static HashMap<String, byte[]> files(Bar bar) {
        HashMap<String, byte[]> out = new HashMap<String, byte[]>();
        for (int id = 5000; id < 5300; id++) {
            byte[] r = bar.get(Bar.TYPE_DATA, id);
            if (r == null || r.length < 2) continue;
            int hl = r[0] & 0xff;
            if (hl + 10 > r.length || (r[hl] & 0xff) != 0x1f || (r[hl + 1] & 0xff) != 0x8b) continue;
            int flg = r[hl + 3] & 0xff, p = hl + 10;
            if ((flg & 4) != 0) p += 2 + u16(r, p);
            if ((flg & 8) == 0) continue;
            int e = p; while (e < r.length && r[e] != 0) e++;
            String name = new String(r, p, e - p, java.nio.charset.StandardCharsets.ISO_8859_1);
            try { out.put(name, gunzip(r, hl)); } catch (IOException ex) { /* skip */ }
        }
        return out;
    }

    private static byte[] gunzip(byte[] d, int off) throws IOException {
        GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(d, off, d.length - off));
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
        return o.toByteArray();
    }

    private void sprites(HashMap<String, byte[]> f) {
        byte[] map = f.get("mappings.bin"), bs = f.get("bitshapes.bin"), wt = f.get("wtexels.bin"),
               st = f.get("stexels.bin"), pal = f.get("palettes.bin");
        if (map == null || bs == null || wt == null || st == null || pal == null) return;
        int texCnt = s32(map, 0), bsCnt = s32(map, 4), texIds = s32(map, 8), sprCnt = s32(map, 12);
        int bsOffs = 16 + texCnt * 2 * 4, texIdOff = bsOffs + bsCnt * 2 * 4, sprIdOff = texIdOff + texIds * 2;
        int wtNibbles = s32(wt, 0) * 2;
        int palCount = s32(pal, 0) / 2;
        for (int w = 0; w < WEAPON_TILE.length; w++) {
            int tile = WEAPON_TILE[w];
            if (tile < 0 || tile >= sprCnt) continue;
            try {
                int idx = (short) u16(map, sprIdOff + tile * 2);
                if (idx < 0 || idx >= bsCnt) continue;
                int off = s32(map, bsOffs + idx * 8) + 4, palOff = s32(map, bsOffs + idx * 8 + 4);
                weapons[w] = shape(bs, off, st, s32(bs, off) - wtNibbles, pal, palOff, palCount);
            } catch (RuntimeException e) { weapons[w] = null; }
        }
    }

    /** One sprite: columns x0..x1 of a 64 x 64 cell, a bit per pixel says "drawn"; colours are 4-bit palette entries. */
    private static Pic shape(byte[] bs, int off, byte[] st, int nib, byte[] pal, int palOff, int palCount) {
        int x0 = bs[off + 8] & 0xff, x1 = bs[off + 9] & 0xff, y0 = bs[off + 10] & 0xff, y1 = bs[off + 11] & 0xff;
        int w = x1 - x0 + 1, h = y1 - y0 + 1, pitch = (h + 7) / 8;
        if (w <= 0 || h <= 0 || w > 64 || h > 64) throw new IllegalArgumentException("bad shape");
        Pic p = new Pic(w, h);
        int k = 0;
        for (int c = 0; c < w; c++) {
            int o = off + 12 + c * pitch;
            for (int r = 0; r < h; r++) {
                if ((bs[o + r / 8] & (1 << (r & 7))) == 0) continue;
                int q = nib + k++, b = st[4 + (q >> 1)] & 0xff, v = (q & 1) != 0 ? b >> 4 : b & 15;
                int pi = palOff + v;
                if (pi < 0 || pi >= palCount) throw new IllegalArgumentException("bad palette");
                p.px[r * w + c] = rgb(u16(pal, 4 + pi * 2));
            }
        }
        return p;
    }

    /** The game's palettes keep blue in the low bits (5-6-5). */
    private static int rgb(int c) {
        int b = (c >> 11) & 31, g = (c >> 5) & 63, r = c & 31;
        return 0xff000000 | ((r << 3) | (r >> 2)) << 16 | ((g << 2) | (g >> 4)) << 8 | ((b << 3) | (b >> 2));
    }

    private void ammoIcons(byte[] bmp) {
        Pic sheet = bmp(bmp);
        if (sheet == null || sheet.w < 18) return;
        for (int t = 0; t < 5; t++) {
            int y0 = AMMO_ICON[t] * 18;
            if (y0 + 18 > sheet.h) continue;
            Pic p = new Pic(18, 18);
            for (int y = 0; y < 18; y++) System.arraycopy(sheet.px, (y0 + y) * sheet.w, p.px, y * 18, 18);
            ammo[t] = p;
        }
    }

    /** Windows .bmp (8-bit palette or 24-bit); magenta is see-through, as in the game. */
    static Pic bmp(byte[] d) {
        if (d == null || d.length < 54 || d[0] != 'B' || d[1] != 'M') return null;
        int dataOff = s32(d, 10), hs = s32(d, 14), w = s32(d, 18), hRaw = s32(d, 22), bpp = u16(d, 28);
        boolean up = hRaw < 0; int h = Math.abs(hRaw);
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return null;
        int[] pal = null;
        if (bpp <= 8) {
            int n = s32(d, 46); if (n == 0) n = 1 << bpp;
            pal = new int[n];
            for (int i = 0; i < n; i++) { int o = 14 + hs + i * 4; pal[i] = 0xff000000 | (d[o + 2] & 0xff) << 16 | (d[o + 1] & 0xff) << 8 | (d[o] & 0xff); }
        } else if (bpp != 24) return null;
        int stride = ((w * bpp + 31) / 32) * 4;
        Pic p = new Pic(w, h);
        for (int y = 0; y < h; y++) {
            int row = dataOff + (up ? y : h - 1 - y) * stride;
            for (int x = 0; x < w; x++) {
                int c;
                if (bpp == 24) { int o = row + x * 3; c = 0xff000000 | (d[o + 2] & 0xff) << 16 | (d[o + 1] & 0xff) << 8 | (d[o] & 0xff); }
                else {
                    int bit = x * bpp, v = (d[row + (bit >> 3)] & 0xff) >> (8 - bpp - (bit & 7)) & ((1 << bpp) - 1);
                    c = v < pal.length ? pal[v] : 0;
                }
                int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
                p.px[y * w + x] = (r > 200 && b > 200 && g < 80) ? 0 : c;
            }
        }
        return p;
    }

    static int u16(byte[] d, int o) { return (d[o] & 0xff) | (d[o + 1] & 0xff) << 8; }
    static int s32(byte[] d, int o) { return (d[o] & 0xff) | (d[o + 1] & 0xff) << 8 | (d[o + 2] & 0xff) << 16 | (d[o + 3] & 0xff) << 24; }
}
