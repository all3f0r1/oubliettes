# Changelog

## 0.6

Saves, solved levels and settings of 0.5 are kept.

### Added

- **Wall** and **Dot** planks under the grid: the chosen mark is laid at the first tap, without
  going through a wall to reach a dot. Tapping the chosen plank again brings back the cycle.
- **New grid** in Endless leaves an unsolved grid. It asks first when the grid has marks.
- An option to be told when every count is met but a rule is broken. Off by default.

### Changed

- The grid and its planks rest on the bottom of the screen, under the thumb, and **Rules** moved
  down with them.
- A solved grid says so above the grid, with the progress of its difficulty.
- The row and the column of the cell touched last are lit over their cells too, not only their counts.
- The magnifier shows up on 10×10 grids as well: it measured the screen instead of the grid.
- A chosen plank carries a gold stroke under its lettering, besides its gold rim.
- In the tutorial, **Back** is now **Previous**.

### Fixed

- The music loops without a gap. It is now decoded once and played from memory, instead of being
  started over by the system player at every turn.
- A row drawn from the last column could be taken for the system back gesture.
- With large fonts, the title no longer runs under the planks and rows of planks wrap.
- Screen readers announce a solved grid, the tutorial's turn and the chosen plank; sliders are
  named, screen titles are headings, and a level begun is said once.

## 0.5

<p>
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.5/metadata/en-US/images/phoneScreenshots/2-game.png" width="200" alt="A grid in progress: underlined counts, the lit row and column, Undo, Redo, Reset and the Rules plank" />
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.5/metadata/en-US/images/phoneScreenshots/4-tutorial.png" width="200" alt="Tutorial step with two cells left to mark, and the Show me plank" />
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.5/metadata/en-US/images/phoneScreenshots/6-levels.png" width="200" alt="Campaign table: level 1 solved, level 2 begun" />
</p>

Saves, solved levels and settings of 0.4 are kept.

### Fixed

- A fast drag no longer leaves holes: every cell between two positions of the finger is filled,
  including the one where it lifts.
- Only the finger that started a drag draws. Other fingers landing on the grid are ignored.
- Endless counts a grid the moment it is solved, and only once. It used to count on **Next grid**,
  so a grid solved and then left was not counted.
- Digging an Endless grid stops when you leave the screen or pick another size, instead of running
  on in the background. It also gives up after 5000 tries and offers to dig another grid.
- The status bar icons were dark on the dark inner screens. They are now light everywhere.
- The music no longer plays over another app: it stays silent while another app is playing, stops
  when one starts, and comes back afterwards.

### Saves

- The 150 campaign grids now ship with the app instead of being generated from seeds: no wait, and
  they stay the same whatever becomes of the generator. They are the grids of 0.4.
- Every save carries a fingerprint of its grid, and Endless saves the grid itself. A change of the
  generator can no longer put old marks on a new grid.
- Grids read from a save or from the campaign file are checked: sizes, counts, monsters and chests.

### Playing

- **Redo**, next to Undo. The history (50 actions) is saved with the marks: it is still there after
  leaving the grid or the app.
- **Reset** is an action like any other and can be undone. It no longer asks twice, and its option
  is gone. On a solved grid it is called **Play again**.
- **Rules** at the top of every grid shows the rules without leaving it, including the one about
  walls never being more than two cells thick.
- **Continue** on the main menu reopens the grid played last. The table of levels marks the levels
  that are begun.
- The tutorial is played: you mark the cells of each step yourself, and **Show me** does it for you.
- The row and the column of the cell touched last are lit up to their counts.
- On grids whose cells are smaller than a fingertip (12×12 on a phone), a magnifier shows what is
  under the finger. It can be turned off.
- A drag is one transaction: the grid is checked and saved when the finger lifts, not at every cell.

### Accessibility and comfort

- Every cell and every count is described to screen readers, with actions to mark the cells.
- A count reached is underlined, a count exceeded is struck through: the colour is no longer the
  only sign.
- Option for plain digits on the wall counts.
- Calm mode: monsters, chests and torches stop moving. On by default when the system animations are
  off.
- Every screen scrolls when it does not fit, on small screens or with large fonts. The tutorial text
  no longer has a fixed height.

### Under the hood

- The marks, the drags and the history of a grid moved out of the screens into `Session`, with
  their own tests.
- CI runs Android Lint and publishes the test and Lint reports. Signing and publishing are a
  separate job that only runs for tags.
- The coroutines library is declared instead of being picked up from other dependencies.

## 0.4

<p>
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.4/metadata/en-US/images/phoneScreenshots/1-menu.png" width="200" alt="Main menu in blackletter, with wooden planks and the monsters watching" />
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.4/metadata/en-US/images/phoneScreenshots/6-levels.png" width="200" alt="Campaign table: three difficulties, 50 numbered slabs, the solved level showing its dungeon" />
  <img src="https://raw.githubusercontent.com/all3f0r1/oubliettes/0.4/metadata/en-US/images/phoneScreenshots/2-game.png" width="200" alt="A grid in progress with the Undo, Reset and Next level planks" />
</p>

### Campaign

- Three difficulties: Easy (8×8), Medium (10×10) and Hard (12×12), 50 levels each, all open from the
  start.
- The campaign opens on a table of numbered levels. A solved level shows a small picture of its
  finished dungeon.
- Every level keeps its own marks, so you can leave one and come back to it.
- Easy 1–10 and Medium 11–25 are the grids of levels 1–25 of 0.3. Campaign progress of 0.3 is not
  carried over: with Skip, a level reached was not a level solved.

### Playing

- A drag can now paint over the cells changed by the previous action, on top of unknown cells: drag
  again over a drag to turn it into dots, or back to unknown. Older marks are still never overwritten.
- **Skip** is gone. The right-hand button moves on once the grid is solved. In Endless, the only
  ways out of a grid are to solve it or to pick another size.
- **Clear** is now **Reset**: greyed while the grid is empty, and it cannot be undone. It asks for a
  second tap (“Sure?”) first. A solved grid can be reset to play it again.
- Every mark gives a light vibration, and the screen stays on while a grid is shown.
- Options has checkboxes to turn off the second tap of Reset, the vibration and the screen staying on.

### Look

- Titles are set in blackletter (UnifrakturCook) and everything else in a calligraphic hand
  (Almendra), wall counts included. Both fonts are bundled, under the SIL Open Font License.
- Every button is a riveted wooden plank; sliders are iron bars filling with gold; checkboxes are
  iron plates crossed in gold; a torch burns while a grid is being dug.
- Torches, chests and monsters are animated frame by frame, all the time. Monsters were redrawn to
  look meaner (fangs, frowns, slit pupils); they blink and glance left and right.

### Sound

- The menus, Options and the table of levels have their own music loop: 24 seconds, lute alone,
  slower and calmer than the 80-second loop of the grids.

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
