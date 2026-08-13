# QuickRebind

A client-side Fabric mod for Minecraft 26.2 that saves your keybinds as presets
and swaps between them in two clicks.

## The problem

You play PvP, so your keys are nothing like the defaults. A friend asks you to
hop on a modded server, you launch a fresh instance, and every bind is back to
vanilla. Twenty minutes of clicking through the controls screen later, you are
finally ready to play — and when you go back to PvP the next day, you do it all
again in reverse.

QuickRebind saves the whole set once and puts it back whenever you want.

## Where presets live

Not in the instance. That's the point.

| OS | Folder |
|---|---|
| Windows | `%APPDATA%\QuickRebind` |
| macOS | `~/Library/Application Support/QuickRebind` |
| Linux | `$XDG_DATA_HOME/QuickRebind`, else `~/.local/share/QuickRebind` |

Because the folder belongs to your computer rather than to a game directory, a
preset saved in one install shows up in every other one: a different modpack, a
different launcher, a different Minecraft account you sign into on the same PC.
Point `-Dquickrebind.dir=...` or the `QUICKREBIND_DIR` environment variable
somewhere else — a synced drive, say — if you want.

Each preset is a plain JSON file named after itself, so `pvp.json` is something
you can open in Notepad, back up, or email to someone.

## Moving between computers

The **Copy** button on any preset puts a share code on your clipboard:

```
QRB1.H4sIAAAAAAAA_6tWKkstKs7Mz1OyUvIvyMxLV9JRSsxNzMlMTk...
```

Paste it into Discord. Whoever receives it hits **Paste share code** and the
preset lands in their folder. It's gzipped JSON in base64 — the name and the
binds, nothing about you.

## Binds are matched by name, not by position

A preset stores entries like `key.sprint` → `key.keyboard.left.control`, using
the same identifier the game and every mod already use internally. Two useful
things follow:

- **Modded binds survive a trip through vanilla.** Apply a preset in an install
  that doesn't have JEI and the JEI entries aren't touched or thrown away — they
  stay in the file, waiting for the next time you launch the pack that has it.
- **Binds the preset doesn't mention are left alone by default**, so a vanilla
  preset won't quietly wipe your modded keys. Switch *Binds not in the preset*
  to **Reset to default** in settings if you'd rather the result match the
  preset exactly.

After applying, the screen tells you how many binds now share a key with
another one, so you find out about a conflict there and then rather than in the
middle of a fight.

## Minecraft versions

A preset made on one version works on any other. Bind names (`key.sprint`) and
key names (`key.keyboard.left.control`) haven't changed since 1.13, so applying
a 26.2 preset on something older just finds fewer binds — the extra entries stay
in the file rather than being dropped. Install the matching build on each
version and they all read the same folder.

The jar itself is per-version, because Fabric mods always are.

| Minecraft | Status | Loom | Gradle | Build JDK | Targets |
|---|---|---|---|---|---|
| 26.2   | built | 1.17-SNAPSHOT | 9.5.1 | 25 | 25 |
| 1.21.1 | built | 1.9.2 | 8.11.1 | 21 | 21 |
| 1.20.1 | built | 1.9.2 | 8.11.1 | 21 | 17 |
| 1.19.2 | built | 1.9.2 | 8.11.1 | 21 | 17 |
| 1.8.9  | planned, see below | Legacy Fabric | | | 8 |

What actually differs between those four builds is smaller than it looks. The
keybind half is identical from 1.19.2 up — `getName`, `saveString`, `setKey`,
`getDefaultKey`, `releaseAll`, `resetMapping` have not moved — so
`KeyMappingHandle` and `GameBinds` are the same file everywhere. It is only the
GUI that shifts:

- **1.21.1 → 1.20.1** is one import. The controls screens sit in
  `screens.options.controls` from 1.21 and plain `screens.controls` before it.
- **1.20.1 → 1.19.2** is a real port: no `GuiGraphics` (rendering takes a
  `PoseStack`, and the background is yours to draw), no `Button.builder`, and no
  `Tooltip` class — buttons carry a `Button.OnTooltip` instead. Cycle-button
  hover hints are dropped on 1.19.2 rather than reimplemented.
- **1.21.1 → 26.2** is the largest jump: `GuiGraphicsExtractor` and an
  extract/submit pipeline, `Gui.setScreen`, `KeyEvent` objects, typed keybind
  categories, and `net.minecraft.util.Util`.

The toolchain differs per version and isn't a free choice. 26.2 ships
unobfuscated, and the Loom that builds it refuses Mojang mappings outright
("Cannot use Mojang mappings in a non-obfuscated environment"), so anything
still obfuscated needs an older Loom — which needs an older Gradle, which needs
an older JDK. Hence a wrapper per version directory rather than one at the root.

### 1.8.9 is a different problem

Everything above holds from 1.13 on. Before that, Minecraft stored binds as
LWJGL2 integer keycodes rather than `key.keyboard.*` names, so that build needs
a translation layer on top of the usual port. Mojang's own `OptionsKeyLwjgl3Fix`
and `OptionsKeyTranslationFix` datafixers hold the authoritative mapping and are
the place to lift it from. Legacy Fabric (`repo.legacyfabric.net`) supplies the
loader, since official Fabric only goes back to 1.14.

Core is written to plain Java 8 with Gson as its only dependency specifically so
that build can use it unchanged.

## Undo

Every apply snapshots your binds first. **Undo** puts them straight back, and
because undo itself takes a snapshot, pressing it again redoes the change.

## Using it

- **F8** opens QuickRebind (rebindable, and it's in the Misc category like any
  other keybind).
- There's also a button on the vanilla **Controls** and **Key Binds** screens,
  which is where you'd go looking anyway.

Settings cover the missing-bind policy, whether to confirm before applying,
which of those two buttons to show, and **Apply on launch** — pick a preset and
that instance sets itself up correctly every time it boots.

## Layout

The mod is split so that one copy of the logic serves every Minecraft version:

```
core/                version-independent: presets, storage, share codes,
                     and the apply rules. Plain Java 8, Gson its only
                     dependency, no Minecraft imports at all.
versions/26.2/       the Fabric mod for one Minecraft version: a BindHandle
                     adapter over that version's keybind class, plus its GUI.
```

Each version is its own self-contained Gradle build that pulls `core` in as a
source directory. They're separate builds rather than subprojects because Loom
can't have two different versions of itself in one build, and older Minecraft
needs an older Loom.

Adding a version means writing two small things — an adapter implementing
`BindHandle`, and the screens in that version's GUI API. The preset format, the
folder layout, the share codes and the apply rules come along for free.

## Building

Each version builds with the wrapper in its own directory, and needs the JDK
from the table above:

```bash
# 26.2 — JDK 25
./gradlew -p versions/26.2 build

# 1.21.1, 1.20.1, 1.19.2 — JDK 21
versions/1.21.1/gradlew -p versions/1.21.1 build \
  -Dorg.gradle.java.home=/path/to/jdk-21
```

Drop the `-Dorg.gradle.java.home` if `JAVA_HOME` already points at the right
JDK. Jars land in `versions/<version>/build/libs/`.

To launch a dev client for testing, swap `build` for `runClient`.

## License

CC0-1.0. Do what you like with it.
