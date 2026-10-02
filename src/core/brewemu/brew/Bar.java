package brewemu.brew;

import java.util.HashMap;

/**
 * BREW resource file (.bar) reader.
 * Layout (little-endian): u16 magic, u16 ?, u16 ?, u16 typeCount, u32 typeTableOff, u32 typeTableSize,
 * u32 offsetTableOff, u32 offsetCount, u32 dataOff, u32 dataSize; each type entry is
 * {u16 type, u16 firstId, u16 ?, u16 firstIndex}; resource i spans offsets[i]..offsets[i+1].
 */
public final class Bar {
    public static final int TYPE_STRING = 1, TYPE_DATA = 6;
    private final HashMap<Long, byte[]> res = new HashMap<Long, byte[]>();

    public Bar(byte[] d) {
        if (d == null || d.length < 0x20) return;
        int types = u16(d, 6);
        int toff = s32(d, 8), ooff = s32(d, 16), ocount = s32(d, 20);
        if (toff < 0 || ooff < 0 || ocount < 1 || ooff + 4L * ocount > d.length || toff + 8L * types > d.length)
            throw new IllegalArgumentException("not a BREW resource file");
        int[] offs = new int[ocount];
        for (int i = 0; i < ocount; i++) offs[i] = s32(d, ooff + 4 * i);
        for (int k = 0; k < types; k++) {
            int e = toff + 8 * k;
            int type = u16(d, e), first = u16(d, e + 2), idx = u16(d, e + 6);
            int end = k + 1 < types ? u16(d, e + 8 + 6) : ocount - 1;
            for (int j = idx; j < end && j + 1 < ocount; j++) {
                int a = offs[j], z = offs[j + 1];
                if (a < 0 || z > d.length || z < a) continue;
                byte[] item = new byte[z - a];
                System.arraycopy(d, a, item, 0, item.length);
                res.put(key(type, first + j - idx), item);
            }
        }
    }

    private static long key(int type, int id) { return (long) type << 32 | (id & 0xffffffffL); }

    /** Raw resource bytes (data resources keep their MIME header; first byte = header length). */
    public byte[] get(int type, int id) { return res.get(key(type, id)); }

    public int count() { return res.size(); }

    /** String resource: first byte is the text encoding, the rest is the text. */
    public String string(int id) {
        byte[] b = get(TYPE_STRING, id);
        if (b == null || b.length == 0) return null;
        int enc = b[0] & 0xff;
        if (enc == 1 || enc == 2) {   // UCS-2 little-endian variants
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i + 1 < b.length; i += 2) { char c = (char) ((b[i] & 0xff) | (b[i + 1] & 0xff) << 8); if (c == 0) break; sb.append(c); }
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < b.length && b[i] != 0; i++) sb.append((char) (b[i] & 0xff));
        return sb.toString();
    }

    static int u16(byte[] d, int o) { return (d[o] & 0xff) | (d[o + 1] & 0xff) << 8; }
    static int s32(byte[] d, int o) { return (d[o] & 0xff) | (d[o + 1] & 0xff) << 8 | (d[o + 2] & 0xff) << 16 | (d[o + 3] & 0xff) << 24; }
}
