package modmarine.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import brewemu.brew.Brew;
import brewemu.brew.Installer;

import java.io.File;

/** Runs the installed BREW game with touch controls. */
public final class GameActivity extends Activity {
    // The emulated phone lives for the whole process (it survives rotation and activity restarts).
    private static AndroidHost host;
    private static KeyPump keys;
    private static Brew brew;
    private static int gameW, gameH;
    private static volatile boolean paused;

    private GameView view;
    private final Handler ui = new Handler();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final File dir = SetupActivity.gameDir(this);
        if (brew == null && !Installer.isInstalled(dir)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        if (host == null) host = new AndroidHost(this);
        host.activity = this;
        host.miniMap = Prefs.miniMap(this);
        if (brew == null) {
            try {
                Installer.Game g = Installer.load(dir);
                boolean large = Prefs.large(this);
                gameW = large ? 240 : 176;
                gameH = large ? 320 : 208;
                brew = new Brew(host, Installer.read(g.mod), Installer.read(g.bar), g.barName, gameW, gameH, g.clsid);
                // this phone can vibrate and play sound together: lets the game offer its own Vibrate option
                if (brew.enableDoomRpgVibrateOption()) vibrateOnByDefault(brew);
                keys = new KeyPump(brew, host);
                host.brew = brew;
            } catch (Throwable t) {
                android.util.Log.e("ModMarine", "load failed", t);
                brew = null;
                showError("Couldn't load the game files: " + t.getMessage() + "\n\nUse \"Choose file\" to install them again.");
                return;
            }
        }
        view = new GameView(this, keys, gameW, gameH, handheldWanted() ? Deck.MODE_HANDHELD : Deck.MODE_TOUCH);
        view.settings = new Runnable() { public void run() { showSettings(); } };
        host.view = view;
        setContentView(view);
        view.requestFocus();
        if (keys.getState() == Thread.State.NEW) keys.start();
        int version = About.versionCode(this);
        if (!Prefs.shownHelp(this)) {
            // first start: the help covers everything, so no "What's new"
            Prefs.setShownHelp(this);
            Prefs.setSeenVersion(this, version);
            view.post(new Runnable() { public void run() { if (!isFinishing()) Help.show(GameActivity.this, null); } });
        } else if (Prefs.seenVersion(this) < version) {
            // first start after an update: show what changed, once
            Prefs.setSeenVersion(this, version);
            view.post(new Runnable() { public void run() { if (!isFinishing()) About.showWhatsNew(GameActivity.this); } });
        }
    }

    // Leaving with Home / Recents: the game waits in the background, but Android may close it there.
    private long lastSaveReminder;

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastSaveReminder != 0 && now - lastSaveReminder < 10 * 60 * 1000L) return;   // at most every 10 minutes
        lastSaveReminder = now;
        android.widget.Toast.makeText(getApplicationContext(),
                "Stout Marine is paused. Save in the game first (MENU, then Save Game): "
                + "Android may close it while you're away.", android.widget.Toast.LENGTH_LONG).show();
    }

    /**
     * The game's own Vibrate option starts on, once per install; after that the player's choice in the game's
     * Options menu is kept. States: 0 not done, 1 switched on in a fresh game that has not saved its
     * settings yet, 2 done.
     */
    private void vibrateOnByDefault(Brew b) {
        final android.content.SharedPreferences p = Prefs.p(this);
        int state = p.getInt("vibrate_default", 0);
        if (state == 2) return;
        boolean hasSettings = new File(AndroidHost.saveDir(this), "Config").isFile();
        if (hasSettings) {
            // state 1: the game saved its settings itself since (on, unless the player turned it off): keep them
            if (state == 0) b.switchDoomRpgVibrateOnInFile(AndroidHost.saveDir(this));
            p.edit().putInt("vibrate_default", 2).commit();
        } else {
            b.vibrateDefaultApplied = new Runnable() { public void run() { p.edit().putInt("vibrate_default", 1).commit(); } };
            b.switchDoomRpgVibrateOnAtStart();
        }
    }

    private void showError(String msg) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Stout Marine")
            .setMessage(msg)
            .setPositiveButton("Choose file", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    Installer.uninstall(SetupActivity.gameDir(GameActivity.this));
                    startActivity(new Intent(GameActivity.this, SetupActivity.class));
                    finish();
                }
            })
            .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { finish(); }
            })
            .setCancelable(false)
            .show();
    }

    private void showSettings() {
        final boolean vib = Prefs.vibrate(this), large = Prefs.large(this), map = Prefs.miniMap(this);
        String[] items = {
            "Help: what each button does",
            "About Stout Marine",
            "Picture: " + PICTURE_NAMES[view != null ? view.picture() : Prefs.picture(this)],
            "Mini-map: " + (map ? "on" : "off"),
            "Vibration: " + (vib ? "on" : "off"),
            "Screen size: " + (large ? "large (240 x 320)" : "classic (176 x 208)"),
            "Handheld mode: " + handheldLabel(),
            "Export saved games",
            "Import saved games",
            "Quit Stout Marine",
            "Reinstall game file",
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Stout Marine settings")
            .setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) Help.show(GameActivity.this, null);
                    else if (which == 1) About.show(GameActivity.this);
                    else if (which == 2) choosePicture();
                    else if (which == 3) { Prefs.setMiniMap(GameActivity.this, !map); host.miniMap = !map; }
                    else if (which == 4) Prefs.p(GameActivity.this).edit().putBoolean("vibrate", !vib).apply();
                    else if (which == 5) confirmScreenSize(!large);
                    else if (which == 6) { Prefs.setHandheld(GameActivity.this, (Prefs.handheld(GameActivity.this) + 1) % 3); updateMode(); }
                    else if (which == 7) SaveTransfer.startExport(GameActivity.this, "StoutMarine-saves.zip");
                    else if (which == 8) confirmImport();
                    else if (which == 9) confirmQuit();
                    else if (which == 10) confirmReload();
                }
            })
            .setNegativeButton("Close", null)
            .show();
    }

    // ---------------------------------------------------------------- picture
    private static final String[] PICTURE_NAMES = {"sharp pixels", "smooth", "smart upscale (hq)", "xBR upscale"};

    private void choosePicture() {
        String[] items = {
            "Sharp pixels \u2014 the original look",
            "Smooth \u2014 soft and blended",
            "Smart upscale (hq) \u2014 smoother edges, keeps the pixel look",
            "xBR upscale \u2014 rounder, more like a drawing",
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Picture")
            .setSingleChoiceItems(items, view.picture(), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) { view.setPicture(which); d.dismiss(); }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ---------------------------------------------------------------- handheld mode
    /** A real game controller (built in, like on the RG Rotate or AYN Thor, or connected). */
    private static boolean hasGameController() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            int s = d.getSources();
            if ((s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return true;
        }
        return false;
    }

    private boolean handheldWanted() {
        int m = Prefs.handheld(this);
        return m == Prefs.HANDHELD_ON || (m == Prefs.HANDHELD_AUTO && hasGameController());
    }

    private String handheldLabel() {
        switch (Prefs.handheld(this)) {
            case Prefs.HANDHELD_ON: return "always";
            case Prefs.HANDHELD_OFF: return "off";
            default: return "automatic (" + (hasGameController() ? "on now" : "off now") + ")";
        }
    }

    /** Applies handheld mode after a setting or a controller change. */
    private void updateMode() {
        if (view == null) return;
        view.setMode(handheldWanted() ? Deck.MODE_HANDHELD : Deck.MODE_TOUCH);
    }

    private final InputManager.InputDeviceListener devices = new InputManager.InputDeviceListener() {
        public void onInputDeviceAdded(int id) { updateMode(); }
        public void onInputDeviceRemoved(int id) { updateMode(); }
        public void onInputDeviceChanged(int id) { updateMode(); }
    };

    private void confirmScreenSize(final boolean large) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(large ? "Use the large screen?" : "Use the classic screen?")
            .setMessage((large
                    ? "The game is told the phone has a 240 x 320 screen and shows the biggest view (the default). "
                      + "It needs more processing power, so it can run slower on older phones."
                    : "The game is told the phone has a 176 x 208 screen. The view is smaller, "
                      + "but it runs faster on older phones.")
                    + "\n\nThe game closes to switch. Save first: tap MENU, then choose Save Game. Open Stout Marine again afterwards.")
            .setPositiveButton("Switch and close", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    Prefs.setLarge(GameActivity.this, large);
                    quitNow();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmImport() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Import saved games?")
            .setMessage("This replaces the saved games on this phone with the ones in the .zip. The game closes afterwards; open Stout Marine again to continue.")
            .setPositiveButton("Choose .zip", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { SaveTransfer.startImport(GameActivity.this); }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            File saves = AndroidHost.saveDir(this);
            if (req == SaveTransfer.REQ_EXPORT) {
                int n = SaveTransfer.export(this, saves, data.getData());
                SaveTransfer.toast(this, n == 0 ? "No saved games yet." : "Exported " + n + " save file" + (n == 1 ? "" : "s") + ".");
            } else if (req == SaveTransfer.REQ_IMPORT) {
                int n = SaveTransfer.importZip(this, saves, data.getData());
                SaveTransfer.toast(this, "Imported " + n + " save file" + (n == 1 ? "" : "s") + ". Open Stout Marine again to continue.");
                getWindow().getDecorView().postDelayed(new Runnable() { public void run() { quitNow(); } }, 1800);
            }
        } catch (Exception e) {
            SaveTransfer.toast(this, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private void quitNow() {
        if (host != null) host.stopAllSounds();
        finishAndRemoveTask();
        getWindow().getDecorView().postDelayed(new Runnable() { public void run() {
            android.os.Process.killProcess(android.os.Process.myPid());
        } }, 300);
    }

    private void confirmQuit() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Quit Stout Marine?")
            .setMessage("Anything you haven't saved will be lost.\n\nTo keep your progress, cancel and save first: "
                    + "tap MENU, then choose Save Game.")
            .setPositiveButton("Quit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { quitNow(); }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmReload() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Reinstall game file?")
            .setMessage("Use this if the wrong file was picked or the game won't start. "
                    + "You'll choose the game's .zip again. The game closes; your saved games are kept.")
            .setPositiveButton("Choose file", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (host != null) host.stopAllSounds();
                    Installer.uninstall(SetupActivity.gameDir(GameActivity.this));
                    startActivity(new Intent(GameActivity.this, SetupActivity.class));
                    finish();
                    // the game lives in static state; restart the process cleanly
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override protected void onResume() {
        super.onResume();
        immersive();
        if (paused && keys != null) { paused = false; keys.resumeGame(); }
        InputManager im = (InputManager) getSystemService(INPUT_SERVICE);
        if (im != null) im.registerInputDeviceListener(devices, ui);
        updateMode();
    }

    @Override protected void onPause() {
        super.onPause();
        if (keys != null && !paused) { paused = true; keys.suspendGame(); }
        InputManager im = (InputManager) getSystemService(INPUT_SERVICE);
        if (im != null) im.unregisterInputDeviceListener(devices);
        releaseSticks();
        ui.removeCallbacks(menuHold);
        menuDown = false;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (host != null && host.view == view) host.view = null;
        if (isFinishing() && brew != null) {
            host.stopAllSounds();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @SuppressWarnings("deprecation")
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    // ---- physical keyboards / gamepads
    private int map(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_W: return GameView.K_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_S: return GameView.K_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return GameView.K_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return GameView.K_RIGHT;
            case KeyEvent.KEYCODE_A: case KeyEvent.KEYCODE_BUTTON_L1: return GameView.K_STRAFE_L;
            case KeyEvent.KEYCODE_D: case KeyEvent.KEYCODE_BUTTON_R1: return GameView.K_STRAFE_R;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_BUTTON_A: return GameView.K_FIRE;
            case KeyEvent.KEYCODE_BUTTON_START: return GameView.K_SETTINGS;
            case KeyEvent.KEYCODE_ESCAPE: case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_MENU: return GameView.K_MENU;
            case KeyEvent.KEYCODE_TAB: case KeyEvent.KEYCODE_M:
            case KeyEvent.KEYCODE_BUTTON_Y: return GameView.K_MAP;
            case KeyEvent.KEYCODE_BUTTON_SELECT: case KeyEvent.KEYCODE_K: return GameView.K_KEYPAD;
            case KeyEvent.KEYCODE_BUTTON_L2: case KeyEvent.KEYCODE_Q: return GameView.K_WPN_PREV;
            case KeyEvent.KEYCODE_BUTTON_R2: case KeyEvent.KEYCODE_E: return GameView.K_WPN_NEXT;
            case KeyEvent.KEYCODE_BUTTON_X: return GameView.K_WAIT;
            case KeyEvent.KEYCODE_DEL: return GameView.K_CLR;
            case KeyEvent.KEYCODE_STAR: return '*';
            case KeyEvent.KEYCODE_POUND: return '#';
        }
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) return '0' + (code - KeyEvent.KEYCODE_0);
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) return '0' + (code - KeyEvent.KEYCODE_NUMPAD_0);
        return 0;
    }

    private boolean fromController(KeyEvent e) {
        int s = e.getSource();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD && e.getKeyCode() != KeyEvent.KEYCODE_BACK;
    }

    // The menu button: a short press opens the game's menu, holding it opens these settings.
    private boolean menuDown, menuHeld;
    private final Runnable menuHold = new Runnable() {
        public void run() {
            if (!menuDown) return;
            menuHeld = true;
            if (view != null && Prefs.vibrate(GameActivity.this)) view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            showSettings();
        }
    };

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        int k = map(code);
        if (k == 0 || keys == null) return super.onKeyDown(code, e);
        if (code == KeyEvent.KEYCODE_BUTTON_L2 || code == KeyEvent.KEYCODE_BUTTON_R2) triggerButtons();
        if (fromController(e) && view != null) view.setHidden(true);
        if (k == GameView.K_KEYPAD) {
            if (e.getRepeatCount() == 0 && view != null) { releaseSticks(); view.toggleKeypad(); }
            return true;
        }
        if (k == GameView.K_SETTINGS) {
            if (e.getRepeatCount() == 0) { releaseSticks(); showSettings(); }
            return true;
        }
        if (view != null && view.bigKeypad()) return bigKeypadDown(code, k, e);
        if (k == GameView.K_MENU) {
            if (e.getRepeatCount() == 0) { menuDown = true; menuHeld = false; ui.postDelayed(menuHold, 700); }
            return true;
        }
        if (e.getRepeatCount() > 0) keys.release(k);
        keys.press(k);
        return true;
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        int k = map(code);
        if (k == 0 || keys == null) return super.onKeyUp(code, e);
        if (k == GameView.K_KEYPAD || k == GameView.K_SETTINGS) return true;
        if (view != null && view.bigKeypad()) return bigKeypadUp(code, k);
        if (k == GameView.K_MENU) {
            ui.removeCallbacks(menuHold);
            if (menuDown && !menuHeld) keys.tap(GameView.K_MENU);
            menuDown = false;
            return true;
        }
        keys.release(k);
        return true;
    }

    // ---- the big keypad (handheld mode, number keys open): d-pad moves the cursor, A presses, B closes.
    // Other game buttons do nothing meanwhile, so a code can't make you walk or fire by accident.
    private boolean bigMenuDown;

    /** Keys that type straight into the game even while the keypad is open (keyboards). */
    private static boolean phoneKey(int code) {
        return (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) || (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9)
                || code == KeyEvent.KEYCODE_STAR || code == KeyEvent.KEYCODE_POUND || code == KeyEvent.KEYCODE_DEL;
    }

    private boolean bigKeypadDown(int code, int k, KeyEvent e) {
        if (phoneKey(code)) { if (e.getRepeatCount() > 0) keys.release(k); keys.press(k); return true; }
        switch (k) {
            case GameView.K_UP: view.moveCursor(0, -1); break;
            case GameView.K_DOWN: view.moveCursor(0, 1); break;
            case GameView.K_LEFT: view.moveCursor(-1, 0); break;
            case GameView.K_RIGHT: view.moveCursor(1, 0); break;
            case GameView.K_FIRE: if (e.getRepeatCount() == 0) view.cursorPress(true); break;
            case GameView.K_MENU: if (e.getRepeatCount() == 0) bigMenuDown = true; break;
            default: break;
        }
        return true;
    }

    private boolean bigKeypadUp(int code, int k) {
        if (phoneKey(code)) { keys.release(k); return true; }
        if (k == GameView.K_FIRE) {
            if (!view.cursorPress(false)) keys.release(k);     // A held since before the keypad opened
        } else if (k == GameView.K_MENU) {
            ui.removeCallbacks(menuHold);
            menuDown = false;
            if (bigMenuDown) view.setKeypad(false);
            bigMenuDown = false;
        } else keys.release(k);                                // a game button held since before the keypad opened
        return true;
    }

    // ---- analog sticks and hat-style d-pads (some controllers report the d-pad as an axis)
    private int stickX, stickY;   // control code held for each axis (0 = none)
    private long stickSince;

    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        int src = e.getSource();
        if (keys == null || e.getAction() != MotionEvent.ACTION_MOVE
                || ((src & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                    && (src & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD)) return super.onGenericMotionEvent(e);
        float x = e.getAxisValue(MotionEvent.AXIS_HAT_X), y = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (Math.abs(x) < 0.5f) x = e.getAxisValue(MotionEvent.AXIS_X);
        if (Math.abs(y) < 0.5f) y = e.getAxisValue(MotionEvent.AXIS_Y);
        boolean big = view != null && view.bigKeypad();
        if (!big) triggers(e);
        int nx = x <= -0.5f ? GameView.K_LEFT : x >= 0.5f ? GameView.K_RIGHT : 0;
        int ny = y <= -0.5f ? GameView.K_UP : y >= 0.5f ? GameView.K_DOWN : 0;
        if (nx != stickX || ny != stickY) {
            if (view != null) view.setHidden(true);
            if (stickX != 0 && stickX != nx) keys.release(stickX);
            if (stickY != 0 && stickY != ny) keys.release(stickY);
            if (big) {
                if (ny != 0 && ny != stickY) view.moveCursor(0, ny == GameView.K_UP ? -1 : 1);
                else if (nx != 0 && nx != stickX) view.moveCursor(nx == GameView.K_LEFT ? -1 : 1, 0);
            } else {
                if (nx != 0 && nx != stickX) keys.press(nx);
                if (ny != 0 && ny != stickY) keys.press(ny);
            }
            stickX = nx; stickY = ny;
            stickSince = android.os.SystemClock.uptimeMillis();
            ui.removeCallbacks(stickRepeat);
            if (nx != 0 || ny != 0) ui.postDelayed(stickRepeat, 320);
        }
        return true;
    }

    /** Holding a stick keeps walking / turning, like holding a d-pad button. */
    private final Runnable stickRepeat = new Runnable() {
        public void run() {
            if (keys == null || (stickX == 0 && stickY == 0)) return;
            if (view != null && view.bigKeypad()) {
                if (stickY != 0) view.moveCursor(0, stickY == GameView.K_UP ? -1 : 1);
                else view.moveCursor(stickX == GameView.K_LEFT ? -1 : 1, 0);
            }
            else if (stickY != 0) { keys.release(stickY); keys.press(stickY); }
            else { keys.release(stickX); keys.press(stickX); }
            ui.postDelayed(this, 170);
        }
    };

    // ---- analog L2 / R2. Some handhelds send the triggers only as axes, others as buttons too; once a
    // trigger button is seen the axes are ignored, and an axis press waits briefly for that button so a
    // single pull never changes weapon twice.
    private boolean triggerKeys, trigL, trigR;
    private final Runnable trigLTap = new Runnable() { public void run() { if (!triggerKeys && keys != null) keys.tap(GameView.K_WPN_PREV); } };
    private final Runnable trigRTap = new Runnable() { public void run() { if (!triggerKeys && keys != null) keys.tap(GameView.K_WPN_NEXT); } };

    private void triggerButtons() {
        triggerKeys = true;
        ui.removeCallbacks(trigLTap); ui.removeCallbacks(trigRTap);
    }

    private void triggers(MotionEvent e) {
        float l = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
        float r = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
        boolean nl = trigL ? l > 0.3f : l > 0.6f, nr = trigR ? r > 0.3f : r > 0.6f;   // hysteresis
        if (nl && !trigL && !triggerKeys) { if (view != null) view.setHidden(true); ui.postDelayed(trigLTap, 80); }
        if (nr && !trigR && !triggerKeys) { if (view != null) view.setHidden(true); ui.postDelayed(trigRTap, 80); }
        trigL = nl; trigR = nr;
    }

    private void releaseSticks() {
        ui.removeCallbacks(trigLTap); ui.removeCallbacks(trigRTap);
        trigL = trigR = false;
        ui.removeCallbacks(stickRepeat);
        if (keys != null) { if (stickX != 0) keys.release(stickX); if (stickY != 0) keys.release(stickY); }
        stickX = stickY = 0;
    }
}
