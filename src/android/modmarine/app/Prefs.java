package modmarine.app;
import android.content.Context;
import android.content.SharedPreferences;
final class Prefs {
    static SharedPreferences p(Context c) { return c.getSharedPreferences("modmarine", Context.MODE_PRIVATE); }
    static boolean vibrate(Context c) { return p(c).getBoolean("vibrate", true); }
    static boolean shownHelp(Context c) { return p(c).getBoolean("help_v4", false); }
    static void setShownHelp(Context c) { p(c).edit().putBoolean("help_v4", true).apply(); }
    /** versionCode whose "What's new" has been seen (0 = before 1.6). */
    static int seenVersion(Context c) { return p(c).getInt("seen_version", 0); }
    static void setSeenVersion(Context c, int v) { p(c).edit().putInt("seen_version", v).apply(); }
    /** Picture: 0 sharp pixels (default), 1 smooth, 2 hq upscale, 3 xBR upscale. Before 1.8 only "smooth" on / off existed. */
    static final int PICTURE_SHARP = 0, PICTURE_SMOOTH = 1, PICTURE_HQ = Upscaler.HQ, PICTURE_XBR = Upscaler.XBR;
    static int picture(Context c) {
        SharedPreferences s = p(c);
        if (s.contains("picture")) return s.getInt("picture", PICTURE_SHARP);
        return s.getBoolean("smooth", false) ? PICTURE_SMOOTH : PICTURE_SHARP;
    }
    static void setPicture(Context c, int v) { p(c).edit().putInt("picture", v).apply(); }
    /** Phone screen size the game is told about: true = 240x320 (larger view, the default), false = 176x208 (classic). */
    static boolean large(Context c) { return p(c).getBoolean("large", true); }
    static void setLarge(Context c, boolean b) { p(c).edit().putBoolean("large", b).commit(); }
    /** Mini-map in the corner of the game picture (off by default: the original look). */
    static boolean miniMap(Context c) { return p(c).getBoolean("minimap", false); }
    static void setMiniMap(Context c, boolean b) { p(c).edit().putBoolean("minimap", b).apply(); }
    /** Handheld mode: 0 = automatic (on while a game controller is present), 1 = always, 2 = never. */
    static final int HANDHELD_AUTO = 0, HANDHELD_ON = 1, HANDHELD_OFF = 2;
    static int handheld(Context c) { return p(c).getInt("handheld", HANDHELD_AUTO); }
    static void setHandheld(Context c, int m) { p(c).edit().putInt("handheld", m).apply(); }
}
