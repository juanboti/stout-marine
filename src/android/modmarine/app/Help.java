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

/** "How to play" screen with pictures: shown on first launch and from the settings (gear button). */
final class Help {
    // Each entry is either "img:<drawable name>" or HTML text.
    private static final String[] PAGE = {
        "<h4>Your controls</h4>",
        "img:help_controls",
        "<b>1 Menu</b> &mdash; the game's menu, or go back inside menus.<br>"
        + "<b>2 Gear</b> &mdash; Stout Marine's settings and this help (holding the menu button works too).<br>"
        + "<b>3 Weapon slot</b> &mdash; left arrow: previous weapon &middot; right arrow: next weapon "
        + "(the small pictures show which) &middot; the weapon in the middle: the <b>weapon picker</b> (more below).<br>"
        + "<b>4 Keypad</b> &mdash; number keys for door codes (more below).<br>"
        + "<b>5 Map</b> &mdash; show or hide the map.<br>"
        + "<b>6 Cross</b> &mdash; up: forward &middot; down: back &middot; left / right: turn. "
        + "Hold to keep going, or slide your thumb to another direction.<br>"
        + "<b>7 L / R</b> &mdash; side-step left / right. Hold to keep going.<br>"
        + "<b>8 Hourglass</b> &mdash; wait: skip a turn.<br>"
        + "<b>9 Red crosshair</b> &mdash; fire / use: attack, open doors, talk, pick things up, select in menus.<br>"
        + "<b>10 XP bar</b> &mdash; your level (in the red circle), the XP you have in this level, the XP the level needs, "
        + "and how much is left until the next level. The numbers are the game's own (read from the game, nothing is changed). "
        + "Turn it off in the settings if you like the original look.",

        "<h4>Turn or side-step?</h4>",
        "img:help_moves",
        "<b>Turn</b> keeps you on the same square and changes where you look. "
        + "<b>Side-step</b> moves you one square sideways and you keep looking ahead.",

        "<h4>Swipe and tap on the game picture</h4>",
        "img:help_gestures",
        "Swipe up / down to step forward / back, left / right to turn. Swipe and hold to keep going. "
        + "Tap to fire / use. Tap the words in the game's bottom bar (Menu / Back on the left, Map / Leave on the right) to press them.",

        "<h4>Weapon picker</h4>"
        + "Tap the weapon in the middle of the weapon slot (or hold <b>L2</b> or <b>R2</b> on a controller) to see all your "
        + "weapons with their ammo. Tap one, or choose it with the d-pad and <b>A</b>, to switch to it; tap outside or press "
        + "<b>B</b> to close. Weapons with no ammo left are grey, as the game won't switch to them. The weapon pictures come "
        + "from your own copy of the game.",

        "<h4>Door-code keypad</h4>",
        "img:help_keypad",
        "When the game asks for a door code, the number keys <b>open by themselves</b> and close again afterwards. "
        + "You can also open them yourself with the keypad button. "
        + "While the number keys are showing, the keypad button <b>glows orange</b> &mdash; in portrait and in landscape, "
        + "even if you turn the phone. To get your controls back, tap the glowing button or <b>X CLOSE</b>.",

        "<h4>Settings (the gear button)</h4>"
        + "Tap a row to use it; on rows with a value, tap again (or press left / right on a controller) to change it. "
        + "Tap outside the window, or press <b>B</b>, to close.<br>"
        + "<b>Save now</b> &mdash; saves your game in one tap. It uses the game's own menu and its <b>Save Game</b>, "
        + "exactly as if you had done it yourself, so it only works while you are walking around (not in a fight, "
        + "a conversation or a menu). A note at the top says when it's saved.<br>"
        + "<b>Stats</b> &mdash; your health, armor, level and XP, and for this sector and overall: time played, "
        + "monsters killed, secrets found and moves. The numbers are the same as the game's own Status screen.<br>"
        + "<b>Picture</b> &mdash; <i>Sharp pixels</i> (the original look), <i>Smooth</i> (soft and blended), "
        + "<i>Smart HQ</i> (smoother edges that keep the pixel look; the default) or <i>xBR</i> (rounder, more like a drawing). "
        + "Tap Picture for a small preview of each. Only the picture changes, not the game.<br>"
        + "<b>Mini-map</b> &mdash; <i>Off</i>, <i>Corner</i> (a small, see-through map in the top-right corner "
        + "while you play; the default) or <i>Big</i> (a larger one, more of the area). "
        + "It shows only what the game's own map shows: the places you have been, doors and the monsters there.<br>"
        + "<b>XP bar</b> &mdash; on (the default) or off.<br>"
        + "<b>Vibration</b> &mdash; <i>Off</i>, <i>Light</i> or <i>Strong</i> (the default), for the game's rumble and the button-tap buzz "
        + "in this app. Choosing a level gives a short sample buzz. "
        + "The game has its own switch too: <b>Vibrate</b> in the game's <b>Options</b> menu. "
        + "Stout Marine turns it on once when the game is first set up; after that your choice there is kept.<br>"
        + "<b>Screen</b> &mdash; <i>Large</i> tells the game it runs on a 240 x 320 phone and gives the biggest view "
        + "(the default). <i>Classic</i> tells it 176 x 208, the smaller size, which is faster on older phones.<br>"
        + "<b>Handheld</b> &mdash; <i>Auto</i> (the default), <i>Always</i> or <i>Off</i> (see below).<br>"
        + "<b>Export / Import saves</b> &mdash; save your progress to a .zip, or load it on another phone.<br>"
        + "<b>New game file</b> &mdash; pick the game's .zip again if the wrong file was chosen. Your saves are kept.<br>"
        + "<b>Quit</b> &mdash; closes the app. Save first (Save now, or the game's menu, then Save Game).",

        "<h4>Handheld mode (RG Rotate, AYN Thor and other gaming handhelds)</h4>",
        "img:help_handheld",
        "When a game controller is built in or connected, the touch controls make way for the game, shown as large "
        + "as possible with sharp, even pixels. Beside it: the gear (settings), menu, map, number "
        + "keypad and wait buttons, and a guide to the physical buttons. <b>Start</b> opens the settings too.<br>"
        + "Settings: <b>Handheld</b> &mdash; Auto (the default), Always or Off. In the settings, the d-pad moves, "
        + "left / right change a value, <b>A</b> chooses and <b>B</b> goes back.",

        "<h4>Door codes on a handheld</h4>",
        "img:help_bigkeypad",
        "When the game asks for a door code, big number keys open beside the game by themselves (and close afterwards). "
        + "<b>Select</b> (or the keypad button) opens them any time. "
        + "<b>D-pad</b>: choose a key &middot; <b>A</b>: press it &middot; <b>B</b> or <b>Select</b>: close. "
        + "You can also tap the keys. While they're open, the other buttons do nothing, so you can't walk or fire by accident.",

        "<h4>Controllers and keyboards</h4>"
        + "<b>D-pad / arrow keys / left stick</b>, <b>W / S</b> &mdash; move and turn.<br>"
        + "<b>L1 / R1</b>, <b>A / D</b> &mdash; side-step.<br>"
        + "<b>A button</b>, <b>Enter</b>, <b>Space</b> &mdash; fire / use.<br>"
        + "<b>B</b>, <b>Esc</b>, <b>Back</b> &mdash; menu (hold for these settings).<br>"
        + "<b>Start</b> &mdash; these settings.<br>"
        + "<b>Y</b>, <b>Tab</b>, <b>M</b> &mdash; map.<br>"
        + "<b>Select</b>, <b>K</b> &mdash; open / close the number keys.<br>"
        + "<b>L2 / R2</b>, <b>Q / E</b> &mdash; previous / next weapon. <b>Hold L2 or R2</b>: the weapon picker.<br>"
        + "<b>X button</b> &mdash; wait. Number keys type door codes. <b>Backspace</b> &mdash; the phone's CLR key.<br>"
        + "The on-screen controls hide while you use a controller and come back when you touch the screen.<br>"
        + "These are the default buttons. To change them: settings, <b>Controller buttons</b>. Choose an action, then press "
        + "the button you want for it (if another action had that button, the two swap). <b>Start</b> always opens the settings "
        + "and the d-pad and stick always move, so you can't lock yourself out. <b>Reset to default</b> puts them back.",

        "<h4>Move the touch controls</h4>"
        + "Settings, <b>Edit touch controls</b>: drag a group of buttons (the cross, the fire button, the L / R buttons, "
        + "the top row) to where your thumbs like them. Tap the cross or the fire button, then change its size (80 to 140%). "
        + "<b>Swap left / right</b> mirrors everything, for left-handed play. Portrait and landscape are kept separately. "
        + "<b>Reset to default</b> puts everything back. Buttons can't overlap or go onto the game picture.",
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
