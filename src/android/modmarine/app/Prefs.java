package modmarine.app;
import android.content.Context;
import android.content.SharedPreferences;
final class Prefs {
    static SharedPreferences p(Context c) { return c.getSharedPreferences("modmarine", Context.MODE_PRIVATE); }
    /** Vibration: 0 off, 1 light, 2 strong (before 2.0: on / off). */
    static final int VIB_OFF = 0, VIB_LIGHT = 1, VIB_STRONG = 2;
    static int vibration(Context c) {
        SharedPreferences s = p(c);
        if (s.contains("vibration")) return s.getInt("vibration", VIB_STRONG);
        return s.getBoolean("vibrate", true) ? VIB_STRONG : VIB_OFF;
    }
    static void setVibration(Context c, int v) { p(c).edit().putInt("vibration", v).apply(); }
    static boolean vibrate(Context c) { return vibration(c) != VIB_OFF; }
    /** Controller buttons chosen by the player (PadBinds.save form; empty = the defaults). */
    static String padBinds(Context c) { return p(c).getString("pad_binds", ""); }
    static void setPadBinds(Context c, String s) { p(c).edit().putString("pad_binds", s).apply(); }
    /** The player's touch layout changes (Deck.saveEdits form), portrait and landscape. */
    static String touchEdits(Context c, boolean portrait) { return p(c).getString(portrait ? "touch_p" : "touch_l", ""); }
    static void setTouchEdits(Context c, boolean portrait, String s) { p(c).edit().putString(portrait ? "touch_p" : "touch_l", s).apply(); }
    /** XP bar under the game picture (on by default). */
    static boolean xpBar(Context c) { return p(c).getBoolean("xpbar", true); }
    static void setXpBar(Context c, boolean on) { p(c).edit().putBoolean("xpbar", on).apply(); }
    /** The "extras" window (what's on, and where to change it) has been shown. */
    static boolean extrasSeen(Context c) { return p(c).getBoolean("extras_v1", false); }
    static void setExtrasSeen(Context c) { p(c).edit().putBoolean("extras_v1", true).apply(); }
    /** The first-run tour of the touch controls has been shown. */
    static boolean tourSeen(Context c) { return p(c).getBoolean("tour_v1", false); }
    static void setTourSeen(Context c) { p(c).edit().putBoolean("tour_v1", true).apply(); }
    static boolean shownHelp(Context c) { return p(c).getBoolean("help_v4", false); }
    static void setShownHelp(Context c) { p(c).edit().putBoolean("help_v4", true).apply(); }
    /** versionCode whose "What's new" has been seen (0 = before 1.6). */
    static int seenVersion(Context c) { return p(c).getInt("seen_version", 0); }
    static void setSeenVersion(Context c, int v) { p(c).edit().putInt("seen_version", v).apply(); }
    /** Picture: 0 sharp pixels, 1 smooth, 2 hq upscale (the default from 2.1), 3 xBR upscale. Before 1.8 only "smooth" on / off existed. */
    static final int PICTURE_SHARP = 0, PICTURE_SMOOTH = 1, PICTURE_HQ = Upscaler.HQ, PICTURE_XBR = Upscaler.XBR;
    static int picture(Context c) {
        SharedPreferences s = p(c);
        if (s.contains("picture")) return s.getInt("picture", PICTURE_HQ);
        if (s.contains("smooth")) return s.getBoolean("smooth", false) ? PICTURE_SMOOTH : PICTURE_SHARP;
        return PICTURE_HQ;
    }
    static void setPicture(Context c, int v) { p(c).edit().putInt("picture", v).apply(); }
    /** Phone screen size the game is told about: true = 240x320 (larger view, the default), false = 176x208 (classic). */
    static boolean large(Context c) { return p(c).getBoolean("large", true); }
    static void setLarge(Context c, boolean b) { p(c).edit().putBoolean("large", b).commit(); }
    /** Mini-map in the corner of the game picture (the corner one is the default from 2.1). */
    static final int MAP_OFF = 0, MAP_CORNER = 1, MAP_BIG = 2;
    /** Mini-map: off, corner or big (before 2.0: on / off). */
    static int miniMap(Context c) {
        SharedPreferences s = p(c);
        if (s.contains("minimap_mode")) return s.getInt("minimap_mode", MAP_CORNER);
        if (s.contains("minimap")) return s.getBoolean("minimap", false) ? MAP_CORNER : MAP_OFF;
        return MAP_CORNER;
    }
    static void setMiniMap(Context c, int m) { p(c).edit().putInt("minimap_mode", m).apply(); }
    /** Handheld mode: 0 = automatic (on while a game controller is present), 1 = always, 2 = never. */
    static final int HANDHELD_AUTO = 0, HANDHELD_ON = 1, HANDHELD_OFF = 2;
    static int handheld(Context c) { return p(c).getInt("handheld", HANDHELD_AUTO); }
    static void setHandheld(Context c, int m) { p(c).edit().putInt("handheld", m).apply(); }
}
