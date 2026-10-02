package brewemu.brew;

/** ISound: only vibration is used. */
final class Sound extends BObj {
    Sound(Brew b) { super(b, Brew.K_SOUND, 16); }

    @Override public int invoke(int slot) {
        switch (slot) {
            case 10: b.host.vibrate(arg(1) & 0xffff); return 0;   // Vibrate(ms)
            case 11: b.host.vibrate(0); return 0;                 // StopVibrate
            default: return super.invoke(slot);
        }
    }
}
