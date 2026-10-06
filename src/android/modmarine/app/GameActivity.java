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
    private static volatile brewemu.doomrpg.DoomArt weaponArt;
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
        PadBinds.load(Prefs.padBinds(this));
        if (brew == null) {
            try {
                Installer.Game g = Installer.load(dir);
                boolean large = Prefs.large(this);
                gameW = large ? 240 : 176;
                gameH = large ? 320 : 208;
                final byte[] bar = Installer.read(g.bar);
                brew = new Brew(host, Installer.read(g.mod), bar, g.barName, gameW, gameH, g.clsid);
                // the weapon picker's pictures, read from the game file in the background
                Thread t = new Thread("art") { public void run() {
                    weaponArt = brewemu.doomrpg.DoomArt.load(bar);
                    ui.post(new Runnable() { public void run() { if (view != null) view.setWeaponArt(weaponArt); } });
                } };
                t.setDaemon(true); t.start();
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
        if (weaponArt != null) view.setWeaponArt(weaponArt);
        host.view = view;
        setContentView(view);
        view.requestFocus();
        if (keys.getState() == Thread.State.NEW) keys.start();
        int version = About.versionCode(this);
        if (!Prefs.shownHelp(this)) {
            // first start: the extras window (what's on, and where to change it), then a short tour of the
            // touch buttons (handheld: the help). No "What's new".
            Prefs.setShownHelp(this);
            Prefs.setSeenVersion(this, version);
            view.post(new Runnable() { public void run() {
                if (isFinishing()) return;
                showExtras(new Runnable() { public void run() {
                    if (view.touchTourAvailable()) {
                        view.startTour(new Runnable() { public void run() { Prefs.setTourSeen(GameActivity.this); } });
                    } else Help.show(GameActivity.this, null);
                } });
            } });
        } else if (!Prefs.extrasSeen(this)) {
            // first start after updating to a version with the extras window: it also says what's new
            Prefs.setSeenVersion(this, version);
            view.post(new Runnable() { public void run() { if (!isFinishing()) showExtras(null); } });
        } else if (Prefs.seenVersion(this) < version) {
            // first start after an update: show what changed, once
            Prefs.setSeenVersion(this, version);
            view.post(new Runnable() { public void run() { if (!isFinishing()) showWhatsNew(); } });
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
                "Stout Marine is paused. Save first (Save now in the settings, or the game's menu, Save Game): "
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

    /**
     * First start: Stout Marine's extras, what each is set to now, and how to change them (the gear, holding
     * the menu button, or Start). "Show me" opens the settings; then (or on "Play") next runs.
     */
    private void showExtras(final Runnable next) {
        if (view == null) return;
        Prefs.setExtrasSeen(this);
        final boolean pad = view.mode() == Deck.MODE_HANDHELD;
        final PixelMenu m = new PixelMenu();
        m.title = "STOUT MARINE EXTRAS";
        m.say("Stout Marine adds these extras to the original game. This is how they are set now. "
                + "To change any of them, or turn them off, open the settings: "
                + (pad ? "press START." : "tap the gear, or hold the menu button."));
        info(m, "XP BAR", Prefs.xpBar(this) ? "ON" : "OFF");
        info(m, "MINI-MAP", MAP_NAMES[Prefs.miniMap(this)]);
        info(m, "PICTURE", PICTURE_LONG[view.picture()]);
        info(m, "SCREEN", Prefs.large(this) ? "LARGE 240X320" : "CLASSIC 176X208");
        info(m, "VIBRATION", VIB_NAMES[Prefs.vibration(this)]);
        final Runnable after = new Runnable() { public void run() { if (next != null) next.run(); } };
        m.add(PixelMenu.BUTTON, null, "SHOW ME THE SETTINGS", null, new Runnable() { public void run() {
            view.closeAllMenus();
            showSettings(pad);
            PixelMenu s = view.topMenu();
            if (s != null) s.onClose = after;
        } }).gap = true;
        m.add(PixelMenu.BUTTON, null, "PLAY", null, new Runnable() { public void run() { view.closeAllMenus(); after.run(); } });
        m.onClose = after;
        m.hintPad = "DPAD + " + PadBinds.nameOf(PadBinds.FIRE); m.hintTouch = "TAP PLAY TO START";
        view.openMenu(m, pad);
        m.cursor = m.items.size() - 1;   // on Play
        view.menuChanged();
    }

    private static final String[] PICTURE_LONG = {"SHARP PIXELS", "SMOOTH", "SMART HQ", "XBR"};

    /** "What's new" after an update, in a pixel window. */
    private void showWhatsNew() {
        if (view == null) return;
        PixelMenu m = new PixelMenu();
        m.title = "WHAT'S NEW"; m.corner = About.version(this);
        m.say(About.WHATS_NEW_SHORT);
        m.add(PixelMenu.BUTTON, null, "OK", null, new Runnable() { public void run() { view.closeAllMenus(); } }).gap = true;
        m.firstSelectable();
        view.openMenu(m, false);
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

    // ---------------------------------------------------------------- settings (pixel windows)
    private boolean menuByController;

    /** The gear, Start or holding the menu button: Stout Marine's settings (again: closes them). */
    private void showSettings() { showSettings(false); }

    private void showSettings(boolean controller) {
        if (view == null) return;
        if (view.menuOpen()) { view.closeAllMenus(); return; }
        menuByController = controller;
        PixelMenu m = new PixelMenu();
        m.title = "SETTINGS"; m.corner = "STOUT MARINE " + About.version(this);
        m.hintTouch = "TAP OUTSIDE TO CLOSE"; m.hintPad = PadBinds.hint("CLOSE");
        fillSettings(m);
        view.openMenu(m, controller);
    }

    private static final String[] PICTURE_SHORT = {"SHARP", "SMOOTH", "HQ", "XBR"};
    private static final String[] MAP_NAMES = {"OFF", "CORNER", "BIG"};
    private static final String[] VIB_NAMES = {"OFF", "LIGHT", "STRONG"};

    private void fillSettings(final PixelMenu m) {
        final GameActivity a = this;
        m.items.clear();
        m.add(PixelMenu.OPEN, Icons.I_HELP, "HELP", null, new Runnable() { public void run() { view.closeAllMenus(); Help.show(a, null); } });
        m.add(PixelMenu.OPEN, Icons.I_INFO, "ABOUT", null, new Runnable() { public void run() { view.closeAllMenus(); About.show(a); } });
        m.add(PixelMenu.OPEN, Icons.I_SAVE, "SAVE NOW", null, new Runnable() { public void run() {
            view.closeAllMenus(); if (keys != null) keys.saveNow(); } }).gap = true;
        m.add(PixelMenu.OPEN, Icons.I_STATS, "STATS", null, new Runnable() { public void run() { showStats(); } });
        final int pic = view.picture();
        PixelMenu.Item it = m.add(PixelMenu.CHOICE, Icons.I_PIC, "PICTURE", PICTURE_SHORT[pic], new Runnable() { public void run() { showPicture(); } });
        it.gap = true;
        it.left = new Runnable() { public void run() { view.setPicture((view.picture() + 3) % 4); refresh(m); } };
        it.right = new Runnable() { public void run() { view.setPicture((view.picture() + 1) % 4); refresh(m); } };
        final int map = Prefs.miniMap(this);
        it = m.add(PixelMenu.CHOICE, Icons.MAP, "MINI-MAP", MAP_NAMES[map], new Runnable() { public void run() { setMap((map + 1) % 3); refresh(m); } });
        it.right = it.act; it.left = new Runnable() { public void run() { setMap((map + 2) % 3); refresh(m); } };
        final boolean xpb = Prefs.xpBar(this);
        it = m.add(PixelMenu.SWITCH, Icons.I_XP, "XP BAR", null, new Runnable() { public void run() {
            Prefs.setXpBar(a, !xpb); view.setXpBar(!xpb); refresh(m); } });
        it.on = xpb;
        final int vib = Prefs.vibration(this);
        it = m.add(PixelMenu.CHOICE, Icons.I_VIB, "VIBRATION", VIB_NAMES[vib], new Runnable() { public void run() { setVib((vib + 1) % 3); refresh(m); } });
        it.right = it.act; it.left = new Runnable() { public void run() { setVib((vib + 2) % 3); refresh(m); } };
        final boolean large = Prefs.large(this);
        m.add(PixelMenu.CHOICE, Icons.I_SCR, "SCREEN", large ? "LARGE" : "CLASSIC", new Runnable() { public void run() { confirmScreenSize(!large); } });
        final int hh = Prefs.handheld(this);
        it = m.add(PixelMenu.CHOICE, Icons.I_PAD, "HANDHELD", hh == Prefs.HANDHELD_ON ? "ALWAYS" : hh == Prefs.HANDHELD_OFF ? "OFF" : "AUTO", new Runnable() { public void run() {
            Prefs.setHandheld(a, (hh + 1) % 3); updateMode(); refresh(m); } });
        it.right = it.act; it.left = new Runnable() { public void run() { Prefs.setHandheld(a, (hh + 2) % 3); updateMode(); refresh(m); } };
        PixelMenu.Item first = null;
        if (view.canEditTouch()) first = m.add(PixelMenu.OPEN, Icons.I_MOVE, "EDIT TOUCH CONTROLS", null, new Runnable() { public void run() { view.startEditing(); } });
        PixelMenu.Item pad = m.add(PixelMenu.OPEN, Icons.I_PAD, "CONTROLLER BUTTONS", null, new Runnable() { public void run() { showPad(); } });
        (first != null ? first : pad).gap = true;
        m.add(PixelMenu.OPEN, Icons.I_UP, "EXPORT SAVES", null, new Runnable() { public void run() {
            view.closeAllMenus(); SaveTransfer.startExport(a, "StoutMarine-saves.zip"); } }).gap = true;
        m.add(PixelMenu.OPEN, Icons.I_DOWN, "IMPORT SAVES", null, new Runnable() { public void run() { confirmImport(); } });
        m.add(PixelMenu.OPEN, Icons.I_REDO, "NEW GAME FILE", null, new Runnable() { public void run() { confirmReload(); } }).gap = true;
        m.add(PixelMenu.OPEN, Icons.I_QUIT, "QUIT", null, new Runnable() { public void run() { confirmQuit(); } }).danger = true;
    }

    /** Rebuilds the settings rows after a change (the cursor stays where it was). */
    private void refresh(PixelMenu m) {
        int c = m.cursor;
        fillSettings(m);
        m.cursor = c;
        if (view != null) view.menuChanged();
    }

    private void setMap(int mode) { Prefs.setMiniMap(this, mode); host.miniMap = mode; }

    private void setVib(int level) {
        Prefs.setVibration(this, level);
        if (level != Prefs.VIB_OFF && host != null) host.vibrate(120);   // a sample, so you can feel the strength
    }

    /** A question in a pixel window: yes runs the action, Cancel (or B, or a tap outside) goes back. */
    private void ask(String title, String text, String yes, boolean danger, final Runnable action) {
        final PixelMenu q = new PixelMenu();
        q.title = title; q.say(text);
        q.add(PixelMenu.BUTTON, null, yes, null, new Runnable() { public void run() { view.closeAllMenus(); action.run(); } }).danger = danger;
        q.add(PixelMenu.BUTTON, null, "CANCEL", null, new Runnable() { public void run() { view.closeMenu(); } });
        q.hintPad = PadBinds.hint("BACK");
        view.openMenu(q, menuByController);
        if (!menuByController) q.cursor = -1;   // touch: nothing highlighted until tapped
        view.menuChanged();
    }

    // ---------------------------------------------------------------- picture
    private void showPicture() {
        view.makePreviews();
        final PixelMenu p = new PixelMenu();
        p.title = "PICTURE";
        String[][] opt = {{"SHARP PIXELS", "ORIGINAL LOOK"}, {"SMOOTH", "SOFT, BLENDED"},
                {"SMART HQ", "SMOOTH EDGES"}, {"XBR", "ROUNDER LOOK"}};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            PixelMenu.Item it = p.add(PixelMenu.RADIO, null, opt[i][0], null, new Runnable() { public void run() {
                view.setPicture(k); view.closeMenu(); PixelMenu s = view.topMenu(); if (s != null) refresh(s); } });
            it.sub = opt[i][1]; it.on = i == view.picture(); it.preview = i;
        }
        p.hintTouch = "ONLY THE PICTURE CHANGES"; p.hintPad = PadBinds.hint("BACK");
        p.cursor = view.picture();
        view.openMenu(p, menuByController);
    }

    // ---------------------------------------------------------------- controller buttons
    private PixelMenu padMenu;
    /** Action waiting for its new button (-1 = none). */
    private int capture = -1;
    private final Runnable captureTimeout = new Runnable() { public void run() { cancelCapture(); } };

    private void showPad() {
        final PixelMenu m = new PixelMenu();
        m.title = "CONTROLLER BUTTONS";
        m.say("Choose an action, then press the button you want for it.");
        m.onClose = new Runnable() { public void run() { cancelCapture(); padMenu = null; } };
        padMenu = m;
        fillPad(m);
        view.openMenu(m, menuByController);
    }

    private void fillPad(final PixelMenu m) {
        m.items.clear();
        for (int i = 0; i < PadBinds.COUNT; i++) {
            final int a = i;
            m.add(PixelMenu.CHOICE, null, PadBinds.ACTION_NAMES[i], capture == i ? "PRESS A BUTTON..." : PadBinds.nameOf(i),
                    new Runnable() { public void run() { startCapture(a); } });
        }
        m.add(PixelMenu.INFO, null, "MOVE AND TURN", "DPAD + STICK", null).gap = true;
        m.add(PixelMenu.INFO, null, "SETTINGS, ALWAYS", "START", null);
        m.add(PixelMenu.BUTTON, null, "RESET TO DEFAULT", null, new Runnable() { public void run() {
            cancelCapture(); PadBinds.reset(); padSaved(); view.toast("Controller buttons reset."); } }).gap = true;
        m.add(PixelMenu.BUTTON, null, "DONE", null, new Runnable() { public void run() { view.closeMenu(); } });
        m.hintPad = capture >= 0 ? "START: CANCEL" : PadBinds.hint("BACK");
        m.hintTouch = capture >= 0 ? "WAITING FOR A BUTTON" : "";
    }

    private void startCapture(int action) {
        if (padMenu == null) return;
        capture = action;
        ui.removeCallbacks(captureTimeout); ui.postDelayed(captureTimeout, 5000);
        int c = padMenu.cursor; fillPad(padMenu); padMenu.cursor = c; view.menuChanged();
    }

    private void cancelCapture() {
        ui.removeCallbacks(captureTimeout);
        if (capture < 0) return;
        capture = -1;
        if (padMenu != null && view != null) { int c = padMenu.cursor; fillPad(padMenu); padMenu.cursor = c; view.menuChanged(); }
    }

    /** A button was pressed while an action waits for one. */
    private void captured(int code) {
        int action = capture;
        if (code == PadBinds.START) { cancelCapture(); return; }   // Start stays the settings
        ui.removeCallbacks(captureTimeout);
        int old = PadBinds.codes[action];
        int moved = PadBinds.bind(action, code);
        capture = -1;
        padSaved();
        if (moved >= 0)
            view.toast(PadBinds.name(code) + " was on " + PadBinds.ACTION_NAMES[moved] + ". " + PadBinds.ACTION_NAMES[moved] + " is now " + PadBinds.name(old) + ".");
    }

    /** Saves the buttons and shows them (this window, the button guide). */
    private void padSaved() {
        Prefs.setPadBinds(this, PadBinds.isDefault() ? "" : PadBinds.save());
        if (padMenu != null) { int c = padMenu.cursor; fillPad(padMenu); padMenu.cursor = c; }
        if (view != null) { view.relayout(); view.menuChanged(); }
    }

    // ---------------------------------------------------------------- stats
    private void showStats() {
        if (keys == null) return;
        keys.readStats(new AndroidHost.StatsCallback() { public void stats(brewemu.doomrpg.DoomGame.Stats s) {
            if (view == null) return;
            if (s == null) { view.toast("Stats are shown while you play."); return; }
            PixelMenu m = new PixelMenu();
            m.title = "STATS";
            m.add(PixelMenu.HEADING, null, "PLAYER", null, null);
            info(m, "HEALTH", s.health + "/" + s.maxHealth); info(m, "ARMOR", s.armor + "/" + s.maxArmor);
            info(m, "LEVEL", String.valueOf(s.level)); info(m, "XP", s.xp + "/" + s.nextXp); info(m, "CREDITS", String.valueOf(s.credits));
            info(m, "DEF STR AGI ACC", s.defense + " " + s.strength + " " + s.agility + " " + s.accuracy);
            m.add(PixelMenu.HEADING, null, "THIS SECTOR", null, null);
            info(m, "TIME", clock(s.sectorMs)); info(m, "MONSTERS", s.monsters + "/" + s.monstersTotal);
            info(m, "SECRETS", s.secrets + "/" + s.secretsTotal); info(m, "MOVES", String.valueOf(s.moves)); info(m, "XP GAINED", String.valueOf(s.xpGained));
            m.add(PixelMenu.HEADING, null, "OVERALL", null, null);
            info(m, "TIME", clock(s.totalMs)); info(m, "MOVES", String.valueOf(s.totalMoves)); info(m, "DEATHS", String.valueOf(s.deaths));
            m.add(PixelMenu.BUTTON, null, "BACK", null, new Runnable() { public void run() { view.closeMenu(); } }).gap = true;
            m.hintPad = PadBinds.nameOf(PadBinds.FIRE) + " OR " + PadBinds.nameOf(PadBinds.MENU) + ": BACK";
            view.openMenu(m, menuByController);
        } });
    }

    private static void info(PixelMenu m, String label, String value) { m.add(PixelMenu.INFO, null, label, value, null); }

    private static String clock(int ms) {
        int min = Math.max(0, ms) / 60000;
        return (min / 60) + ":" + (min % 60 < 10 ? "0" : "") + (min % 60);
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
        ask(large ? "LARGE SCREEN?" : "CLASSIC SCREEN?",
            (large ? "The game is told the phone has a 240 x 320 screen: the biggest view. It needs more power, so it can be slower on old phones."
                   : "The game is told the phone has a 176 x 208 screen: a smaller view that runs faster on old phones.")
            + "\n\nThe game closes to switch. Save first (Save now), then open Stout Marine again.",
            "SWITCH AND CLOSE", false, new Runnable() { public void run() { Prefs.setLarge(GameActivity.this, large); quitNow(); } });
    }

    private void confirmImport() {
        ask("IMPORT SAVES?", "This replaces the saved games on this phone with the ones in the .zip. The game closes afterwards; open Stout Marine again to continue.",
            "CHOOSE .ZIP", false, new Runnable() { public void run() { SaveTransfer.startImport(GameActivity.this); } });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            File saves = AndroidHost.saveDir(this);
            if (req == SaveTransfer.REQ_EXPORT) {
                int n = SaveTransfer.export(this, saves, data.getData());
                note(n == 0 ? "No saved games yet." : "Exported " + n + " save file" + (n == 1 ? "" : "s") + ".");
            } else if (req == SaveTransfer.REQ_IMPORT) {
                int n = SaveTransfer.importZip(this, saves, data.getData());
                note("Imported " + n + " save file" + (n == 1 ? "" : "s") + ". Open Stout Marine again to continue.");
                getWindow().getDecorView().postDelayed(new Runnable() { public void run() { quitNow(); } }, 1800);
            }
        } catch (Exception e) {
            note(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    /** A short pixel note at the top of the game (an Android toast if the game isn't showing). */
    private void note(String msg) { if (view != null) view.toast(msg); else SaveTransfer.toast(this, msg); }

    private void quitNow() {
        if (host != null) host.stopAllSounds();
        finishAndRemoveTask();
        getWindow().getDecorView().postDelayed(new Runnable() { public void run() {
            android.os.Process.killProcess(android.os.Process.myPid());
        } }, 300);
    }

    private void confirmQuit() {
        ask("QUIT STOUT MARINE?", "Anything you haven't saved will be lost. To keep your progress, cancel and use Save now first.",
            "QUIT", true, new Runnable() { public void run() { quitNow(); } });
    }

    private void confirmReload() {
        ask("NEW GAME FILE?", "Use this if the wrong file was picked or the game won't start. You'll choose the game's .zip again. The game closes; your saved games are kept.",
            "CHOOSE FILE", true, new Runnable() { public void run() {
                if (host != null) host.stopAllSounds();
                Installer.uninstall(SetupActivity.gameDir(GameActivity.this));
                startActivity(new Intent(GameActivity.this, SetupActivity.class));
                finish();
                // the game lives in static state; restart the process cleanly
                android.os.Process.killProcess(android.os.Process.myPid());
            } });
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
        if (PadBinds.isPadButton(code)) return PadBinds.key(code);   // controller buttons: the player's choice
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_W: return GameView.K_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_S: return GameView.K_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return GameView.K_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return GameView.K_RIGHT;
            case KeyEvent.KEYCODE_A: return GameView.K_STRAFE_L;
            case KeyEvent.KEYCODE_D: return GameView.K_STRAFE_R;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_SPACE: return GameView.K_FIRE;
            case KeyEvent.KEYCODE_ESCAPE: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_MENU: return GameView.K_MENU;
            case KeyEvent.KEYCODE_TAB: case KeyEvent.KEYCODE_M: return GameView.K_MAP;
            case KeyEvent.KEYCODE_K: return GameView.K_KEYPAD;
            case KeyEvent.KEYCODE_Q: return GameView.K_WPN_PREV;
            case KeyEvent.KEYCODE_E: return GameView.K_WPN_NEXT;
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
            showSettings(true);
        }
    };

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (capture >= 0) {
            if (PadBinds.isPadButton(code)) { if (e.getRepeatCount() == 0) captured(code); pickerKeys.add(code); return true; }
            cancelCapture();   // another key (d-pad, keyboard): stop waiting, then handle it as usual
        }
        int k = map(code);
        if (k == 0 || keys == null) return super.onKeyDown(code, e);
        if (code == KeyEvent.KEYCODE_BUTTON_L2 || code == KeyEvent.KEYCODE_BUTTON_R2) triggerButtons();
        if (fromController(e) && view != null) view.setHidden(true);
        if (k == GameView.K_KEYPAD) {
            if (e.getRepeatCount() == 0 && view != null) { releaseSticks(); view.toggleKeypad(); }
            return true;
        }
        if (k == GameView.K_SETTINGS) {
            if (e.getRepeatCount() == 0) { releaseSticks(); showSettings(true); }
            return true;
        }
        if (view != null && view.tourActive()) {
            pickerKeys.add(code);
            if (e.getRepeatCount() == 0 && (k == GameView.K_FIRE || k == GameView.K_MENU)) view.tourNext(k == GameView.K_MENU);
            return true;
        }
        if (view != null && view.menuOpen()) return menuKeyDown(code, k, e);
        if (view != null && view.pickerOpen()) return pickerDown(code, k, e);
        if ((k == GameView.K_WPN_PREV || k == GameView.K_WPN_NEXT) && PadBinds.isPadButton(code) && view != null) {
            // tap: previous / next weapon; hold: the weapon picker
            if (e.getRepeatCount() == 0) { trigHoldKey = k; trigHoldFired = false; ui.removeCallbacks(trigHold); ui.postDelayed(trigHold, 450); }
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
        if (pickerKeys.remove(code)) return true;
        if (view != null && (view.pickerOpen() || view.menuOpen() || view.tourActive())) return true;
        if ((k == GameView.K_WPN_PREV || k == GameView.K_WPN_NEXT) && PadBinds.isPadButton(code)) {
            ui.removeCallbacks(trigHold);
            if (!trigHoldFired && trigHoldKey == k) keys.tap(k);
            trigHoldKey = 0;
            return true;
        }
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

    // ---- the weapon picker with a controller: d-pad / stick move, A takes the weapon, B (or L2 / R2) closes.
    // Keys pressed while it is open are kept here so their release does nothing in the game.
    private final java.util.HashSet<Integer> pickerKeys = new java.util.HashSet<Integer>();
    private int trigHoldKey;
    private boolean trigHoldFired;
    /** L2 / R2 (button or analog) held: open the picker instead of changing weapon. */
    private final Runnable trigHold = new Runnable() {
        public void run() {
            trigHoldFired = true;
            if (view == null) return;
            releaseStickKeys();
            view.setPicker(true, true);
            if (view.pickerOpen() && Prefs.vibrate(GameActivity.this)) view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }
    };

    /** A pixel window is open: d-pad / stick move, left / right change a value, A chooses, B goes back. */
    private boolean menuKeyDown(int code, int k, KeyEvent e) {
        pickerKeys.add(code);
        boolean dir = k == GameView.K_UP || k == GameView.K_DOWN || k == GameView.K_LEFT || k == GameView.K_RIGHT;
        if (e.getRepeatCount() > 0 && !dir) return true;
        switch (k) {
            case GameView.K_UP: view.menuMove(-1); break;
            case GameView.K_DOWN: view.menuMove(1); break;
            case GameView.K_LEFT: view.menuSide(-1); break;
            case GameView.K_RIGHT: view.menuSide(1); break;
            case GameView.K_FIRE: view.menuActivate(); break;
            case GameView.K_MENU: view.closeMenu(); break;
            default: break;
        }
        return true;
    }

    private boolean pickerDown(int code, int k, KeyEvent e) {
        pickerKeys.add(code);
        if (e.getRepeatCount() > 0 && k != GameView.K_UP && k != GameView.K_DOWN && k != GameView.K_LEFT && k != GameView.K_RIGHT) return true;
        switch (k) {
            case GameView.K_UP: view.pickerMove(0, -1); break;
            case GameView.K_DOWN: view.pickerMove(0, 1); break;
            case GameView.K_LEFT: view.pickerMove(-1, 0); break;
            case GameView.K_RIGHT: view.pickerMove(1, 0); break;
            case GameView.K_FIRE: view.pickerChoose(); break;
            case GameView.K_MENU: case GameView.K_WPN_PREV: case GameView.K_WPN_NEXT: view.setPicker(false, false); break;
            default: break;
        }
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
        boolean menu = view != null && (view.menuOpen() || view.tourActive());
        boolean pick = view != null && view.pickerOpen();
        boolean big = view != null && (view.bigKeypad() || pick || menu);
        if (!big) triggers(e);
        int nx = x <= -0.5f ? GameView.K_LEFT : x >= 0.5f ? GameView.K_RIGHT : 0;
        int ny = y <= -0.5f ? GameView.K_UP : y >= 0.5f ? GameView.K_DOWN : 0;
        if (nx != stickX || ny != stickY) {
            if (view != null) view.setHidden(true);
            if (stickX != 0 && stickX != nx) keys.release(stickX);
            if (stickY != 0 && stickY != ny) keys.release(stickY);
            if (menu) {
                if (view.menuOpen()) {
                    if (ny != 0 && ny != stickY) view.menuMove(ny == GameView.K_UP ? -1 : 1);
                    else if (nx != 0 && nx != stickX) view.menuSide(nx == GameView.K_LEFT ? -1 : 1);
                }
            } else if (pick) {
                if (ny != 0 && ny != stickY) view.pickerMove(0, ny == GameView.K_UP ? -1 : 1);
                else if (nx != 0 && nx != stickX) view.pickerMove(nx == GameView.K_LEFT ? -1 : 1, 0);
            } else if (big) {
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
            if (view != null && view.tourActive()) { /* nothing */ }
            else if (view != null && view.menuOpen()) {
                if (stickY != 0) view.menuMove(stickY == GameView.K_UP ? -1 : 1);
            }
            else if (view != null && view.pickerOpen()) {
                if (stickY != 0) view.pickerMove(0, stickY == GameView.K_UP ? -1 : 1);
                else view.pickerMove(stickX == GameView.K_LEFT ? -1 : 1, 0);
            }
            else if (view != null && view.bigKeypad()) {
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
    private void triggerButtons() {
        if (!triggerKeys && trigHoldKey != 0 && (trigL || trigR)) { ui.removeCallbacks(trigHold); trigHoldKey = 0; }   // the axis had started it
        triggerKeys = true;
    }

    private void triggers(MotionEvent e) {
        float l = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
        float r = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
        boolean nl = trigL ? l > 0.3f : l > 0.6f, nr = trigR ? r > 0.3f : r > 0.6f;   // hysteresis
        if (!triggerKeys) {
            // pulled: hold opens the picker; let go before that: previous / next weapon
            if ((nl && !trigL) || (nr && !trigR)) {
                if (view != null) view.setHidden(true);
                trigHoldKey = nl && !trigL ? GameView.K_WPN_PREV : GameView.K_WPN_NEXT; trigHoldFired = false;
                ui.removeCallbacks(trigHold); ui.postDelayed(trigHold, 450);
            }
            if ((trigL && !nl && trigHoldKey == GameView.K_WPN_PREV) || (trigR && !nr && trigHoldKey == GameView.K_WPN_NEXT)) {
                ui.removeCallbacks(trigHold);
                if (!trigHoldFired && keys != null) keys.tap(trigHoldKey);
                trigHoldKey = 0;
            }
        }
        trigL = nl; trigR = nr;
    }

    private void releaseStickKeys() {
        ui.removeCallbacks(stickRepeat);
        if (keys != null) { if (stickX != 0) keys.release(stickX); if (stickY != 0) keys.release(stickY); }
        stickX = stickY = 0;
    }

    private void releaseSticks() {
        ui.removeCallbacks(trigHold); trigHoldKey = 0;
        trigL = trigR = false;
        ui.removeCallbacks(stickRepeat);
        if (keys != null) { if (stickX != 0) keys.release(stickX); if (stickY != 0) keys.release(stickY); }
        stickX = stickY = 0;
    }
}
