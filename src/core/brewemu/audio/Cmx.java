package brewemu.audio;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Converts CMX ("cmid") clips into something a phone can play today:
 * sampled sounds (QCELP-13K wave packets) become 16-bit PCM at 8 kHz, note-based songs become a
 * Standard MIDI File (General MIDI). Written from the published CMF/CMX format description.
 */
public final class Cmx {
    /** Decoded clip: exactly one of pcm / midi is set. */
    public static final class Clip {
        public short[] pcm;         // mono, 8000 Hz
        public byte[] midi;         // SMF format 0
        public int durationMs;
    }

    private static final int[] TIMEBASE = {6, 12, 24, 48, 96, 192, 384, 48, 15, 30, 60, 120, 240, 480, 960, 48};

    private static final class Ev {
        int tick, kind, a, b, c;   // kind: 0 note(a=chanIdx,b=key/oct,c=gate|vel<<8), 1 ext(a=code,b=data), 2 wave(payload index)
        int track;
        byte[] data;
    }

    private static int u16(byte[] d, int o) { return (d[o] & 0xff) << 8 | (d[o + 1] & 0xff); }
    private static int u32(byte[] d, int o) { return (d[o] & 0xff) << 24 | (d[o + 1] & 0xff) << 16 | (d[o + 2] & 0xff) << 8 | (d[o + 3] & 0xff); }

    /** Returns null if the data is not a CMX clip. */
    public static Clip decode(byte[] d, int off, int len) {
        if (len < 16 || d[off] != 'c' || d[off + 1] != 'm' || d[off + 2] != 'i' || d[off + 3] != 'd') return null;
        int end = Math.min(off + len, d.length);
        int hl = u16(d, off + 8);
        int p = off + 13, hEnd = off + 10 + hl;
        boolean note4 = false;
        while (p + 6 <= hEnd) {
            String name = new String(d, p, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            int ln = u16(d, p + 4);
            if (name.equals("note") && ln >= 2) note4 = (d[p + 7] & 1) != 0;
            p += 6 + ln;
        }
        p = hEnd;
        ArrayList<Ev> evs = new ArrayList<Ev>();
        int track = 0;
        while (p + 8 <= end) {
            int ln = u32(d, p + 4);
            boolean isTrac = d[p] == 't' && d[p + 1] == 'r' && d[p + 2] == 'a' && d[p + 3] == 'c';
            int q = p + 8, tEnd = Math.min(end, q + Math.max(0, ln));
            if (isTrac) { parseTrack(d, q, tEnd, note4, track, evs); track++; }
            if (ln < 0) break;
            p = q + ln;
        }
        boolean hasWave = false, hasNotes = false;
        for (Ev e : evs) { if (e.kind == 2) hasWave = true; if (e.kind == 0) hasNotes = true; }
        Clip clip = new Clip();
        if (hasWave && !hasNotes) clip.pcm = toPcm(evs);
        else clip.midi = toMidi(evs, clip);
        if (clip.pcm != null) clip.durationMs = clip.pcm.length / 8;
        return clip;
    }

    private static void parseTrack(byte[] d, int p, int end, boolean note4, int track, ArrayList<Ev> out) {
        int tick = 0;
        while (p + 2 <= end) {
            tick += d[p] & 0xff;
            int st = d[p + 1] & 0xff;
            Ev e = new Ev(); e.tick = tick; e.track = track;
            if (st == 0xff) {
                if (p + 3 >= end + 1) break;
                int code = d[p + 2] & 0xff;
                if (code >= 0xf0) {
                    if (p + 5 > end) break;
                    int ln = u16(d, p + 3);
                    int s = p + 5, z = Math.min(end, s + ln);
                    if (code == 0xf1) {
                        e.kind = 2; e.data = new byte[z - s];
                        System.arraycopy(d, s, e.data, 0, z - s);
                        out.add(e);
                    }
                    p = s + ln;
                } else {
                    if (p + 4 > end) break;
                    e.kind = 1; e.a = code; e.b = d[p + 3] & 0xff;
                    out.add(e);
                    if (code == 0xdf) break;   // end of track
                    p += 4;
                }
            } else {
                int n = note4 ? 4 : 3;
                if (p + n > end) break;
                e.kind = 0; e.a = st >> 6; e.b = st & 0x3f;
                int gate = d[p + 2] & 0xff;
                int vo = note4 ? d[p + 3] & 0xff : 0xff;   // velocity(6) | octave(2)
                e.c = gate | vo << 8;
                out.add(e);
                p += n;
            }
        }
    }

    // ------------------------------------------------------------------ sampled sound
    private static short[] toPcm(ArrayList<Ev> evs) {
        float gain = 50f / 63f;
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        for (Ev e : evs) {
            if (e.kind == 1 && e.a == 0xe8) gain = (e.b & 0x3f) / 63f;           // wave channel volume
            if (e.kind == 2 && e.data.length > 7) {
                int fmt = e.data[1] & 0x3f;
                if (fmt == 4) frames.write(e.data, 7, e.data.length - 7);        // QCELP packets
            }
        }
        byte[] f = frames.toByteArray();
        Qcelp dec = new Qcelp();
        float[] buf = new float[160];
        ArrayList<short[]> parts = new ArrayList<short[]>();
        int p = 0, total = 0;
        while (p < f.length) {
            int sz = Qcelp.packetSize(f[p] & 0xff);
            if (sz < 0 || p + sz > f.length) break;
            dec.decode(f, p, sz, buf);
            short[] s = new short[160];
            for (int i = 0; i < 160; i++) {
                float v = buf[i] * gain * 32767f;
                s[i] = (short) (v > 32767 ? 32767 : v < -32768 ? -32768 : v);
            }
            parts.add(s); total += 160; p += sz;
        }
        short[] out = new short[total];
        int o = 0;
        for (short[] s : parts) { System.arraycopy(s, 0, out, o, 160); o += 160; }
        return out;
    }

    // ------------------------------------------------------------------ songs
    private static final class Out implements Comparable<Out> {
        long ms; int order; byte[] msg;
        public int compareTo(Out o) { return ms != o.ms ? Long.compare(ms, o.ms) : Integer.compare(order, o.order); }
    }

    private static byte[] toMidi(ArrayList<Ev> evs, Clip clip) {
        // tempo map (global, by tick)
        ArrayList<int[]> tempo = new ArrayList<int[]>();   // tick, timebase, bpm
        for (Ev e : evs) if (e.kind == 1 && (e.a & 0xf0) == 0xc0) tempo.add(new int[]{e.tick, TIMEBASE[e.a & 15], Math.max(20, e.b)});
        Collections.sort(tempo, new Comparator<int[]>() { public int compare(int[] x, int[] y) { return Integer.compare(x[0], y[0]); } });
        final int[] tt = new int[tempo.size()], tb = new int[tempo.size()], bpm = new int[tempo.size()];
        for (int i = 0; i < tt.length; i++) { tt[i] = tempo.get(i)[0]; tb[i] = tempo.get(i)[1]; bpm[i] = tempo.get(i)[2]; }

        // which (track, channel) pairs are drums
        HashMap<Integer, Boolean> drum = new HashMap<Integer, Boolean>();
        for (Ev e : evs) if (e.kind == 1 && e.a == 0xe1 && (e.b & 0x3f) == 0x3f) drum.put(e.track * 4 + (e.b >> 6), true);
        HashMap<Integer, Integer> chan = new HashMap<Integer, Integer>();
        int next = 0;
        int[] bank = new int[64];
        int[] vol = new int[64];
        java.util.Arrays.fill(vol, 50);
        int master = 100;

        ArrayList<Out> out = new ArrayList<Out>();
        int order = 0;
        long last = 0;
        for (Ev e : evs) {
            long ms = ms(e.tick, tt, tb, bpm);
            if (e.kind == 0) {
                int key = chanKey(e.track, e.a);
                int ch = midiChannel(key, drum, chan, next); if (!chan.containsKey(key)) { chan.put(key, ch); if (ch != 9) next = ch + 1; }
                int k = e.b;
                if (k == 0x3f) continue;
                int oct = (e.c >> 8) & 3, vel6 = (e.c >> 10) & 0x3f;
                int shift = oct == 1 ? 12 : oct == 2 ? -24 : oct == 3 ? -12 : 0;
                int note = 45 + k + shift;
                if (note < 0 || note > 127) continue;
                int vel = Math.max(1, vel6 * 127 / 63);
                int gate = Math.max(1, e.c & 0xff);
                long offMs = ms(e.tick + gate, tt, tb, bpm);
                out.add(msg(ms, order++, 0x90 | ch, note, vel));
                out.add(msg(offMs, order++, 0x80 | ch, note, 0));
                last = Math.max(last, offMs);
                continue;
            }
            if (e.kind != 1) continue;
            int code = e.a, v = e.b;
            if (code < 0x80) {   // fine pitch bend: channel (3 bits) + 13-bit value
                int key = chanKey(e.track, code >> 5);
                int ch = midiChannel(key, drum, chan, next); if (!chan.containsKey(key)) { chan.put(key, ch); if (ch != 9) next = ch + 1; }
                int bend = ((code & 0x1f) << 8 | v) * 2;
                out.add(msg(ms, order++, 0xe0 | ch, bend & 0x7f, (bend >> 7) & 0x7f));
                continue;
            }
            if (code == 0xb0) { master = v & 0x7f; continue; }
            if (code == 0xdf) { last = Math.max(last, ms); continue; }
            if (code >= 0xe0 && code <= 0xe7) {
                int key = chanKey(e.track, v >> 6), val = v & 0x3f;
                int ch = midiChannel(key, drum, chan, next); if (!chan.containsKey(key)) { chan.put(key, ch); if (ch != 9) next = ch + 1; }
                switch (code) {
                    case 0xe0: if (ch != 9) out.add(msg(ms, order++, 0xc0 | ch, Math.min(127, val + (bank[key] == 3 ? 64 : 0)), -1)); break;
                    case 0xe1: bank[key] = val; break;
                    case 0xe2: vol[key] = val; out.add(msg(ms, order++, 0xb0 | ch, 7, Math.min(127, val * 2 * master / 100))); break;
                    case 0xe3: out.add(msg(ms, order++, 0xb0 | ch, 10, Math.min(127, val * 2))); break;
                    case 0xe4: { int bend = 8192 + (val - 32) * 256; bend = Math.max(0, Math.min(16383, bend));
                        out.add(msg(ms, order++, 0xe0 | ch, bend & 0x7f, bend >> 7)); break; }
                    case 0xe7:
                        out.add(msg(ms, order++, 0xb0 | ch, 101, 0)); out.add(msg(ms, order++, 0xb0 | ch, 100, 0));
                        out.add(msg(ms, order++, 0xb0 | ch, 6, Math.min(24, val))); out.add(msg(ms, order++, 0xb0 | ch, 38, 0));
                        break;
                    default: break;
                }
            }
        }
        Collections.sort(out);
        clip.durationMs = (int) last;
        // SMF format 0, 500 ticks per quarter at 500000 us per quarter = 1 tick per ms
        ByteArrayOutputStream trk = new ByteArrayOutputStream();
        writeVar(trk, 0); trk.write(0xff); trk.write(0x51); trk.write(3); trk.write(0x07); trk.write(0xa1); trk.write(0x20);
        for (int ch = 0; ch < 16; ch++) {   // default channel volume 50 and centred pan
            writeVar(trk, 0); trk.write(0xb0 | ch); trk.write(7); trk.write(Math.min(127, 100 * master / 100));
        }
        long now = 0;
        for (Out o : out) {
            writeVar(trk, (int) (o.ms - now)); now = o.ms;
            trk.write(o.msg, 0, o.msg.length);
        }
        writeVar(trk, (int) Math.max(0, last - now)); trk.write(0xff); trk.write(0x2f); trk.write(0);
        ByteArrayOutputStream smf = new ByteArrayOutputStream();
        byte[] t = trk.toByteArray();
        smf.write('M'); smf.write('T'); smf.write('h'); smf.write('d');
        smf.write(0); smf.write(0); smf.write(0); smf.write(6);
        smf.write(0); smf.write(0); smf.write(0); smf.write(1); smf.write(500 >> 8); smf.write(500 & 0xff);
        smf.write('M'); smf.write('T'); smf.write('r'); smf.write('k');
        smf.write(t.length >>> 24); smf.write(t.length >>> 16); smf.write(t.length >>> 8); smf.write(t.length);
        smf.write(t, 0, t.length);
        return smf.toByteArray();
    }

    private static int chanKey(int track, int ch) { return (track * 4 + (ch & 3)) & 63; }

    private static int midiChannel(int key, HashMap<Integer, Boolean> drum, HashMap<Integer, Integer> chan, int next) {
        Integer c = chan.get(key);
        if (c != null) return c;
        if (drum.containsKey(key)) return 9;
        int n = next == 9 ? 10 : next;
        return n > 15 ? 15 : n;
    }

    private static Out msg(long ms, int order, int status, int d1, int d2) {
        Out o = new Out(); o.ms = ms; o.order = order;
        o.msg = d2 < 0 ? new byte[]{(byte) status, (byte) d1} : new byte[]{(byte) status, (byte) d1, (byte) d2};
        return o;
    }

    /** Milliseconds at a tick position under the tempo map (default 48 ticks/beat at 125 BPM). */
    private static long ms(int tick, int[] tt, int[] tb, int[] bpm) {
        double t = 0; int pos = 0; int base = 48, tempo = 125;
        for (int i = 0; i < tt.length && tt[i] <= tick; i++) {
            t += (tt[i] - pos) * 60000.0 / (tempo * base);
            pos = tt[i]; base = tb[i]; tempo = bpm[i];
        }
        t += (tick - pos) * 60000.0 / (tempo * base);
        return Math.round(t);
    }

    private static void writeVar(ByteArrayOutputStream o, int v) {
        if (v < 0) v = 0;
        int buf = v & 0x7f;
        while ((v >>= 7) > 0) { buf <<= 8; buf |= 0x80 | (v & 0x7f); }
        while (true) { o.write(buf & 0xff); if ((buf & 0x80) != 0) buf >>= 8; else break; }
    }
}
