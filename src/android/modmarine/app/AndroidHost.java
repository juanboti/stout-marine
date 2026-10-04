package modmarine.app;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Vibrator;
import android.widget.Toast;

import brewemu.audio.Mixer;
import brewemu.brew.Brew;
import brewemu.brew.Host;
import brewemu.doomrpg.DoomMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;

/** Connects the emulator to the phone: picture, vibration, sound and storage. */
final class AndroidHost implements Host {
    final Context app;
    volatile Activity activity;
    volatile GameView view;
    private final Vibrator vib;
    private final File dataDir;
    private volatile boolean closing;

    AndroidHost(Activity a) {
        app = a.getApplicationContext();
        activity = a;
        vib = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
        dataDir = saveDir(app);
        dataDir.mkdirs();
    }

    /** Where the game keeps its own save files. */
    static File saveDir(Context c) { return new File(c.getFilesDir(), "saves"); }

    public void present(int[] argb, int w, int h) {
        GameView v = view;
        if (v == null) return;
        v.submitFrame(argb, w, h);
        Brew b = brew;
        if (b != null) {
            // the game asks for a door code: open the number keys by themselves (and close them afterwards)
            boolean pw = DoomMap.state(b) == DoomMap.ST_PASSWORD;
            if (pw != codePrompt) { codePrompt = pw; v.codePrompt(pw); }
        }
        if (miniMap) updateMiniMap(v, w);
        else if (mapShown) { mapShown = false; v.submitMap(null, 0); }
    }

    // ---------------------------------------------------------------- mini-map
    // Read from the game's memory right after each frame, on the emulator thread (so it matches the picture).
    volatile Brew brew;
    volatile boolean miniMap;
    private final DoomMap map = new DoomMap();
    private int[] mapPx = new int[0];
    private boolean mapShown;
    private boolean codePrompt;

    private void updateMiniMap(GameView v, int gameW) {
        Brew b = brew;
        if (b != null && map.read(b)) {
            int tiles = gameW >= 240 ? 15 : 13, cell = gameW >= 240 ? 5 : 4, size = tiles * cell;
            if (mapPx.length != size * size) mapPx = new int[size * size];
            map.drawAround(mapPx, size, cell, 0x99000000);
            v.submitMap(mapPx, size);
            mapShown = true;
        } else if (mapShown) {
            mapShown = false;
            v.submitMap(null, 0);
        }
    }

    public File dataDir() { return dataDir; }

    private java.lang.reflect.Method createOneShot, vibrateWithEffect;
    private android.media.AudioAttributes gameAttrs;

    /** Android 8+: one-shot vibration marked as game feedback (looked up at run time; built against API 23). */
    private boolean vibrateEffect(int ms) {
        try {
            if (createOneShot == null) {
                Class<?> ve = Class.forName("android.os.VibrationEffect");
                createOneShot = ve.getMethod("createOneShot", long.class, int.class);
                vibrateWithEffect = Vibrator.class.getMethod("vibrate", ve, android.media.AudioAttributes.class);
                gameAttrs = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_GAME)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            }
            Object effect = createOneShot.invoke(null, (long) ms, -1);   // -1 = VibrationEffect.DEFAULT_AMPLITUDE
            vibrateWithEffect.invoke(vib, effect, gameAttrs);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The game's rumble; follows this app's Vibration setting only (phone settings are never changed). */
    @SuppressWarnings("deprecation")
    public void vibrate(int ms) {
        if (vib == null) return;
        try {
            if (ms <= 0) { vib.cancel(); return; }
            if (!Prefs.vibrate(app) || !vib.hasVibrator()) return;
            if (android.os.Build.VERSION.SDK_INT >= 26 && vibrateEffect(ms)) return;
            vib.vibrate(ms);
        } catch (Throwable t) { /* no vibrator */ }
    }

    // ------------------------------------------------------------------ sound
    // Sampled sounds (effects) go through one software mixer and one streaming AudioTrack, so any number
    // can overlap with no per-sound setup cost. Songs (MIDI) play on Android's built-in synthesizer.
    private static final int MIX_RATE = 22050, CHUNK = 256;   // ~12 ms per chunk
    private static final int MIDI_BASE = 1 << 30;
    private final Mixer mixer = new Mixer(MIX_RATE);
    private Thread mixThread;
    private final HashMap<Integer, Song> songs = new HashMap<Integer, Song>();
    private int nextSong = MIDI_BASE;

    private static final class Song {
        final MediaPlayer mp;
        final long started = android.os.SystemClock.uptimeMillis();
        volatile boolean completed;
        Song(MediaPlayer m) { mp = m; }
    }

    public int playPcm(short[] pcm, int rate, int volume) {
        if (pcm.length == 0) return -1;
        startMixer();
        return mixer.play(pcm, rate, volume);
    }

    private synchronized void startMixer() {
        if (mixThread != null) return;
        mixThread = new Thread("sound") {
            public void run() {
                try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO); } catch (Throwable t) { /* keep default */ }
                try { mixLoop(); } catch (Throwable t) { android.util.Log.e("ModMarine", "sound output stopped", t); }
            }
        };
        mixThread.setDaemon(true);
        mixThread.start();
    }

    @SuppressWarnings("deprecation")
    private static AudioTrack openTrack(int bytes, boolean builder) {
        if (builder && android.os.Build.VERSION.SDK_INT >= 23) {
            return new AudioTrack.Builder()
                .setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_GAME)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(MIX_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        }
        return new AudioTrack(AudioManager.STREAM_MUSIC, MIX_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bytes, AudioTrack.MODE_STREAM);
    }

    /** A ready streaming track, or null if the phone refuses to give one. */
    private static AudioTrack openTrack() {
        int min = AudioTrack.getMinBufferSize(MIX_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bytes = Math.max(min > 0 ? min : 0, CHUNK * 2 * 4);
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                AudioTrack t = openTrack(bytes, attempt == 0);
                if (t.getState() == AudioTrack.STATE_INITIALIZED) return t;
                t.release();
            } catch (Throwable e) {
                android.util.Log.w("ModMarine", "AudioTrack attempt " + attempt + " failed", e);
            }
        }
        return null;
    }

    /** Pulls mixed audio and writes it to the speaker; the track pauses while nothing plays. */
    private void mixLoop() throws InterruptedException {
        AudioTrack track = openTrack();
        short[] buf = new short[CHUNK];
        if (track == null) {
            // no sound output available: keep consuming clips in real time so the game sees them finish
            android.util.Log.e("ModMarine", "no audio output for sound effects");
            while (true) {
                while (!mixer.awaitSound(1000)) { /* idle */ }
                mixer.mix(buf, CHUNK);
                Thread.sleep(CHUNK * 1000L / MIX_RATE);
            }
        }
        boolean running = false;
        int silent = 0;
        while (true) {
            if (mixer.idle()) {
                // keep feeding silence briefly so the end of a sound is not cut, then pause until the next one
                if (silent > MIX_RATE / 2 / CHUNK || !running) {
                    if (running) { track.pause(); track.flush(); running = false; }
                    while (!mixer.awaitSound(1000)) { /* idle */ }
                    silent = 0;
                } else silent++;
            } else silent = 0;
            if (!running) { track.play(); running = true; }
            mixer.mix(buf, CHUNK);
            int off = 0;
            while (off < CHUNK) {
                int n = track.write(buf, off, CHUNK - off);
                if (n <= 0) { Thread.sleep(CHUNK * 1000L / MIX_RATE); break; }   // output error: keep time, try again
                off += n;
            }
        }
    }

    public synchronized int playMidi(byte[] smf, int volume) {
        try {
            File f = new File(app.getCacheDir(), "song_" + Integer.toHexString(java.util.Arrays.hashCode(smf)) + "_" + smf.length + ".mid");
            if (!f.exists() || f.length() != smf.length) {
                FileOutputStream o = new FileOutputStream(f);
                try { o.write(smf); } finally { o.close(); }
            }
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            FileInputStream in = new FileInputStream(f);
            try { mp.setDataSource(in.getFD()); } finally { in.close(); }
            mp.prepare();
            float v = Math.max(0, Math.min(100, volume)) / 100f;
            mp.setVolume(v, v);
            final Song s = new Song(mp);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer m) { s.completed = true; }
            });
            mp.start();
            int h = nextSong++;
            if (nextSong < MIDI_BASE) nextSong = MIDI_BASE;
            songs.put(h, s);
            return h;
        } catch (Throwable e) {
            android.util.Log.w("ModMarine", "song failed", e);
            return -1;
        }
    }

    public synchronized void stopSound(int handle) {
        if (handle >= MIDI_BASE) { Song s = songs.remove(handle); if (s != null) release(s.mp); }
        else mixer.stop(handle);
    }

    public synchronized void setVolume(int handle, int volume) {
        float v = Math.max(0, Math.min(100, volume)) / 100f;
        if (handle < MIDI_BASE) { mixer.setVolume(handle, volume); return; }
        Song s = songs.get(handle);
        if (s != null) try { s.mp.setVolume(v, v); } catch (Throwable e) { /* already released */ }
    }

    public synchronized boolean soundPlaying(int handle) {
        if (handle < MIDI_BASE) return mixer.playing(handle);
        Song s = songs.get(handle);
        if (s == null) return false;
        boolean playing;
        try {
            // isPlaying() can still be false for a moment right after start()
            playing = !s.completed && (s.mp.isPlaying() || android.os.SystemClock.uptimeMillis() - s.started < 1500);
        } catch (Throwable e) { playing = false; }
        if (!playing) stopSound(handle);
        return playing;
    }

    synchronized void stopAllSounds() {
        mixer.stopAll();
        for (Song s : songs.values()) release(s.mp);
        songs.clear();
    }

    private static void release(MediaPlayer m) {
        if (m == null) return;
        try { m.stop(); } catch (Throwable x) { /* not started */ }
        try { m.release(); } catch (Throwable x) { /* ignore */ }
    }

    // ------------------------------------------------------------------ misc
    public void log(String s) { android.util.Log.i("ModMarine", s); }

    public void closeApplet() {
        if (closing) return;
        closing = true;
        stopAllSounds();
        final Activity a = activity;
        if (a != null) a.runOnUiThread(new Runnable() { public void run() { a.finishAndRemoveTask(); } });
        new Thread() { public void run() {
            try { Thread.sleep(400); } catch (InterruptedException e) { /* exit anyway */ }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } }.start();
    }

    void showMessage(final String msg) {
        final Activity a = activity;
        if (a == null) return;
        a.runOnUiThread(new Runnable() { public void run() { Toast.makeText(app, msg, Toast.LENGTH_LONG).show(); } });
    }
}
