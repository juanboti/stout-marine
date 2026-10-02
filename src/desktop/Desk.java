import brewemu.brew.*;
import java.io.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Desktop harness: runs the applet with a virtual clock, logs API use and saves frames as PNG.
 *  args: gameDir outDir ms [script]   script: "t=1000:k=E031,t=1200:u=E031,..."  (k=press, u=release,
 *  s=name saves a PNG, p / r suspend / resume the applet) */
public class Desk {
    static int frames; static long[] mark = new long[2];
    /** Sound effects are mixed exactly as on the phone; -Dwav=file.wav saves them (music is not rendered). */
    static final brewemu.audio.Mixer mixer = new brewemu.audio.Mixer(22050);
    static java.io.ByteArrayOutputStream audio; static long mixed;
    static int[] last; static int lw, lh;
    public static void main(String[] a) throws Exception {
        final File dir = new File(a[0]), out = new File(a[1]);
        out.mkdirs();
        final File data = new File(out, "data"); data.mkdirs();
        long total = Long.parseLong(a[2]);
        String script = a.length > 3 ? a[3] : "";
        int w = Integer.getInteger("w", 240), h = Integer.getInteger("h", 320);
        final long[] now = {0};
        Host host = new Host() {
            public void present(int[] argb, int w, int h) { frames++; last = argb.clone(); lw = w; lh = h; }
            public File dataDir() { return data; }
            public void vibrate(int ms) { System.out.println("[vibrate " + ms + "]"); }
            public int playPcm(short[] pcm, int rate, int volume) {
                System.out.println("[" + now[0] + "] [pcm " + pcm.length + " vol " + volume + "]");
                return mixer.play(pcm, rate, volume);
            }
            public int playMidi(byte[] smf, int volume) { System.out.println("[" + now[0] + "] [midi " + smf.length + " vol " + volume + "]"); return -1; }
            public void stopSound(int h) { mixer.stop(h); }
            public boolean soundPlaying(int h) { return mixer.playing(h); }
            public void log(String s) { System.out.println("[" + now[0] + "] " + s); }
            public void closeApplet() { System.out.println("closeApplet"); }
        };
        Brew b;
        if (Installer.isInstalled(dir)) {      // a folder written by the app's installer
            Installer.Game g = Installer.load(dir);
            b = new Brew(host, Installer.read(g.mod), Installer.read(g.bar), g.barName, w, h, g.clsid);
        } else {                               // loose doomrpg.mod / doomrpg.bar
            byte[] mod = Files.readAllBytes(new File(dir, "doomrpg.mod").toPath());
            byte[] bar = Files.readAllBytes(new File(dir, "doomrpg.bar").toPath());
            b = new Brew(host, mod, bar, "doomrpg.bar", w, h, Installer.DEFAULT_CLSID);
        }
        b.clock = new Brew.Clock() { public long uptimeMs() { return now[0]; } };
        if (System.getProperty("wav") != null) audio = new java.io.ByteArrayOutputStream();
        long t0 = System.nanoTime();
        b.enableDoomRpgVibrateOption();     // as the app does
        if (!Boolean.getBoolean("keepvibrate")) {   // the app does this once per install
            if (b.switchDoomRpgVibrateOnInFile(data)) System.out.println("Vibrate switched on in the settings file");
            else {
                b.vibrateDefaultApplied = new Runnable() { public void run() { System.out.println("[" + now[0] + "] Vibrate switched on in the running game"); } };
                b.switchDoomRpgVibrateOnAtStart();
            }
        }
        System.out.println("start -> " + b.start());
        String[] steps = script.isEmpty() ? new String[0] : script.split(",");
        int si = 0; long nextShot = 1000; int shot = 0;
        while (now[0] < total && !b.closed) {
            while (si < steps.length) {
                String[] p = steps[si].split(":");
                long t = Long.parseLong(p[0].substring(2));
                if (t > now[0]) break;
                for (int k = 1; k < p.length; k++) {
                    String kv = p[k];
                    if (kv.startsWith("k=")) b.keyDown(Integer.parseInt(kv.substring(2), 16));
                    else if (kv.startsWith("u=")) b.keyUp(Integer.parseInt(kv.substring(2), 16));
                    else if (kv.startsWith("s=")) save(new File(out, kv.substring(2) + ".png"));
                    else if (kv.equals("p")) b.suspend();
                    else if (kv.equals("r")) b.resume();
                }
                si++;
            }
            b.runTimers();
            long nd = b.nextDue();
            long step = nd == Long.MAX_VALUE ? 50 : Math.max(1, Math.min(50, nd - now[0]));
            now[0] += step;
            if (audio != null) {     // render the sound effects in step with the virtual clock
                long want = now[0] * mixer.rate / 1000;
                int n = (int) (want - mixed);
                if (n > 0) { short[] buf = new short[n]; mixer.mix(buf, n); for (short x : buf) { audio.write(x & 0xff); audio.write((x >> 8) & 0xff); } mixed = want; }
            }
            if (now[0] == 36000) { mark[0] = b.cpu.executed; mark[1] = frames; }
            if (now[0] >= nextShot) { save(new File(out, String.format("auto%03d.png", shot++))); nextShot += Long.getLong("every", 2000); }
        }
        save(new File(out, "final.png"));
        if (audio != null) writeWav(new File(System.getProperty("wav")), audio.toByteArray(), mixer.rate);
        if (mark[1] > 0) System.out.printf("after 36s: %d frames, %.2f M instructions per frame, %.1f M/s game time%n", frames - mark[1], (b.cpu.executed - mark[0]) / 1e6 / Math.max(1, frames - mark[1]), (b.cpu.executed - mark[0]) / 1e6 / ((now[0] - 36000) / 1000.0));
        System.out.printf("frames %d, cpu instructions %d, wall %.1fs, heap in use %d peak %d%n", frames, b.cpu.executed,
            (System.nanoTime() - t0) / 1e9, b.heap.inUse, b.heap.peak);
    }
    static void writeWav(File f, byte[] pcm, int rate) throws IOException {
        DataOutputStream w = new DataOutputStream(new FileOutputStream(f));
        try {
            w.writeBytes("RIFF"); w.writeInt(Integer.reverseBytes(36 + pcm.length)); w.writeBytes("WAVEfmt ");
            w.writeInt(Integer.reverseBytes(16)); w.writeShort(Short.reverseBytes((short) 1)); w.writeShort(Short.reverseBytes((short) 1));
            w.writeInt(Integer.reverseBytes(rate)); w.writeInt(Integer.reverseBytes(rate * 2));
            w.writeShort(Short.reverseBytes((short) 2)); w.writeShort(Short.reverseBytes((short) 16));
            w.writeBytes("data"); w.writeInt(Integer.reverseBytes(pcm.length)); w.write(pcm);
        } finally { w.close(); }
    }
    static void save(File f) throws IOException {
        if (last == null) return;
        BufferedImage img = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, lw, lh, last, 0, lw);
        ImageIO.write(img, "png", f);
    }
}
