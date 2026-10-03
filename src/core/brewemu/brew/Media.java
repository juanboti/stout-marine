package brewemu.brew;

import brewemu.audio.Cmx;
import brewemu.cpu.Memory;
import java.util.HashMap;
import java.util.zip.CRC32;

/** IMedia for CMX clips: decoded once, played by the host, completion reported through the notify callback. */
final class Media extends BObj {
    static final int PARM_MEDIA_DATA = 1, PARM_VOLUME = 4;
    static final int STATE_READY = 1, STATE_PLAYING = 3;
    private static final HashMap<Long, Cmx.Clip> cache = new HashMap<Long, Cmx.Clip>();

    private Cmx.Clip clip;
    private int notifyFn, notifyUser, volume = 100;
    private int handle = -1;
    private boolean playing;
    private long started;

    Media(Brew b) { super(b, Brew.K_MEDIA, 16); b.medias.add(this); }

    @Override public int invoke(int slot) {
        Memory m = b.mem;
        switch (slot) {
            case 2: if (arg(2) != 0) m.write32(arg(2), 0); return Brew.ECLASSNOTSUPPORT;   // QueryInterface
            case 3: notifyFn = arg(1); notifyUser = arg(2); return Brew.SUCCESS;           // RegisterNotify
            case 4: {   // SetMediaParm(parm, p1, p2)
                int parm = arg(1), p1 = arg(2);
                if (parm == PARM_MEDIA_DATA && p1 != 0) {
                    int data = m.read32(p1 + 4), size = m.read32(p1 + 8);
                    clip = load(data, size);
                    return clip != null ? Brew.SUCCESS : Brew.EUNSUPPORTED;
                }
                if (parm == PARM_VOLUME) {
                    volume = Math.max(0, Math.min(100, p1));
                    if (handle >= 0) b.host.setVolume(handle, volume);   // as on the phone: heard at once, also mid-song
                    return Brew.SUCCESS;
                }
                return Brew.EUNSUPPORTED;
            }
            case 5: {   // GetMediaParm(parm, p1*, p2*)
                if (arg(1) == PARM_VOLUME && arg(2) != 0) { m.write32(arg(2), volume); return Brew.SUCCESS; }
                return Brew.EUNSUPPORTED;
            }
            case 6: play(); return Brew.SUCCESS;                                              // Play
            case 8: stop(); return Brew.SUCCESS;                                              // Stop
            case 10: stop(); return Brew.SUCCESS;                                             // Pause
            case 11: return Brew.SUCCESS;                                                     // Resume
            case 12: return clip != null ? Brew.SUCCESS : Brew.EFAILED;                       // GetTotalTime (async in BREW)
            case 13: if (arg(1) != 0) m.write8(arg(1), 0); return playing ? STATE_PLAYING : STATE_READY;   // GetState
            default: return super.invoke(slot);
        }
    }

    private Cmx.Clip load(int data, int size) {
        if (size <= 0 || size > (4 << 20)) return null;
        byte[] d = new byte[size];
        b.mem.read(data, d, 0, size);
        CRC32 crc = new CRC32(); crc.update(d);
        long key = crc.getValue() << 24 ^ size;
        Cmx.Clip c = cache.get(key);
        if (c == null) {
            try { c = Cmx.decode(d, 0, size); } catch (RuntimeException e) { b.host.log("clip decode failed: " + e); c = null; }
            if (c != null) cache.put(key, c);
        }
        return c;
    }

    private void play() {
        stop();
        if (clip == null || !b.soundOn) return;
        handle = clip.pcm != null ? b.host.playPcm(clip.pcm, 8000, volume) : b.host.playMidi(clip.midi, volume);
        playing = true;
        started = b.clock.uptimeMs();
    }

    private void stop() {
        if (handle >= 0) b.host.stopSound(handle);
        handle = -1;
        playing = false;
    }

    /** Called from the emulator loop: reports finished clips to the module. */
    void poll() {
        if (!playing || dead) return;
        boolean done = handle < 0 ? b.clock.uptimeMs() - started >= clip.durationMs : !b.host.soundPlaying(handle);
        if (!done) return;
        playing = false; handle = -1;
        if (notifyFn != 0) {
            int n = b.heap.alloc(28);
            if (n == 0) return;
            Memory m = b.mem;
            m.write32(n, Brew.CLS_MEDIA); m.write32(n + 4, addr); m.write32(n + 8, 4); m.write32(n + 12, 0);
            m.write32(n + 16, 2); m.write32(n + 20, 0); m.write32(n + 24, 0);
            b.call(notifyFn, notifyUser, n);
            b.heap.release(n);
        }
    }

    @Override protected void destroy() { stop(); b.medias.remove(this); }
}
