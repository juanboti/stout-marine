package modmarine.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import brewemu.brew.Installer;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

/** Lets the user pick their own BREW game files and installs them (runs in its own process). */
public final class SetupActivity extends Activity {
    private static final int PICK = 1;
    private TextView status;
    private Button pick;
    private ProgressBar spinner;
    private boolean busy;

    static File gameDir(Activity a) { return new File(a.getFilesDir(), "game"); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setBackgroundColor(0xFF0B0B0F);
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        float dp = m.density;
        int sw = m.widthPixels, sh = m.heightPixels;
        boolean wide = sw > sh;                       // sideways phones, square-ish handhelds in landscape

        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(getResources().getIdentifier("logo", "drawable", getPackageName()));
        logo.setAdjustViewBounds(true);
        // never more than about a quarter of the screen height (side by side: about half), at most 160 dp
        int ls = (int) Math.min(160 * dp, wide ? Math.min(sh * 0.5f, sw * 0.3f) : sh * 0.25f);

        TextView title = new TextView(this);
        title.setText("Stout Marine");
        title.setTextColor(0xFFFF5A4E);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, sh / dp < 420 ? 28 : 34);
        title.setGravity(Gravity.CENTER);

        TextView first = text("This app doesn't include any game.", 16, 0xFFCFCFD6);
        TextView info = text("Choose your own Doom RPG BREW game: the .zip file that holds doomrpg.mod and doomrpg.bar "
                + "(or pick those two files together). They're copied once and stay on this phone.", 15, 0xFFB8B8C2);

        pick = new Button(this);
        pick.setText("Choose game file");
        pick.setAllCaps(false);
        pick.setTextColor(Color.WHITE);
        pick.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        pick.setBackground(pill(0xFFD3362B, 28 * dp, dp));
        pick.setPadding((int) (28 * dp), (int) (14 * dp), (int) (28 * dp), (int) (14 * dp));
        pick.setFocusable(true);
        pick.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { choose(); }
        });

        spinner = new ProgressBar(this);
        spinner.setVisibility(View.GONE);

        status = text("", 15, 0xFFFFB4AB);

        TextView about = text("About", 14, 0xFF9A9AA6);
        about.setPaintFlags(about.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        about.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (8 * dp));
        about.setBackground(pill(0x00000000, 16 * dp, dp));
        about.setFocusable(true);
        about.setClickable(true);
        about.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { About.show(SetupActivity.this); }
        });

        // the text column: title, first line, button, the longer explanation, progress, About
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(title, new LinearLayout.LayoutParams(-1, -2));
        col.addView(first, margins(-1, 0, (int) (10 * dp)));
        LinearLayout.LayoutParams bp = margins(-2, 0, (int) (20 * dp));
        bp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(pick, bp);
        col.addView(info, margins(-1, 0, (int) (20 * dp)));
        LinearLayout.LayoutParams sp = margins(-2, 0, (int) (16 * dp));
        sp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(spinner, sp);
        col.addView(status, margins(-1, 0, (int) (12 * dp)));
        LinearLayout.LayoutParams ap = margins(-2, 0, (int) (12 * dp));
        ap.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(about, ap);

        int pad = (int) ((sh / dp < 420 ? 16 : 28) * dp);
        LinearLayout page = new LinearLayout(this);
        page.setPadding(pad, pad, pad, pad);
        if (wide) {
            // side by side: logo on the left, everything else on the right
            page.setOrientation(LinearLayout.HORIZONTAL);
            page.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lgp = new LinearLayout.LayoutParams(ls, ls);
            lgp.rightMargin = (int) (24 * dp);
            page.addView(logo, lgp);
            page.addView(col, new LinearLayout.LayoutParams((int) Math.min(420 * dp, sw - ls - 2 * pad - 24 * dp), -2));
        } else {
            page.setOrientation(LinearLayout.VERTICAL);
            page.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lgp = new LinearLayout.LayoutParams(ls, ls);
            lgp.gravity = Gravity.CENTER_HORIZONTAL;
            lgp.bottomMargin = (int) (8 * dp);
            page.addView(logo, lgp);
            page.addView(col, new LinearLayout.LayoutParams((int) Math.min(520 * dp, sw - 2 * pad), -2));
        }

        // centred when everything fits; scrolls only when the screen is too short (small screens, large text)
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page, new android.widget.FrameLayout.LayoutParams(-1, -2));
        setContentView(scroll);

        // controllers: the button starts selected, so A (or Enter) opens the file picker
        pick.requestFocus();

        // opened via "Open with" / share on a .zip
        Uri data = getIntent().getData();
        if (data == null && Intent.ACTION_SEND.equals(getIntent().getAction()))
            data = (Uri) getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        if (data != null && b == null) {
            ArrayList<Uri> one = new ArrayList<Uri>();
            one.add(data);
            install(one);
        }
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private static LinearLayout.LayoutParams margins(int w, int h, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, -2);
        lp.topMargin = top;
        return lp;
    }

    /** Rounded background with a white ring while selected with a controller or keyboard. */
    private static android.graphics.drawable.Drawable pill(int color, float radius, float dp) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(color); normal.setCornerRadius(radius);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(color); focused.setCornerRadius(radius);
        focused.setStroke((int) Math.max(2, 3 * dp), Color.WHITE);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(color == 0 ? 0x33FFFFFF : 0xFFA82A21); pressed.setCornerRadius(radius);
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    /** A controller's A button presses whatever is selected (some phones don't treat A as "OK"). */
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (e.getKeyCode() == android.view.KeyEvent.KEYCODE_BUTTON_A) {
            if (e.getAction() == android.view.KeyEvent.ACTION_UP && e.getRepeatCount() == 0) {
                View f = getCurrentFocus();
                if (f == null) f = pick;                  // still in touch mode: nothing selected yet
                if (f != null && f.isEnabled()) f.performClick();
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void choose() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(i, PICK); }
        catch (Exception e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT);
            g.addCategory(Intent.CATEGORY_OPENABLE);
            g.setType("*/*");
            g.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(g, "Choose game file"), PICK);
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req != PICK || res != RESULT_OK || data == null) return;
        ArrayList<Uri> uris = new ArrayList<Uri>();
        ClipData clip = data.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        if (!uris.isEmpty()) install(uris);
    }

    private String displayName(Uri u) {
        Cursor c = null;
        try {
            c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (Exception e) {
            // fall back to the path
        } finally { if (c != null) c.close(); }
        String p = u.getLastPathSegment();
        return p != null ? p : "file";
    }

    private void install(final ArrayList<Uri> uris) {
        if (busy) return;
        busy = true;
        pick.setEnabled(false);
        spinner.setVisibility(View.VISIBLE);
        status.setTextColor(0xFFCFCFD6);
        status.setText("Copying...");
        final File dir = gameDir(this);
        new Thread("install") {
            public void run() {
                try {
                    Installer.Files files = new Installer.Files();
                    for (Uri u : uris) {
                        InputStream in = getContentResolver().openInputStream(u);
                        if (in == null) throw new Exception("Couldn't open that file.");
                        try { files.add(displayName(u), in); } finally { in.close(); }
                    }
                    Installer.install(files, dir);
                    show("Installed.", false);
                    runOnUiThread(new Runnable() { public void run() { launch(); } });
                } catch (final Throwable t) {
                    String m = t.getMessage() != null ? t.getMessage() : t.toString();
                    show(m, true);
                    runOnUiThread(new Runnable() { public void run() {
                        busy = false; pick.setEnabled(true); spinner.setVisibility(View.GONE);
                    } });
                }
            }
        }.start();
    }

    private void show(final String msg, final boolean error) {
        runOnUiThread(new Runnable() { public void run() {
            status.setText(msg);
            status.setTextColor(error ? 0xFFFFB4AB : 0xFFCFCFD6);
        } });
    }

    private void launch() {
        Intent i = new Intent(this, GameActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
        // this activity runs in its own process; end it so it never holds stale state
        getWindow().getDecorView().postDelayed(new Runnable() { public void run() {
            android.os.Process.killProcess(android.os.Process.myPid());
        } }, 1500);
    }
}
