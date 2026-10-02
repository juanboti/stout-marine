package modmarine.app;

import android.graphics.Bitmap;
import android.view.View;

import brewemu.video.Hqx;
import brewemu.video.Xbr;

/**
 * Smart picture filters (hq, xBR) on their own thread: the newest game frame is scaled up while the game
 * keeps running; frames that arrive while one is being scaled are skipped (only the newest one counts).
 */
final class Upscaler extends Thread {
    static final int HQ = 2, XBR = 3;   // the Picture setting's values for these filters

    private final View view;
    private final Object lock = new Object();
    // newest frame waiting to be scaled
    private int[] in = new int[0];
    private int iw, ih, mode, factor;
    private boolean pending, quit;
    // newest result
    private int[] out = new int[0];
    private int ow, oh, outMode;
    private long serial;
    private long shown = -1;

    Upscaler(View v) {
        super("picture");
        view = v;
        setDaemon(true);
        setPriority(Thread.NORM_PRIORITY - 1);
    }

    /** Queues a frame (copied) for scaling with the given filter and factor (2..4). */
    void submit(int[] argb, int w, int h, int filter, int n) {
        synchronized (lock) {
            if (in.length != argb.length) in = new int[argb.length];
            System.arraycopy(argb, 0, in, 0, argb.length);
            iw = w; ih = h; mode = filter; factor = n;
            pending = true;
            lock.notifyAll();
        }
    }

    void quit() { synchronized (lock) { quit = true; lock.notifyAll(); } }

    /** Forgets the last result (after a filter change), so nothing stale is shown. */
    void clear() { synchronized (lock) { ow = oh = 0; shown = -1; } }

    /**
     * Puts the newest result for this filter into bmp (a new bitmap if the size changed) and returns it,
     * or null when there is no result yet.
     */
    Bitmap latest(Bitmap bmp, int filter) {
        synchronized (lock) {
            if (ow == 0 || outMode != filter) return null;
            if (bmp == null || bmp.getWidth() != ow || bmp.getHeight() != oh) {
                bmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
                shown = -1;
            }
            if (shown != serial) { bmp.setPixels(out, 0, ow, 0, 0, ow, oh); shown = serial; }
            return bmp;
        }
    }

    @Override public void run() {
        int[] src = new int[0], dst = new int[0], yuv = new int[0];
        try {
            while (true) {
                int w, h, m, n;
                synchronized (lock) {
                    while (!pending && !quit) lock.wait();
                    if (quit) return;
                    if (src.length != in.length) src = new int[in.length];
                    System.arraycopy(in, 0, src, 0, in.length);
                    w = iw; h = ih; m = mode; n = factor;
                    pending = false;
                }
                if (w <= 0 || h <= 0 || src.length < w * h) continue;
                if (dst.length != w * n * h * n) dst = new int[w * n * h * n];
                if (yuv.length < w * h) yuv = new int[w * h];
                if (m == XBR) Xbr.scale(src, w, h, n, dst, yuv); else Hqx.scale(src, w, h, n, dst, yuv);
                synchronized (lock) {
                    int[] t = out; out = dst; dst = t;         // swap buffers
                    ow = w * n; oh = h * n; outMode = m;
                    serial++;
                }
                view.postInvalidateOnAnimation();
            }
        } catch (InterruptedException e) {
            // closing
        } catch (Throwable t) {
            android.util.Log.e("ModMarine", "picture filter stopped", t);
        }
    }
}
