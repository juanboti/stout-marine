import brewemu.audio.Mixer;
import brewemu.brew.*;
import java.io.*;
import java.util.*;

/**
 * Plays the game automatically to exercise sounds: walks forward, turns when blocked, fires often and
 * clicks through dialogs. Logs every sound request and renders the sound effects to a WAV file.
 * args: gameDir outDir minutes [seed]   (-Dclassic=true for the 176 x 208 screen)
 */
public class Explore {
    static int[] last; static int lw, lh; static long frameHash;

    public static void main(String[] a) throws Exception {
        final File dir = new File(a[0]), out = new File(a[1]);
        out.mkdirs();
        final File data = new File(out, "data"); data.mkdirs();
        long total = (long) (Double.parseDouble(a[2]) * 60000);
        Random rnd = new Random(a.length > 3 ? Long.parseLong(a[3]) : 1);
        final long[] now = {0};
        final Mixer mixer = new Mixer(22050);
        final TreeMap<Integer, Integer> clipCounts = new TreeMap<Integer, Integer>();
        final int[] pcmPlays = {0}, midiPlays = {0}, vibes = {0};
        Host host = new Host() {
            public void present(int[] argb, int w, int h) {
                last = argb; lw = w; lh = h; frameHash = Arrays.hashCode(argb);
                if (miniMap != null) {   // -Dminimap=true: read the mini-map after every frame, as the app does
                    long t0 = System.nanoTime();
                    boolean ok = miniMap.read(brewRef[0]);
                    if (ok) { int size = w >= 240 ? 75 : 52; if (mapPx.length != size * size) mapPx = new int[size * size]; miniMap.drawAround(mapPx, size, w >= 240 ? 5 : 4, 0x99000000); mapSize = size; }
                    else mapSize = 0;
                    long dt = System.nanoTime() - t0; mapMaxNs = Math.max(mapMaxNs, dt); mapTotalNs += dt;
                    if (ok) mapOk++; else mapNo++;
                }
            }
            public File dataDir() { return data; }
            public void vibrate(int ms) { if (ms > 0) vibes[0]++; }
            public int playPcm(short[] pcm, int rate, int volume) {
                pcmPlays[0]++;
                Integer k = clipCounts.get(pcm.length); clipCounts.put(pcm.length, k == null ? 1 : k + 1);
                return mixer.play(pcm, rate, volume);
            }
            public int playMidi(byte[] smf, int volume) { midiPlays[0]++; return -1; }
            public void stopSound(int h) { mixer.stop(h); }
            public void setVolume(int h, int v) { mixer.setVolume(h, v); }
            public boolean soundPlaying(int h) { return mixer.playing(h); }
            public void log(String s) { System.out.println("[" + now[0] + "] " + s); }
            public void closeApplet() { System.out.println("closeApplet"); }
        };
        Installer.Game g = Installer.load(dir);
        boolean classic = Boolean.getBoolean("classic");      // default: the large 240 x 320 screen, like the app
        miniMap = Boolean.getBoolean("minimap") ? new brewemu.doomrpg.DoomMap() : null;
        Brew b = new Brew(host, Installer.read(g.mod), Installer.read(g.bar), g.barName, classic ? 176 : 240, classic ? 208 : 320, g.clsid);
        b.enableDoomRpgVibrateOption();                       // as the app does
        brewRef[0] = b;
        b.clock = new Brew.Clock() { public long uptimeMs() { return now[0]; } };
        if (!b.start()) throw new IllegalStateException("start failed");
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        long mixed = 0, nextAction = 11000, shot = 0;
        int blocked = 0;
        long before = 0;
        while (now[0] < total && !b.closed) {
            if (now[0] >= nextAction) {
                int r = rnd.nextInt(100);
                int key;
                if (now[0] < 36000) key = Brew.AVK_SELECT;                  // menus and intro
                else if (blocked > 1) { key = rnd.nextBoolean() ? Brew.AVK_RIGHT : Brew.AVK_LEFT; blocked = 0; }
                else if (r < 45) key = Brew.AVK_UP;
                else if (r < 80) key = Brew.AVK_SELECT;                     // fire / use / talk
                else if (r < 86) key = Brew.AVK_RIGHT;
                else if (r < 92) key = Brew.AVK_LEFT;
                else if (r < 96) key = Brew.AVK_0 + 3;
                else key = Brew.AVK_0 + 1;
                if (key == Brew.AVK_UP) before = frameHash;
                b.keyDown(key); b.keyUp(key);
                nextAction = now[0] + (now[0] < 36000 ? 2500 : 450);
                if (key == Brew.AVK_UP) pendingCheck = now[0] + 400;
            }
            if (pendingCheck > 0 && now[0] >= pendingCheck) { if (frameHash == before) blocked++; pendingCheck = 0; }
            b.runTimers();
            long nd = b.nextDue();
            now[0] += nd == Long.MAX_VALUE ? 15 : Math.max(1, Math.min(15, nd - now[0]));
            long want = now[0] * mixer.rate / 1000;
            int n = (int) (want - mixed);
            if (n > 0) { short[] buf = new short[n]; mixer.mix(buf, n); for (short x : buf) { wav.write(x & 0xff); wav.write((x >> 8) & 0xff); } mixed = want; }
            if (now[0] / 30000 != shot) { shot = now[0] / 30000; save(new File(out, String.format("shot%03d.png", shot))); }
        }
        byte[] pcm = wav.toByteArray();
        DataOutputStream w = new DataOutputStream(new FileOutputStream(new File(out, "effects.wav")));
        w.writeBytes("RIFF"); w.writeInt(Integer.reverseBytes(36 + pcm.length)); w.writeBytes("WAVEfmt ");
        w.writeInt(Integer.reverseBytes(16)); w.writeShort(Short.reverseBytes((short) 1)); w.writeShort(Short.reverseBytes((short) 1));
        w.writeInt(Integer.reverseBytes(22050)); w.writeInt(Integer.reverseBytes(44100)); w.writeShort(Short.reverseBytes((short) 2)); w.writeShort(Short.reverseBytes((short) 16));
        w.writeBytes("data"); w.writeInt(Integer.reverseBytes(pcm.length)); w.write(pcm); w.close();
        System.out.println("screen " + lw + "x" + lh + ", sound effects played: " + pcmPlays[0] + ", songs: " + midiPlays[0] + ", vibrations: " + vibes[0]);
        System.out.println("effects by clip length (samples): " + clipCounts);
        System.out.println("heap in use " + b.heap.inUse + " peak " + b.heap.peak);
        if (miniMap != null) System.out.println("mini-map: shown on " + mapOk + " frames, hidden on " + mapNo
                + ", average " + (mapTotalNs / Math.max(1, mapOk + mapNo) / 1000) + " us, slowest " + (mapMaxNs / 1000) + " us");
    }
    static long pendingCheck;
    static brewemu.doomrpg.DoomMap miniMap;
    static final Brew[] brewRef = new Brew[1];
    static int[] mapPx = new int[0];
    static int mapSize, mapOk, mapNo;
    static long mapMaxNs, mapTotalNs;

    static void save(File f) throws IOException {
        if (last == null) return;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(lw, lh, java.awt.image.BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, lw, lh, last, 0, lw);
        if (mapSize > 0) {   // the mini-map as the app shows it: top-right, see-through
            java.awt.Graphics2D gr = img.createGraphics();
            java.awt.image.BufferedImage m = new java.awt.image.BufferedImage(mapSize, mapSize, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            m.setRGB(0, 0, mapSize, mapSize, mapPx, 0, mapSize);
            gr.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 210 / 255f));
            gr.drawImage(m, lw - mapSize - 3, 21, null);
            gr.dispose();
        }
        javax.imageio.ImageIO.write(img, "png", f);
    }
}
