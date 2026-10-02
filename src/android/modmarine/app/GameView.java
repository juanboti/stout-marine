package modmarine.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;



/** Renders the game's screen scaled up, plus a pixel-art touch-control deck. */
final class GameView extends View {
    // control codes (KeyPump turns them into BREW keys): arrows, select, the two soft keys, CLR and phone keys
    static final int K_UP = -1, K_DOWN = -2, K_LEFT = -3, K_RIGHT = -4, K_FIRE = -5,
            K_MENU = -6, K_MAP = -7, K_CLR = -8, K_STRAFE_L = '1', K_STRAFE_R = '3',
            K_WPN_PREV = '*', K_WPN_NEXT = '7', K_WAIT = '9';
    /** Not a game key: opens / closes the number keys (Select on a controller). */
    static final int K_KEYPAD = -20;


    final KeyPump keys;
    /** Opened by holding the gear (menu) button. */
    Runnable settings;
    private final float dp;

    // framebuffer
    private final Object frameLock = new Object();
    private int[] front;
    private int fw, fh;
    private boolean dirty;
    private Bitmap bmp;
    private final Rect src = new Rect();
    private final RectF gameRect = new RectF();
    private final Paint bmpPaint = new Paint();
    /** Picture setting: Prefs.PICTURE_SHARP / SMOOTH / HQ / XBR. */
    private volatile int picture;
    private volatile Upscaler upscaler;
    private Bitmap upBmp;
    private volatile int upFactor = 2;

    // pixel deck
    private final Deck deck;
    private Bitmap deckBmp;
    private boolean deckDirty = true;
    private final Paint deckPaint = new Paint();
    private final RectF deckDst = new RectF();
    private boolean hidden; // hidden while a gamepad / keyboard is in use

    // swipe on the game picture
    private int swPointer = -1;
    private float swX, swY;
    private long swAt;
    private boolean swDone;
    private int swKey;
    private long swLastRepeat;
    private long lastPulse;

    GameView(Context c, KeyPump keys, int gameW, int gameH) { this(c, keys, gameW, gameH, Deck.MODE_TOUCH); }

    /** mode: Deck.MODE_TOUCH / MODE_HANDHELD (game + side panels). */
    GameView(Context c, KeyPump keys, int gameW, int gameH, int mode) {
        super(c);
        this.keys = keys;
        dp = c.getResources().getDisplayMetrics().density;
        deck = new Deck(dp, gameW, gameH);
        deck.mode = mode;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
        setHapticFeedbackEnabled(true);
        picture = Prefs.picture(c);
        deckPaint.setFilterBitmap(false);
        deckPaint.setAntiAlias(false);
        post(ticker);
    }

    // ---------------------------------------------------------------- frames
    void submitFrame(int[] argb, int w, int h) {
        synchronized (frameLock) {
            if (front == null || front.length != argb.length) front = new int[argb.length];
            System.arraycopy(argb, 0, front, 0, argb.length);
            fw = w; fh = h; dirty = true;
        }
        Upscaler u = upscaler;
        int pic = picture;
        if (u != null && (pic == Prefs.PICTURE_HQ || pic == Prefs.PICTURE_XBR)) u.submit(argb, w, h, pic, upFactor);
        postInvalidateOnAnimation();
    }

    int picture() { return picture; }

    /** Changes the Picture setting (and saves it). */
    void setPicture(int p) {
        picture = p;
        Prefs.setPicture(getContext(), p);
        if (p == Prefs.PICTURE_HQ || p == Prefs.PICTURE_XBR) {
            if (upscaler == null) { upscaler = new Upscaler(this); upscaler.start(); }
            upscaler.clear();
            synchronized (frameLock) { if (front != null) upscaler.submit(front, fw, fh, p, upFactor); }
        }
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (upscaler == null && (picture == Prefs.PICTURE_HQ || picture == Prefs.PICTURE_XBR)) { upscaler = new Upscaler(this); upscaler.start(); }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (upscaler != null) { upscaler.quit(); upscaler = null; }
    }

    // ---------------------------------------------------------------- layout
    /** Switches between the touch deck and handheld mode (re-lays out at once). */
    void setMode(int mode) {
        if (deck.mode == mode) return;
        deck.mode = mode;
        deck.keypadOn = false;
        if (getWidth() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        if (mode != Deck.MODE_TOUCH) hidden = false;
        invalidate();
    }

    int mode() { return deck.mode; }

    // ---------------------------------------------------------------- number keys
    private int cursorHeld;   // game key held by the controller's A on the big keypad (0 = none)

    boolean keypadOpen() { return deck.keypadOn; }
    /** Handheld mode with the number keys open: the big keypad, worked with the d-pad and A. */
    boolean bigKeypad() { return deck.bigKeypad; }

    void toggleKeypad() { setKeypad(!deck.keypadOn); }

    /** Opens / closes the number keys; in handheld mode this switches to / from the big keypad. */
    void setKeypad(boolean on) {
        if (deck.keypadOn == on) return;
        cursorPress(false);
        deck.keypadOn = on;
        if (on) { deck.cursor = 4; hidden = false; }      // cursor starts on 5
        if (deck.mode == Deck.MODE_HANDHELD && getWidth() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        changed();
    }

    void moveCursor(int dx, int dy) {
        if (!deck.bigKeypad) return;
        cursorPress(false);
        deck.moveCursor(dx, dy);
        changed();
    }

    /** A on the big keypad: presses (down) / releases the key under the cursor. Returns false if nothing was held. */
    boolean cursorPress(boolean down) {
        if (!down) {
            if (cursorHeld == 0) return false;
            keys.release(cursorHeld);
            for (int i = 0; i < deck.keypad.size(); i++) if (deck.keypad.get(i).pointer == -2) deck.keypad.get(i).pointer = -1;
            cursorHeld = 0;
            changed();
            return true;
        }
        if (!deck.bigKeypad || deck.cursor < 0 || deck.cursor >= deck.keypad.size() || cursorHeld != 0) return false;
        Deck.Btn b = deck.keypad.get(deck.cursor);
        haptic();
        if (b.key == Deck.K_CLOSE) { setKeypad(false); return true; }
        b.pointer = -2;           // drawn pressed
        cursorHeld = b.key;
        keys.press(b.key);
        changed();
        return true;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        for (int i = 0; i < deck.buttons.size(); i++) if (deck.buttons.get(i).pointer != -1 && deck.buttons.get(i).key != 0 && !deck.buttons.get(i).longPress) keys.release(deck.buttons.get(i).key);
        for (int i = 0; i < deck.keypad.size(); i++) if (deck.keypad.get(i).pointer >= 0) keys.release(deck.keypad.get(i).key);
        if (cursorHeld != 0) { keys.release(cursorHeld); cursorHeld = 0; }
        boolean keypad = deck.keypadOn;
        deck.layout(w, h);
        deck.keypadOn = keypad;
        gameRect.set(deck.game[0], deck.game[1], deck.game[2], deck.game[3]);
        // hq / xBR factor: about the size the picture is shown at, 2x to 3x (more costs time and adds little)
        float shown = deck.gW > 0 ? gameRect.width() / deck.gW : 2;
        upFactor = shown < 2.5f ? 2 : 3;
        deckBmp = Bitmap.createBitmap(deck.artW, deck.artH, Bitmap.Config.ARGB_8888);
        deckDst.set(0, 0, deck.artW * deck.artPx, deck.artH * deck.artPx);
        deckDirty = true;
    }

    // ---------------------------------------------------------------- drawing
    @Override protected void onDraw(Canvas c) {
        c.drawColor(Color.BLACK);
        if (!hidden && deckBmp != null) {
            if (deckDirty) {
                deck.render();
                deckBmp.setPixels(deck.art.px, 0, deck.artW, 0, 0, deck.artW, deck.artH);
                deckDirty = false;
            }
            c.drawBitmap(deckBmp, null, deckDst, deckPaint);
        }
        synchronized (frameLock) {
            if (front != null) {
                if (bmp == null || bmp.getWidth() != fw || bmp.getHeight() != fh) {
                    bmp = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
                    dirty = true;
                }
                if (dirty) { bmp.setPixels(front, 0, fw, 0, 0, fw, fh); dirty = false; }
            }
        }
        Bitmap up = null;
        if (upscaler != null && (picture == Prefs.PICTURE_HQ || picture == Prefs.PICTURE_XBR)) {
            up = upscaler.latest(upBmp, picture);
            if (up != null) upBmp = up;
        }
        if (up != null) {
            // the filter's result, scaled the rest of the way smoothly
            src.set(0, 0, up.getWidth(), up.getHeight());
            bmpPaint.setFilterBitmap(true);
            c.drawBitmap(up, src, gameRect, bmpPaint);
        } else if (bmp != null) {
            src.set(0, 0, bmp.getWidth(), bmp.getHeight());
            // Smooth, or the big keypad (it shrinks the game to an uneven size), or a filter's first frame not ready yet
            bmpPaint.setFilterBitmap(picture != Prefs.PICTURE_SHARP || deck.bigKeypad);
            c.drawBitmap(bmp, src, gameRect, bmpPaint);
        }
    }

    // ---------------------------------------------------------------- input
    /** Button-tap feedback for this app only; follows the app's Vibration setting, never changes phone settings. */
    private void haptic() { if (Prefs.vibrate(getContext())) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); }

    /** Hides the touch deck while a controller is used (touch mode only; handheld panels always show). */
    void setHidden(boolean h) {
        if (h && deck.mode != Deck.MODE_TOUCH) return;
        if (hidden != h) { hidden = h; invalidate(); }
    }

    private void changed() { deckDirty = true; invalidate(); }

    /** A tap on the game's own bottom bar presses its labels (left: Menu/Back, right: Map/Leave); elsewhere it fires. */
    private int softKeyAt(float x, float y) {
        float gx = (x - gameRect.left) / gameRect.width();
        float gy = (y - gameRect.top) / gameRect.height();
        if (gy >= 1f - 0.07f) {
            if (gx < 1f / 3) return K_MENU;
            if (gx > 2f / 3) return K_MAP;
        }
        return K_FIRE;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        setHidden(false);
        int act = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (act) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                down(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                up(e.getPointerId(idx), false);
                break;
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < e.getPointerCount(); i++) up(e.getPointerId(i), true);
                break;
        }
        return true;
    }

    private void down(int id, float x, float y) {
        long now = SystemClock.uptimeMillis();
        float ax = x / deck.artPx, ay = y / deck.artPx;
        if (deck.keypadOn) {
            for (int i = 0; i < deck.keypad.size(); i++) {
                Deck.Btn b = deck.keypad.get(i);
                if (!b.hit(ax, ay, 0)) continue;
                haptic();
                if (b.key == Deck.K_CLOSE) { setKeypad(false); return; }
                b.pointer = id; keys.press(b.key); changed(); return;
            }
        }
        if (!gameRect.contains(x, y)) {
            for (int i = 0; i < deck.buttons.size(); i++) {
                Deck.Btn b = deck.buttons.get(i);
                if (deck.blocked(b) || !b.hit(ax, ay, 2)) continue;
                b.pointer = id; b.downAt = now; b.lastRepeat = now; b.longFired = false;
                haptic();
                if (b.key == 0) { b.pointer = -1; setKeypad(!deck.keypadOn); return; }
                if (!b.longPress) keys.press(b.key);
                changed();
                return;
            }
        }
        if (gameRect.contains(x, y) && swPointer == -1 && !deck.bigKeypad) {   // no firing by accident while typing a code
            swPointer = id; swX = x; swY = y; swAt = now; swDone = false; swKey = 0;
        }
    }

    private void move(int id, float x, float y) {
        // sliding a finger across the movement block switches buttons
        for (int i = 0; i < deck.buttons.size(); i++) {
            Deck.Btn b = deck.buttons.get(i);
            if (b.pointer != id || b.kind != Deck.CELL) continue;
            float ax = x / deck.artPx, ay = y / deck.artPx;
            if (b.hit(ax, ay, 1)) return;
            for (int j = 0; j < deck.buttons.size(); j++) {
                Deck.Btn n = deck.buttons.get(j);
                if (n.kind == Deck.CELL && n != b && n.hit(ax, ay, 0)) {
                    keys.release(b.key); b.pointer = -1;
                    long now = SystemClock.uptimeMillis();
                    n.pointer = id; n.downAt = now; n.lastRepeat = now;
                    keys.press(n.key); haptic(); changed();
                    return;
                }
            }
            return;
        }
        if (id == swPointer && !swDone) {
            float dx = x - swX, dy = y - swY, th = 28 * dp;
            if (Math.abs(dx) > th || Math.abs(dy) > th) {
                if (Math.abs(dx) > Math.abs(dy)) swKey = dx > 0 ? K_RIGHT : K_LEFT;
                else swKey = dy > 0 ? K_DOWN : K_UP;
                swDone = true;
                swLastRepeat = SystemClock.uptimeMillis();
                keys.tap(swKey);
                haptic();
            }
        }
    }

    private void up(int id, boolean cancel) {
        boolean any = false;
        for (int i = 0; i < deck.keypad.size(); i++) {
            Deck.Btn b = deck.keypad.get(i);
            if (b.pointer == id) { b.pointer = -1; keys.release(b.key); any = true; }
        }
        for (int i = 0; i < deck.buttons.size(); i++) {
            Deck.Btn b = deck.buttons.get(i);
            if (b.pointer == id) {
                b.pointer = -1; any = true;
                if (b.longPress) { if (!b.longFired && !cancel) keys.tap(b.key); }
                else if (b.key != 0) keys.release(b.key);
            }
        }
        if (any) changed();
        if (id == swPointer) {
            swPointer = -1;
            if (!cancel && !swDone && SystemClock.uptimeMillis() - swAt < 350) { keys.tap(softKeyAt(swX, swY)); haptic(); }
            swKey = 0;
        }
    }

    // auto-repeat for held movement controls, long-press for the menu button
    private final Runnable ticker = new Runnable() {
        public void run() {
            long now = SystemClock.uptimeMillis();
            final long delay = 320, rate = 170;
            for (int i = 0; i < deck.buttons.size(); i++) {
                Deck.Btn b = deck.buttons.get(i);
                if (b.repeat && b.pointer != -1 && now - b.downAt > delay && now - b.lastRepeat > rate) {
                    b.lastRepeat = now; keys.release(b.key); keys.press(b.key);
                }
                if (b.longPress && b.pointer != -1 && !b.longFired && now - b.downAt > 700) {
                    b.longFired = true; b.pointer = -1;
                    if (Prefs.vibrate(getContext())) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    changed();
                    if (settings != null) settings.run();
                }
            }
            // the keypad toggle pulses while the number keys are showing
            if (deck.keypadOn && now - lastPulse > 450) { lastPulse = now; deck.pulse = !deck.pulse; changed(); }
            // swipe and hold keeps walking / turning
            if (swPointer != -1 && swDone && swKey != 0 && now - swLastRepeat > delay + rate) {
                swLastRepeat = now - delay; keys.tap(swKey);
            }
            postDelayed(this, 30);
        }
    };
}
