package modmarine.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** "About Stout Marine": credits, the not-affiliated note and the open source licences. */
final class About {
    private About() {}

    /** The installed version name, e.g. "1.6". */
    static String version(Activity a) {
        try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; }
        catch (Throwable t) { return "?"; }
    }

    @SuppressWarnings("deprecation")
    static int versionCode(Activity a) {
        try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionCode; }
        catch (Throwable t) { return 0; }
    }

    /** Shown once after an update (keep in step with CHANGES.md). */
    static final String WHATS_NEW =
          "\u2022 New: when the game asks for a door code, the number keys open by themselves.\n\n"
        + "\u2022 New: a settings button. The gear now opens Stout Marine's settings with one tap (Start on a controller); "
        + "the game's menu has its own button with three bars. Holding the menu button still opens the settings.\n\n"
        + "\u2022 New: Mini-map. Tap the gear, then Mini-map: a small see-through map in the top-right corner "
        + "while you play. It shows what the game's own map shows. Off by default.\n\n"
        + "\u2022 From 1.8.1 and 1.8.2: fires and animated scenery move, and the game's Volume setting changes music "
        + "that is already playing, as on the original phone.";

    static void showWhatsNew(Activity a) {
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("What's new in " + version(a))
            .setView(textView(a, WHATS_NEW))
            .setPositiveButton("OK", null)
            .show();
    }

    static String text(Activity a) {
        return "Stout Marine \u2014 version " + version(a) + "\n\n"
            + "Made by Juan Aponte.\n\n"
            + "An unofficial fan project. You need your own copy of the game; none is included.\n\n"
            + "Not affiliated with the owners of Doom RPG, or with Qualcomm (BREW).\n\n"
            + "Open source: MIT licence (sound decoder and xBR picture filter: LGPL; hq picture filter: ISC).";
    }

    static void show(final Activity a) {
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("About Stout Marine")
            .setView(textView(a, text(a)))
            .setPositiveButton("OK", null)
            .setNeutralButton("Licences", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { showLicences(a); }
            })
            .show();
    }

    private static void showLicences(Activity a) {
        String s = asset(a, "NOTICE.txt") + "\n\n" + asset(a, "LICENSE-LGPL-2.1.txt");
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Licences")
            .setView(textView(a, s))
            .setPositiveButton("OK", null)
            .show();
    }

    private static ScrollView textView(Activity a, String s) {
        float dp = a.getResources().getDisplayMetrics().density;
        TextView tv = new TextView(a);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setLineSpacing(0, 1.15f);
        int p = (int) (22 * dp);
        tv.setPadding(p, (int) (12 * dp), p, (int) (8 * dp));
        ScrollView sv = new ScrollView(a);
        sv.addView(tv);
        return sv;
    }

    private static String asset(Activity a, String name) {
        try {
            InputStream in = a.getAssets().open(name);
            try {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
                return o.toString("UTF-8");
            } finally { in.close(); }
        } catch (Throwable t) { return ""; }
    }
}
