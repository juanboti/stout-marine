package modmarine.app;

import brewemu.brew.Brew;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The emulator thread: runs the applet's timers in real time and delivers key events between them.
 * All guest code runs on this one thread.
 */
final class KeyPump extends Thread {
    private static final int PRESS = 0, RELEASE = 1, SUSPEND = 2, RESUME = 3;
    private final LinkedBlockingQueue<int[]> q = new LinkedBlockingQueue<int[]>();
    private final Brew brew;
    private final AndroidHost host;
    private boolean paused;

    KeyPump(Brew b, AndroidHost h) { super("brew"); brew = b; host = h; setDaemon(true); }

    void press(int code) { int k = avk(code); if (k != 0) q.offer(new int[]{PRESS, k}); }
    void release(int code) { int k = avk(code); if (k != 0) q.offer(new int[]{RELEASE, k}); }
    void tap(int code) { press(code); release(code); }
    void suspendGame() { q.offer(new int[]{SUSPEND, 0}); }
    void resumeGame() { q.offer(new int[]{RESUME, 0}); }

    /** Control-deck key codes to BREW key codes. */
    static int avk(int code) {
        switch (code) {
            case GameView.K_UP: return Brew.AVK_UP;
            case GameView.K_DOWN: return Brew.AVK_DOWN;
            case GameView.K_LEFT: return Brew.AVK_LEFT;
            case GameView.K_RIGHT: return Brew.AVK_RIGHT;
            case GameView.K_FIRE: return Brew.AVK_SELECT;
            case GameView.K_MENU: return Brew.AVK_SOFT1;
            case GameView.K_MAP: return Brew.AVK_SOFT2;
            case GameView.K_CLR: return Brew.AVK_CLR;
            case '*': return Brew.AVK_STAR;
            case '#': return Brew.AVK_POUND;
            default:
                if (code >= '0' && code <= '9') return Brew.AVK_0 + (code - '0');
                return 0;
        }
    }

    public void run() {
        try {
            if (!brew.start()) { host.showMessage("The game didn't start (wrong or damaged game files?)."); return; }
            while (!brew.closed) {
                long wait = paused ? 1000 : Math.min(1000, brew.nextDue() - brew.clock.uptimeMs());
                int[] e = wait <= 0 ? q.poll() : q.poll(wait, TimeUnit.MILLISECONDS);
                while (e != null && !brew.closed) {
                    switch (e[0]) {
                        case PRESS: if (!paused) brew.keyDown(e[1]); break;
                        case RELEASE: if (!paused) brew.keyUp(e[1]); break;
                        case SUSPEND: if (!paused) { paused = true; brew.suspend(); host.stopAllSounds(); } break;
                        case RESUME: if (paused) { paused = false; brew.resume(); } break;
                    }
                    e = q.poll();
                }
                if (!paused && !brew.closed) brew.runTimers();
            }
        } catch (InterruptedException ie) {
            return;
        } catch (Throwable t) {
            android.util.Log.e("ModMarine", "emulation stopped", t);
            host.showMessage("The game stopped: " + t.getMessage());
            return;
        }
        host.closeApplet();
    }
}
