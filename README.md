# Stout Marine

Made by Juan Aponte. An unofficial fan project, not affiliated with the owners of Doom RPG or with
Qualcomm (BREW). (Formerly called MOD Marine.)

An Android player for the BREW version of Doom RPG, with pixel-art touch controls.

Stout Marine is a small BREW phone emulator. It runs the game's original, unmodified `.mod` program
on an emulated ARM processor and provides the BREW system functions the game calls (screen, keys,
timers, files, sound, vibration). Nothing in the game is changed.

**No game files are included.** On first launch the player picks their own copy of the game:
the `.zip` that holds `doomrpg.mod` and `doomrpg.bar` (or those two files picked together).
They are copied into the app's private storage and never leave the phone.

## Installing

1. Download `StoutMarine-1.8.apk` from the release page and open it on your Android phone or handheld
   (Android 5.0 or newer). Android will ask you to allow installing apps from that source.
2. Open Stout Marine and tap **Choose game file**: pick the `.zip` of your own copy of the BREW
   version of Doom RPG (it must contain `doomrpg.mod` and `doomrpg.bar`).
3. Play. Hold the gear (or the B button on a controller) for settings and help.

Updates install over the previous version and keep your saved games. Your saved games can also be
exported to a `.zip` and imported again (settings, Export / Import saved games).

## Controls

A cross like a handheld's d-pad (up / down walk, left / right turn) with separate L and R side-step buttons:

| Control | BREW key |
| --- | --- |
| Cross: up / down (forward / back) | Up / Down |
| Cross: left / right (turn) | Left / Right |
| L / R (side-step left / right) | 1 / 3 |
| Weapon slot, left and right arrows | * / 7 |
| Hourglass (wait) | 9 |
| Red crosshair (fire / use) | Select |
| Gear (tap) | Left soft key (Menu / Back) |
| Map | Right soft key (Map / Leave) |
| Keypad | 0-9, *, # |

You can also swipe on the game picture (up/down to step, left/right to turn) and tap it to fire.
Tap the words in the game's bottom bar to press the soft keys. Hold the gear for settings and help.
Gamepads and keyboards work too (Backspace is the phone's CLR key).

Settings: help, about (credits and licences), picture (sharp pixels, smooth, smart upscale (hq) or
xBR upscale), vibration (this app only), screen size
(large 240 x 320, the default, or classic 176 x 208), handheld mode, export / import saved games, quit,
and reinstall the game file. "What's new" is shown once after an update, and leaving the game with
Home or Recents shows a short reminder to save first (Android may close apps waiting in the background).
See `CHANGES.md` for the version history.

## Handheld mode (Android handhelds such as the Anbernic RG Rotate or the AYN Thor)

When a built-in or attached game controller is found on a landscape or square screen, the touch deck
is replaced by the game picture at the largest whole-number scale (sharp pixels), with small side
panels: gear, map, keypad and wait buttons on the left, and a button guide on the right (or the
keypad, when opened). The setting "Handheld mode" can be Auto (the default), On or Off.

| Button | Action |
| --- | --- |
| A | Fire / use (Select) |
| B (or Start) | Game menu (left soft key); **hold** for these settings |
| X | Wait (9) |
| Y | Map (right soft key) |
| Select | Open / close the big number keys (door codes) |
| D-pad / left stick up, down | Forward / back |
| D-pad / left stick left, right | Turn |
| L1 / R1 | Side-step (1 / 3) |
| L2 / R2 | Previous / next weapon (* / 7), as buttons or analog triggers |

**Door codes:** Select (or the keypad button) opens big number keys beside the game: the game moves to
the left and shrinks a little, the right side becomes a large keypad. The d-pad moves an orange cursor,
A presses the key, B or Select closes it. Touch works too. While it is open the other buttons do nothing,
so you can't walk or fire by accident.

## Vibration

BREW games read what the phone can do from text settings in their resource file. This copy of
Doom RPG was built for a handset that could not vibrate while playing sound, so its Options menu
only offers "FX: Sound / None" and never vibrates. Stout Marine reports a phone that can do both
(string 7 of `doomrpg.bar` is answered as "0" instead of "1"; the file itself is not changed), so the
game shows its own **Vibrate** option. The original starts with Vibrate off; Stout Marine switches it on
once per install (in the game's settings file, or in the running game on a fresh install) and after
that keeps whatever the player chooses in the game's Options menu.

## Building

Needs a JDK (8 or newer) and these Android SDK pieces: `platforms/android-23/android.jar`,
`dx` (`dalvik-exchange`), `aapt`, `zipalign` and `apksigner`. On Debian or Ubuntu:

    sudo apt install openjdk-17-jdk android-sdk android-sdk-platform-23 dalvik-exchange \
                     aapt zipalign apksigner

Then:

    tools/build.sh

The signed APK is written to `build/StoutMarine.apk`. A signing key (`tools/modmarine.keystore`)
is created on the first build. Keep it private and backed up: Android only installs updates signed
with the same key. The key is not part of this source; a build of your own is signed with your own
key, so it installs as a separate update line from the official releases (uninstall one before
installing the other; export your saved games first).

## Running on a computer (for testing)

`src/desktop/Desk.java` runs the game without Android, with a scripted list of key presses,
and saves screenshots:

    mkdir -p build/desk
    javac -encoding UTF-8 -d build/desk $(find src/core src/desktop test -name '*.java')
    java -cp build/desk Desk <folder with doomrpg.mod and doomrpg.bar> out 60000 "t=11000:k=E035,t=11100:u=E035"

Key codes are BREW codes in hex (E031 up, E032 down, E033 left, E034 right, E035 select,
E036 / E037 soft keys, E021-E02A digits 0-9).

## Tests

* `test/gen_cpu_tests.py` creates single-instruction test cases with the Unicorn engine
  (`pip install unicorn capstone`), and `test/CpuTest.java` checks the Java ARM/Thumb interpreter
  against them: `java -cp build/desk CpuTest cpu_tests.txt`.
* `test/QcelpTest.java` decodes raw QCELP packets, for comparison with FFmpeg.
* `test/CmxTest.java` converts every sound in a `.bar` file to WAV / MIDI files.
* `test/InstallTest.java` installs a game `.zip` the same way the app does.
* `test/Explore.java` plays the game automatically (walks, turns, fires, talks), counts every sound the
  game asks for and saves the mixed sound effects as `effects.wav`, so sound can be checked without a phone.
* `Desk` with `-Dwav=file.wav` also saves the sound effects of a scripted run.
* `test/UpscaleTest.java` checks the hq and xBR filters against FFmpeg's, pixel by pixel, and times them:
  `java -cp build/desk UpscaleTest picture.png outdir` (needs `ffmpeg`).

## How it works

* `src/core/brewemu/cpu` - ARMv5TE interpreter (ARM and Thumb).
* `src/core/brewemu/brew` - the BREW system: module loading, memory, the helper-function table, IShell
  (timers, resources, device info), IDisplay and bitmaps, IGraphics, files, memory and unzip streams,
  vibration and media. Written from the publicly documented BREW API.
* `src/core/brewemu/video` - the hq and xBR picture filters (exact ports of FFmpeg's hqx and xbr filters).
* `src/core/brewemu/audio` - sound: CMX (`cmid`) clips are turned into PCM (sampled sounds,
  QCELP 13K) or Standard MIDI files (music, played by Android's built-in synthesizer). Sound effects
  are mixed in software (`Mixer`) and streamed through one Android `AudioTrack`.
* `src/android/modmarine/app` - the Android app (setup screen, game screen, control deck, settings).

## Licences

* Everything here is under the MIT licence (see `LICENSE`), except:
* `src/core/brewemu/audio/Qcelp.java` is a Java port of FFmpeg's QCELP decoder and is under the
  GNU Lesser General Public License 2.1 or later (see `LICENSE-LGPL-2.1.txt`).
* `src/core/brewemu/video/Xbr.java` is a Java port of FFmpeg's xbr filter (Hyllian's xBR) and is under
  the GNU Lesser General Public License 2.1 or later.
* `src/core/brewemu/video/Hqx.java` and `Yuv.java` are Java ports of FFmpeg's hqx filter and keep its
  ISC licence (the notice is in the files and in `apk/assets/NOTICE.txt`).

Doom RPG is a trademark of its owners; this project is not affiliated with them and contains none
of their files. BREW is a trademark of Qualcomm; this project is not affiliated with Qualcomm.
