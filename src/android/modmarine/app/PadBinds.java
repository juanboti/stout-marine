package modmarine.app;

/**
 * Controller buttons: which gamepad button does what. The player can change them (Settings, Controller buttons);
 * the d-pad and sticks always move, and Start always opens the settings, so nobody can lock themselves out.
 * Pure Java (Android key codes are copied here as numbers) so it can be tested off the device.
 */
final class PadBinds {
    // Android KeyEvent codes of gamepad buttons
    static final int A = 96, B = 97, C = 98, X = 99, Y = 100, Z = 101, L1 = 102, R1 = 103, L2 = 104, R2 = 105,
            THUMBL = 106, THUMBR = 107, START = 108, SELECT = 109, MODE = 110;

    /** The actions that can be moved to another button. */
    static final int FIRE = 0, MENU = 1, WAIT = 2, MAP = 3, STEP_L = 4, STEP_R = 5, WPN_PREV = 6, WPN_NEXT = 7, KEYPAD = 8, COUNT = 9;
    static final String[] ACTION_NAMES = {"FIRE / USE", "GAME MENU", "WAIT", "MAP", "STEP LEFT", "STEP RIGHT",
            "PREVIOUS WEAPON", "NEXT WEAPON", "KEYPAD"};
    static final int[] DEFAULTS = {A, B, X, Y, L1, R1, L2, R2, SELECT};
    /** The game controls each action stands for (GameView key codes). */
    static final int[] KEYS = {GameView.K_FIRE, GameView.K_MENU, GameView.K_WAIT, GameView.K_MAP, GameView.K_STRAFE_L,
            GameView.K_STRAFE_R, GameView.K_WPN_PREV, GameView.K_WPN_NEXT, GameView.K_KEYPAD};

    /** The button for each action (Android key codes). */
    static final int[] codes = DEFAULTS.clone();

    /** A gamepad button (Android's own list; the d-pad is not one of them). */
    static boolean isPadButton(int code) {
        return (code >= 96 && code <= 110) || (code >= 188 && code <= 203);   // A..MODE, BUTTON_1..BUTTON_16
    }

    /** The action on this button, or -1. */
    static int actionOf(int code) {
        for (int i = 0; i < COUNT; i++) if (codes[i] == code) return i;
        return -1;
    }

    /** Game control for a gamepad button: Start is always the settings; 0 = this button does nothing. */
    static int key(int code) {
        if (code == START) return GameView.K_SETTINGS;
        int a = actionOf(code);
        return a < 0 ? 0 : KEYS[a];
    }

    /**
     * Puts action on button code. If another action had that button, the two swap buttons (so every action keeps one).
     * Returns the action that moved, or -1. Start can't be used (it is always the settings).
     */
    static int bind(int action, int code) {
        if (action < 0 || action >= COUNT || code == START || !isPadButton(code)) return -1;
        int other = actionOf(code), old = codes[action];
        codes[action] = code;
        if (other >= 0 && other != action) { codes[other] = old; return other; }
        return -1;
    }

    static void reset() { System.arraycopy(DEFAULTS, 0, codes, 0, COUNT); }

    static boolean isDefault() { for (int i = 0; i < COUNT; i++) if (codes[i] != DEFAULTS[i]) return false; return true; }

    /** Short name of a button, as printed on controllers. */
    static String name(int code) {
        switch (code) {
            case A: return "A"; case B: return "B"; case C: return "C"; case X: return "X"; case Y: return "Y"; case Z: return "Z";
            case L1: return "L1"; case R1: return "R1"; case L2: return "L2"; case R2: return "R2";
            case THUMBL: return "L3"; case THUMBR: return "R3"; case START: return "START"; case SELECT: return "SELECT";
            case MODE: return "HOME";
        }
        if (code >= 188 && code <= 203) return "B" + (code - 187);
        return "?";
    }

    static String nameOf(int action) { return name(codes[action]); }

    /** Saved form: the codes, comma-separated. */
    static String save() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < COUNT; i++) { if (i > 0) s.append(','); s.append(codes[i]); }
        return s.toString();
    }

    /** Loads a saved form; anything wrong (or a repeated button) and the defaults are used. */
    static void load(String s) {
        reset();
        if (s == null || s.isEmpty()) return;
        String[] p = s.split(",");
        if (p.length != COUNT) return;
        int[] c = new int[COUNT];
        try { for (int i = 0; i < COUNT; i++) c[i] = Integer.parseInt(p[i].trim()); } catch (NumberFormatException e) { return; }
        for (int i = 0; i < COUNT; i++) {
            if (!isPadButton(c[i]) || c[i] == START) return;
            for (int j = 0; j < i; j++) if (c[j] == c[i]) return;
        }
        System.arraycopy(c, 0, codes, 0, COUNT);
    }

    /** The button guide for the handheld panel: button, action. */
    static String[][] legend() {
        return new String[][]{{nameOf(FIRE), "FIRE"}, {nameOf(MENU), "MENU"}, {"START", "SETUP"}, {nameOf(WAIT), "WAIT"},
                {nameOf(MAP), "MAP"}, {nameOf(STEP_L) + " " + nameOf(STEP_R), "STEP"}, {nameOf(WPN_PREV) + " " + nameOf(WPN_NEXT), "WEAPON"},
                {nameOf(KEYPAD), "KEYPAD"}, {"DPAD", "MOVE"}};
    }

    /** "DPAD + A     B: CLOSE" with the player's buttons. */
    static String hint(String back) { return "DPAD + " + nameOf(FIRE) + "     " + nameOf(MENU) + ": " + back; }
}
