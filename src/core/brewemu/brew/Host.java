package brewemu.brew;

import java.io.File;

/** What the emulator needs from the platform (Android app or desktop harness). */
public interface Host {
    /** A finished frame: 0xAARRGGBB pixels, w*h. Called on the emulator thread; copy if kept. */
    void present(int[] argb, int w, int h);
    /** Directory for the game's own files (saves, settings). */
    File dataDir();
    /** Phone vibration request (milliseconds; 0 = stop). */
    void vibrate(int ms);
    /** Plays 16-bit mono PCM; volume 0..100. Returns a handle (>= 0) or -1. */
    int playPcm(short[] pcm, int rate, int volume);
    /** Plays a Standard MIDI File; volume 0..100. Returns a handle (>= 0) or -1. */
    int playMidi(byte[] smf, int volume);
    void stopSound(int handle);
    /** Changes the volume (0..100) of a clip that is already playing. */
    void setVolume(int handle, int volume);
    /** True while the clip is still playing. */
    boolean soundPlaying(int handle);
    void log(String s);
    /** Applet asked to close itself. */
    void closeApplet();
}
