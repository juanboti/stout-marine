package brewemu.audio;

import java.util.ArrayList;

/**
 * Software mixer for sampled sounds: any number of mono 16-bit clips are resampled (linear interpolation)
 * to one output rate and added together. The platform pulls finished audio with {@link #mix}.
 * Thread-safe: clips are started and stopped from the emulator thread while the audio thread mixes.
 */
public final class Mixer {
    private static final class Voice {
        int handle;
        short[] pcm;
        long pos;      // 32.32 fixed-point position in source samples
        long step;     // source samples per output sample, 32.32
        int gain;      // 0..65536
    }

    public final int rate;
    private final ArrayList<Voice> voices = new ArrayList<Voice>();
    private int nextHandle = 1;
    private int[] acc = new int[0];

    public Mixer(int outputRate) { rate = outputRate; }

    /** Starts a clip; volume 0..100. Returns its handle. */
    public synchronized int play(short[] pcm, int srcRate, int volume) {
        Voice v = new Voice();
        v.handle = nextHandle++;
        if (nextHandle <= 0) nextHandle = 1;
        v.pcm = pcm;
        v.step = ((long) srcRate << 32) / rate;
        v.gain = Math.max(0, Math.min(100, volume)) * 65536 / 100;
        if (pcm.length > 0) voices.add(v);
        notifyAll();
        return v.handle;
    }

    public synchronized void stop(int handle) {
        for (int i = voices.size() - 1; i >= 0; i--) if (voices.get(i).handle == handle) voices.remove(i);
    }

    public synchronized void stopAll() { voices.clear(); }

    /** True until the clip has been completely mixed (or stopped). */
    public synchronized boolean playing(int handle) {
        for (int i = 0; i < voices.size(); i++) if (voices.get(i).handle == handle) return true;
        return false;
    }

    public synchronized boolean idle() { return voices.isEmpty(); }

    /** Waits up to ms milliseconds for a clip to start; returns true if one is playing. */
    public synchronized boolean awaitSound(long ms) throws InterruptedException {
        if (voices.isEmpty() && ms > 0) wait(ms);
        return !voices.isEmpty();
    }

    /** Fills out[0..frames) with the mix of all playing clips; finished clips are dropped. */
    public synchronized void mix(short[] out, int frames) {
        if (acc.length < frames) acc = new int[frames];
        java.util.Arrays.fill(acc, 0, frames, 0);
        for (int i = voices.size() - 1; i >= 0; i--) {
            Voice v = voices.get(i);
            short[] s = v.pcm;
            int n = s.length;
            long pos = v.pos, step = v.step;
            int gain = v.gain;
            for (int k = 0; k < frames; k++) {
                int idx = (int) (pos >>> 32);
                if (idx >= n) break;
                int a = s[idx], b = idx + 1 < n ? s[idx + 1] : 0;
                int frac = (int) ((pos >>> 16) & 0xffff);
                int x = a + (((b - a) * frac) >> 16);
                acc[k] += (int) (((long) x * gain) >> 16);
                pos += step;
            }
            v.pos = pos;
            if ((int) (pos >>> 32) >= n) voices.remove(i);
        }
        for (int k = 0; k < frames; k++) {
            int x = acc[k];
            out[k] = (short) (x > 32767 ? 32767 : x < -32768 ? -32768 : x);
        }
    }
}
