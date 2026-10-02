# Changelog

## 0.3

<p>
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.3/metadata/en-US/images/phoneScreenshots/2-game.png" width="200" alt="A grid in progress, with the new Undo button" />
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.3/metadata/en-US/images/phoneScreenshots/3-solved.png" width="200" alt="A solved dungeon: no dots left, monsters hopping" />
</p>

**The application ID is now `io.github.all3f0r1.oubliettes`** (was `app.oubliettes`), an identifier under
a domain the project controls, as F-Droid asks. Android sees it as a different app: 0.3 installs next to
0.2 instead of updating it, and starts with fresh progress. Uninstall 0.2 by hand.

### Quality of life

- Dragging now stays on the row or the column it started along (no zig-zag), and only fills unknown
  cells: it can no longer overwrite cells that are already marked.
- New **Undo** button: takes back the last action, a whole drag counting as one. Clearing the
  grid can be undone too. The history is kept while the grid stays on screen.
- Fixed: two quick taps on the same cell could count as a single one.
- When a dungeon is solved, the open-cell dots disappear, the whole floor lights up, monsters hop and
  the chest pulses.

### Language

- The interface is now in English (it was in French).

### Sound

- A single short, subtle click for every cell change replaces the wall and mark sounds of 0.2 (the wall
  thud was too low to be heard on phone speakers).
- New victory jingle when a grid is completed; the music goes silent while it plays.

### Grids

- Walls are never more than two cells thick: the generator keeps digging until no 3×3 block of walls
  is left. This also replaces the 0.2 filter on fully walled rows and columns. Grids differ from 0.2
  for the same level number (level 1 happens to be unchanged).
- 12×12 grids take longer to generate than before (about three times, measured on a desktop JVM).

### F-Droid preparation

- GPL-3.0-or-later license added.
- Store listing in `metadata/` (description, icon, feature graphic, screenshots,
  per-version changelogs) and a draft build recipe in `fdroid/`.
- The APK no longer embeds the encrypted dependency list that F-Droid rejects.

## 0.2

<p>
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.2/docs/menu.png" width="200" alt="Main menu" />
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.2/docs/game.png" width="200" alt="A grid in progress" />
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.2/docs/tutorial.png" width="200" alt="Tutorial" />
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.2/docs/options.png" width="200" alt="Options" />
</p>

### Main menu and game modes

- New main menu in a dungeon theme: torch-lit stone wall with flickering light, wooden plank buttons.
  The system back button returns to it from any screen.
- **Campaign**: a single sequence of levels with fixed seeds (level *n* is always the same grid). Grids
  grow with the level: 8×8 up to level 10, 10×10 up to level 25, 12×12 after that.
- **Endless**: randomly generated grids in 8×8, 10×10 or 12×12. Each size remembers its current grid.
- **Tutorial**: an 8-step guided solve of a 6×6 dungeon. Each step marks the cells it deduces, outlines
  them in gold and explains the rule behind it: wall counts, dead ends, treasure rooms, hallways.

### Sound

- Sound effects for placing a wall, marking a cell and solving a dungeon.
- Background music: an original 80-second medieval loop written to be easy on the ears — slow 3/4 in
  D Dorian, soft plucked strings, a quiet recorder that rests for a third of the loop, no percussion,
  nothing above 5 kHz, seamless loop point. It is synthesised by `tools/make_audio.py`.
- Options screen with separate volume sliders for sounds and music.
- "Replace the music loop with the radio" checkbox: plays the live stream of
  [Ancient FM](https://ancientfm.com/) instead. If the stream cannot be reached within 15 seconds or
  drops, the app says so and goes back to the loop. The app now asks for the Internet permission,
  used for this stream only.
- Music stops when the app leaves the screen.

### Readability

- Walls are much lighter against a darker floor, with cracked and mossy variants.
- Three new monsters (skull, bat, watcher) on top of slime, ghost and imp; monsters in a grid get
  different faces.
- Cells known to be open (marked ones, monsters, chests) have a lighter floor and a bigger dot.
- Wall counts are larger and much heavier. A satisfied count is green, an exceeded one red, a pending
  one white.

### Fixes and other changes

- Progress on a grid is saved per mode and per size, so playing one grid no longer discards the marks
  of another. Marks from 0.1 on the first 8×8 grid are carried over.
- Grids with more than one fully walled row or column are no longer generated (they squeezed the
  dungeon into a corner, mostly at 12×12). A few grids therefore differ from 0.1 for the same number.
- Release APK is minified with R8.
- Campaign replaces the per-size level counters of 0.1 and starts at level 1.

## 0.1

- First release: 8×8, 10×10 and 12×12 grids generated on the device, each with a single solution.
- Walls, wall counts, monsters in dead ends, treasure rooms.
- Signed release APK built by CI.
