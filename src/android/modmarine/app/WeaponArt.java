package modmarine.app;

/** Stout Marine's own weapon drawings for the picker: the side-view pistol (the game has no pistol pickup) and plain icons. */
final class WeaponArt {
    private WeaponArt() {}

    private static final String[] PISTOL = {
        "......KKKKKKKKKKKKKKKKKKK.",
        ".....KHHHHHHHHHHHHHHHHHHLK",
        ".....KLLLLLLLLLLLLLLLLLLMK",
        ".....KMMLMMLMMLMMMMMMMMMDK",
        ".....KDDDDDDDDDDDDDDDDDDDK",
        "....KKKKKKKKKKKKKKKKKKKKK.",
        "...KBBBBBKDDDDK...........",
        "...KBbBBBKK..DK...........",
        "..KBbBBBBK.KKK............",
        "..KBbBBBK.................",
        ".KBbBBBBK.................",
        ".KBbBBBK..................",
        "KBbBBBBK..................",
        "KbbbbbbK..................",
        "KKKKKKKK..................",
        "..........................",
        ".SSSSSSSSSSSSSSSSSSSSSSSS.",
    };
    static final int PISTOL_W = 26, PISTOL_H = PISTOL.length;

    static int[] pistol() {
        int[] px = new int[PISTOL_W * PISTOL_H];
        for (int y = 0; y < PISTOL_H; y++)
            for (int x = 0; x < PISTOL[y].length(); x++) {
                int c;
                switch (PISTOL[y].charAt(x)) {
                    case 'K': c = 0xFF121010; break;
                    case 'H': c = 0xFFC4C4C8; break;
                    case 'L': c = 0xFF96969C; break;
                    case 'M': c = 0xFF68686E; break;
                    case 'D': c = 0xFF3E3C40; break;
                    case 'B': c = 0xFF764C2A; break;
                    case 'b': c = 0xFF4E301A; break;
                    case 'S': c = 0x96000000; break;   // the floor shadow, like the game's pickups
                    default: c = 0;
                }
                px[y * PISTOL_W + x] = c;
            }
        return px;
    }

    /** A plain one-colour icon as pixels. */
    static int[] icon(String[] ic, int color) {
        int w = ic[0].length(), h = ic.length;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < ic[y].length() && x < w; x++) if (ic[y].charAt(x) == '#') px[y * w + x] = color;
        return px;
    }

    /** Greyed and darker (a weapon with no ammo left). */
    static int[] grey(int[] px) {
        int[] o = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i], a = c >>> 24;
            int l = (((c >> 16) & 255) * 30 + ((c >> 8) & 255) * 59 + (c & 255) * 11) / 100 * 55 / 100;
            o[i] = a << 24 | l << 16 | l << 8 | l;
        }
        return o;
    }
}
