# C&C: Wolfenstein

A Command & Conquer–style real-time strategy game for Android, written in Java, set in the
Wolfenstein universe: the **Kreisau Circle** resistance against the Regime's **Totenkopf
Division**, fighting over the uranium seams of Kreisau Valley.

Base building, harvesting, tech prerequisites, power, fog of war, a weapon-versus-armour
counter system and a skirmish AI that builds, defends and sends attack waves — all on a
touchscreen.

> **Assets:** every unit and structure you see is drawn from vector primitives at run time.
> There are no image files in this project, nothing is taken from any id Software or Bethesda
> release, and no code is derived from any Command & Conquer release. The naming is a homage;
> the implementation is original work.

## Building

```bash
# The simulation core: no Android SDK required
./gradlew :core:test

# The Android app (needs an SDK; set ANDROID_HOME or local.properties)
./gradlew :app:assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug       # onto a connected device
```

`settings.gradle.kts` only includes `:app` when an Android SDK is actually reachable, so
`:core` builds and tests on a plain JDK machine with no SDK installed.

## Playing it

Landscape, one skirmish against the AI on Kreisau Valley. You are the Resistance.

| Gesture | Effect |
|---|---|
| Tap a unit or structure | Select it |
| Drag one finger | Rubber-band select |
| Tap the ground with a selection | Move there (or attack a hostile, or harvest an ore seam) |
| Long press the ground | Attack-move there |
| Drag two fingers | Pan the camera; pinch to zoom |
| Tap or drag the minimap | Jump the camera |
| Sidebar button | Queue a unit or structure; long press cancels and refunds |
| Sidebar button marked READY | Arm placement, then tap the ground to site the structure |
| Tap a structure, then the ground | Set its rally point |

The economy is the usual loop: harvesters mine uranium seams, drive it back to a refinery and
that becomes credits. Power shortfalls do not switch the base off, they slow every production
line down. You lose when you have nothing left standing and nothing left alive.

### Order of battle

Structures are shared. The Command Post is pre-placed; everything else needs it standing.

| Structure | Cost | Notes |
|---|---|---|
| Generator | 300 | +100 power |
| Uranium Refinery | 1200 | Arrives with a free harvester |
| Barracks | 500 | Infantry |
| War Works | 1500 | Vehicles and harvesters; needs a refinery |
| Flak Turret | 600 | Outranges every mobile unit; needs a barracks |

| Kreisau Circle | Cost | | Totenkopf Division | Cost |
|---|---|---|---|---|
| Partisan — rifle infantry | 120 | | Soldat — line infantry | 110 |
| Panzerschreck Team — anti-armour | 300 | | Ubersoldat — armoured walker | 800 |
| Scout Jeep — fast, thin-skinned | 400 | | Panzerhund — fast mech-hound | 500 |
| Captured Panzer — heavy armour | 1000 | | | |

Rifles shred infantry and bounce off plate; rockets do the reverse. The whole matrix lives in
`DamageTable`, and every unit and structure stat lives in `UnitType` and `BuildingType` — those
three files are the entire balance surface.

## How it is put together

```
core/   plain Java, zero Android imports — the whole game simulation
app/    Android application — rendering, HUD and touch input only
```

The split is deliberate: because `core` has no Android dependency, the simulation compiles,
runs and is tested on any build machine.

| Package | What lives there |
|---|---|
| `core.map` | Tile map, terrain, the text `.map` format, bundled maps |
| `core.entity` | Units, structures and the stat tables |
| `core.path` | 8-way A*, occupancy grid, path following |
| `core.order` | Move, attack, attack-move, harvest, stop |
| `core.sim` | `GameWorld` — the authoritative fixed-step simulation |
| `core.ai` | The skirmish opponent |
| `core.harness` | Headless match runner |

`GameWorld` steps at a fixed 20 Hz and is deterministic for a given seed. The Android layer
never mutates game state directly: it reads state to draw, and pushes orders in.

### Running a match without a device

```bash
./gradlew :core:run --args="--ticks 24000 --seed 7 --difficulty VETERAN"
```

Plays a full AI-versus-AI match as fast as the CPU allows and prints a scoreboard every
simulated minute — the quickest way to see whether a balance change actually changed anything.

### Tests

```bash
./gradlew :core:test                # simulation: 45 tests, no SDK needed
./gradlew :app:testDebugUnitTest    # camera maths, plus Robolectric render tests
```

The app tests render real frames into bitmaps and write them to `app/build/test-frames/*.png`,
which is how the HUD and renderer get checked on a machine with no emulator.

## Status

Milestone 1: a playable skirmish. Not yet built: campaign missions, saving, sound, multiple
maps, unit veterancy, or a menu — the app drops you straight into a match.
