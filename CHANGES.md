# Changes

## 2.2
- Edit touch controls (settings): drag groups of buttons (the cross, the fire button, L / R, the top row; in
  landscape also the wait button and the weapon slot) to new places on the control area, and make the cross or
  the fire button 80 to 140 % of their size. Swap left / right mirrors the layout for left-handed play. Portrait
  and landscape are kept separately; Reset to default puts them back. Buttons can't overlap each other, the XP
  bar or the game picture: a drop with no room goes to the nearest free place. A saved layout that doesn't fit
  another screen falls back to the default for the buttons concerned.
- Controller buttons (settings): choose an action (fire, game menu, wait, map, step left / right, previous /
  next weapon, keypad), then press the button for it; if another action had that button, the two swap. The
  button guide, the big keypad and the windows' hints show the buttons you chose. Start always opens the
  settings and the d-pad and sticks always move, so nobody can lock themselves out. Reset to default.

## 2.1
- XP bar (Diablo style), on by default: your level in a round socket, a gold bar with ten segments, the XP you
  have in this level, the XP the level needs, and how much is left until the next level. The numbers are the
  game's own, read from the emulated phone's memory without changing anything (the game keeps XP per level,
  so the bar fills from empty to the next level). Portrait: a strip right under the game picture. Landscape:
  the right panel, under R. Handheld: the top of the left panel. Settings, XP bar: on / off.
- The extras now start on for new players: XP bar, mini-map in the corner, Smart HQ picture, large screen
  (240 x 320) and strong vibration. Settings you changed yourself are kept.
- First start: a "Stout Marine extras" window lists them as they are set and says where to change them
  (the gear, holding the menu button, or Start), with a button that opens the settings.

## 2.0
- Settings, questions and notes are drawn in the same pixel style as the controls and the weapon picker,
  and work with touch or a controller (d-pad moves, left / right change a value, A chooses, B goes back).
- Save now (settings): saves in one tap. It opens the game's own menu and chooses Save Game, exactly as if
  you did it yourself, and a note says when it's saved (or that it can't save right now, e.g. in a fight).
- Stats (settings): health, armor, level, XP, credits, and for this sector and overall the time, monsters
  killed, secrets found, moves and deaths. Read from the emulated phone's memory; the same numbers as the
  game's own Status screen.
- Picture: tapping it shows a small preview of each choice, made from the current game picture.
- Mini-map: Off, Corner or Big (a larger map showing more of the area).
- Vibration: Off, Light or Strong.
- First start: a short tour of the touch buttons (tap to go on, or skip).

## 1.10
- Weapon picker: tap the weapon in the middle of the weapon slot (or hold L2 / R2 on a controller) to see all
  your weapons with their ammo, and tap one (or d-pad + A) to switch to it. The pictures are the game's own
  weapon pickups and ammo icons, read from your copy of the game (the pistol, which the game never drops, is
  Stout Marine's own drawing). Switching uses the game's own next / previous weapon keys.
- The weapon slot shows the weapon in hand, and small icons beside its arrows show what each arrow switches to.

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
