package modmarine.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Html;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** "How to play" screen with pictures: shown on first launch and from the hold-gear settings. */
final class Help {
    // Each entry is either "img:<drawable name>" or HTML text.
    private static final String[] PAGE = {
        "<h4>Your controls</h4>",
        "img:help_controls",
        "<b>1 Gear</b> &mdash; tap: game menu, or go back inside menus. <b>Hold</b>: settings and this help.<br>"
        + "<b>2 Weapon slot</b> &mdash; left arrow: previous weapon &middot; right arrow: next weapon.<br>"
        + "<b>3 Keypad</b> &mdash; number keys for door codes (more below).<br>"
        + "<b>4 Map</b> &mdash; show or hide the map.<br>"
        + "<b>5 Cross</b> &mdash; up: forward &middot; down: back &middot; left / right: turn. "
        + "Hold to keep going, or slide your thumb to another direction.<br>"
        + "<b>6 L / R</b> &mdash; side-step left / right. Hold to keep going.<br>"
        + "<b>7 Hourglass</b> &mdash; wait: skip a turn.<br>"
        + "<b>8 Red crosshair</b> &mdash; fire / use: attack, open doors, talk, pick things up, select in menus.",

        "<h4>Turn or side-step?</h4>",
        "img:help_moves",
        "<b>Turn</b> keeps you on the same square and changes where you look. "
        + "<b>Side-step</b> moves you one square sideways and you keep looking ahead.",

        "<h4>Swipe and tap on the game picture</h4>",
        "img:help_gestures",
        "Swipe up / down to step forward / back, left / right to turn. Swipe and hold to keep going. "
        + "Tap to fire / use. Tap the words in the game's bottom bar (Menu / Back on the left, Map / Leave on the right) to press them.",

        "<h4>Door-code keypad</h4>",
        "img:help_keypad",
        "While the number keys are showing, the keypad button <b>glows orange</b> &mdash; in portrait and in landscape, "
        + "even if you turn the phone. To get your controls back, tap the glowing button or <b>X CLOSE</b>.",

        "<h4>Settings (hold the gear)</h4>"
        + "<b>Picture</b> &mdash; <i>Sharp pixels</i> (the original look, the default), <i>Smooth</i> (soft and blended), "
        + "<i>Smart upscale (hq)</i> (smoother edges that keep the pixel look) or <i>xBR upscale</i> (rounder, more like a drawing). "
        + "Only the picture changes, not the game.<br>"
        + "<b>Vibration</b> &mdash; turns the game's rumble and the button-tap buzz on or off for this app only. "
        + "The game has its own switch too: <b>Vibrate</b> in the game's <b>Options</b> menu. "
        + "Stout Marine turns it on once when the game is first set up; after that your choice there is kept.<br>"
        + "<b>Screen size</b> &mdash; <i>large</i> tells the game it runs on a 240 x 320 phone and gives the biggest view "
        + "(the default). <i>Classic</i> tells it 176 x 208, the smaller size, which is faster on older phones.<br>"
        + "<b>Export / Import saved games</b> &mdash; save your progress to a .zip, or load it on another phone.<br>"
        + "<b>Quit Stout Marine</b> &mdash; closes the app. Save in the game first (gear, then Save Game).<br>"
        + "<b>Reinstall game file</b> &mdash; pick the game's .zip again if the wrong file was chosen. Your saves are kept.",

        "<h4>Handheld mode (RG Rotate, AYN Thor and other gaming handhelds)</h4>",
        "img:help_handheld",
        "When a game controller is built in or connected, the touch controls make way for the game, shown as large "
        + "as possible with sharp, even pixels. Beside it: the gear (tap: game menu, hold: settings), map, number keypad "
        + "and wait buttons, and a guide to the physical buttons.<br>"
        + "Settings: <b>Handheld mode</b> &mdash; automatic (the default), always or off.",

        "<h4>Door codes on a handheld</h4>",
        "img:help_bigkeypad",
        "Press <b>Select</b> (or tap the keypad button) to open big number keys beside the game. "
        + "<b>D-pad</b>: choose a key &middot; <b>A</b>: press it &middot; <b>B</b> or <b>Select</b>: close. "
        + "You can also tap the keys. While they're open, the other buttons do nothing, so you can't walk or fire by accident.",

        "<h4>Controllers and keyboards</h4>"
        + "<b>D-pad / arrow keys / left stick</b>, <b>W / S</b> &mdash; move and turn.<br>"
        + "<b>L1 / R1</b>, <b>A / D</b> &mdash; side-step.<br>"
        + "<b>A button</b>, <b>Enter</b>, <b>Space</b> &mdash; fire / use.<br>"
        + "<b>B</b>, <b>Start</b>, <b>Esc</b>, <b>Back</b> &mdash; menu (hold for these settings).<br>"
        + "<b>Y</b>, <b>Tab</b>, <b>M</b> &mdash; map.<br>"
        + "<b>Select</b>, <b>K</b> &mdash; open / close the number keys.<br>"
        + "<b>L2 / R2</b>, <b>Q / E</b> &mdash; previous / next weapon.<br>"
        + "<b>X button</b> &mdash; wait. Number keys type door codes. <b>Backspace</b> &mdash; the phone's CLR key.<br>"
        + "The on-screen controls hide while you use a controller and come back when you touch the screen.",
    };

    @SuppressWarnings("deprecation")
    static void show(Activity a, final Runnable onClose) {
        float dp = a.getResources().getDisplayMetrics().density;
        int p = (int) (20 * dp);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(p, (int) (4 * dp), p, p);
        for (int i = 0; i < PAGE.length; i++) {
            String s = PAGE[i];
            if (s.startsWith("img:")) {
                int id = a.getResources().getIdentifier(s.substring(4), "drawable", a.getPackageName());
                if (id == 0) continue;
                ImageView iv = new ImageView(a);
                iv.setImageResource(id);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = (int) (6 * dp); lp.bottomMargin = (int) (8 * dp);
                col.addView(iv, lp);
            } else {
                TextView tv = new TextView(a);
                tv.setText(Html.fromHtml(s));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                tv.setLineSpacing(0, 1.2f);
                col.addView(tv, new LinearLayout.LayoutParams(-1, -2));
            }
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        AlertDialog d = new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("How to play")
            .setView(sv)
            .setPositiveButton("Got it", null)
            .create();
        if (onClose != null) d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface di) { onClose.run(); }
        });
        d.show();
    }
}
