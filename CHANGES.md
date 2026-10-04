# Changes

## 1.9
- Door codes: when the game asks for a code, the number keys open by themselves (the big keypad in handheld
  mode, the touch keypad on phones) and close again afterwards. Select / the keypad button still open them
  any time.
- Settings button: the gear now opens Stout Marine's settings with one tap, and the game's menu has its own
  button (three bars). On controllers, Start opens the settings (B is still the game's menu). Holding the
  menu button still opens the settings, as before.
- Mini-map (settings, Mini-map: on / off; off by default): a small, see-through map in the top-right corner
  of the game picture while you play, centred on you. It is drawn from the game's own automap data, read
  from the emulated phone's memory without changing anything, so it shows exactly what the game's map
  shows: places you have visited, walls, doors and the monsters there. It hides itself in menus, on the
  game's own map screen and while the big keypad is open.

## 1.8.2
- Fixed: the game's own Volume setting (Options, Volume) now changes the music and sounds that are already
  playing, as on the original phone. Before, music kept its old volume until the next song started.

## 1.8.1
- Fixed: fires (the ones you put out with the extinguisher) and other animated scenery stood still. The game
  asks the phone for the time of day in milliseconds (BREW's GETTIMEMS) to animate them, and Stout Marine
  answered with seconds. It now answers in milliseconds, so they animate as on the original phone.

## 1.8
- Picture setting with four choices: Sharp pixels (default), Smooth, Smart upscale (hq) and xBR upscale.
  The two upscale filters are Java ports of FFmpeg's hqx (ISC licence) and xbr (LGPL) filters and give
  exactly the same pixels; they run on their own thread so the game is never slowed down.
  The old "Smooth pixels" choice carries over.

## 1.7
- Big keypad for door codes in handheld mode: Select (or the keypad button) opens large number keys
  beside the game. The d-pad moves a cursor, A presses, B or Select closes; touch works too. While it is
  open the other buttons do nothing, so you can't walk or fire by accident.
- Select now opens the keypad (Y is still the map). The button guide and help show it.

## 1.6 - Stout Marine
- New name: **Stout Marine** (was MOD Marine), with a new logo and app icon. It is the same app underneath:
  it installs over 1.5 and keeps saved games and settings.
- About screen (settings, then About Stout Marine; also linked from the first screen), with the
  open source licences.
- First screen fits every screen: it scrolls only when needed, the logo shrinks on short screens,
  sideways screens use two columns, and a controller's A button presses "Choose game file".
- A short reminder to save in the game when leaving it with Home or Recents (at most every 10 minutes).
- "What's new" is shown once after an update.
- Removed the second-screen controls (dual-screen handhelds); handheld mode on the main screen is unchanged.

## 1.5
- Touch controls: a cross like a handheld's d-pad (up / down walk, left / right turn) and separate
  L / R shoulder buttons for side-stepping. Help pictures and text updated.

## 1.4
- Handheld mode for gaming handhelds (RG Rotate, AYN Thor and others): the game at the largest sharp
  size with a button guide beside it. A = fire / use, B = menu (hold for settings), X = wait, Y = map,
  d-pad = move and turn, L / R = side-step, L2 / R2 = change weapon (buttons or analog triggers).
- Left stick moves like the d-pad.

## 1.3
- The game's Vibrate option starts switched on (once; the player's choice is kept after that).

## 1.2
- Vibration works: the game's own Vibrate option is shown.
- The large screen (240 x 320) is the default.

## 1.1
- Sound effects fixed: all effects are mixed in software and played through one audio stream.

## 1.0
- First release: a BREW phone emulator that runs your own copy of Doom RPG (BREW), with pixel-art
  touch controls, smooth-pixels option, two screen sizes, and export / import of saved games.
