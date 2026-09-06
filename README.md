# QuickRebind

A client-side Fabric mod that saves your keybinds as presets and swaps between
them in two clicks — or one keypress. Built for Minecraft 26.2, 1.21.11,
1.21.8, 1.21.1, 1.20.1 and 1.19.2.

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

Two things deliberately *don't* travel, and live in the instance's own config
folder as `quickrebind.json`: which preset that install boots into, and which
one it is currently on. Both only mean anything per install — the whole reason
to set a launch preset is that this instance wants different keys from the one
next to it, and a shared value could only ever hold one answer.

## Moving between computers

The **Share** button on any preset puts a share code on your clipboard:

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

## Seeing what a preset will do first

`PvP (94 binds)` tells you nothing about whether it is the one you want, so
every row has a **Details** button. It lists each bind against the key it is on
right now, and the ones that would move are sorted to the top and drawn in
colour:

```
Bind                  Key
Sprint                Left Control → V
Drop                  Q → G
Jump                  Space
Open Inventory        E
JEI: Show Recipe      R — not in this install
```

So the first thing you read is the answer to "what changes if I press Apply",
not a hundred lines of things staying put. Applying only ever moves the binds
that differ, and the header counts them for you.

Details is also where the per-preset actions live — **Update**, **Rename**,
**Share** and **Delete**. **Update** is the one that was missing: it replaces a
preset's keys with the ones you are using now, keeping the same preset. Before
it existed, changing a single key and keeping it meant saving a second preset,
deleting the first and renaming the survivor.

## Switching without opening anything

**Switch to next preset** is a second keybind that applies the next preset in
the list and tells you which one you landed on, no screen involved. On a
two-preset setup it is a toggle. It ships **unbound** on purpose — it rebinds
your whole keyboard on one press, so it should be a key you picked rather than
one that happened to collide with something you already use.

The preset you are currently on is drawn green in the list, so it is obvious
which of them you are looking at.

## Minecraft versions

A preset made on one version works on any other. Bind names (`key.sprint`) and
key names (`key.keyboard.left.control`) haven't changed since 1.13, so applying
a 26.2 preset on something older just finds fewer binds — the extra entries stay
in the file rather than being dropped. Install the matching build on each
version and they all read the same folder.

The jar itself is per-version, because Fabric mods always are.

| Minecraft | Status | Loom | Gradle | Build JDK | Targets |
|---|---|---|---|---|---|
| 26.2    | built | 1.17-SNAPSHOT | 9.5.1 | 25 | 25 |
| 1.21.11 | built | 1.13.6 | 8.14.3 | 21 | 21 |
| 1.21.8  | built | 1.11.8 | 8.14.3 | 21 | 21 |
| 1.21.1  | built | 1.9.2 | 8.11.1 | 21 | 21 |
| 1.20.1  | built | 1.9.2 | 8.11.1 | 21 | 17 |
| 1.19.2  | built | 1.9.2 | 8.11.1 | 21 | 17 |
| 1.8.9   | planned, see below | Legacy Fabric | | | 8 |

What actually differs between those builds is smaller than it looks. The keybind
half is identical from 1.19.2 up — `getName`, `saveString`, `setKey`,
`getDefaultKey`, `releaseAll`, `resetMapping` have not moved — so
`KeyMappingHandle` and `GameBinds` are the same file everywhere. It is only the
GUI and the input plumbing that shift:

- **1.21.1 → 1.20.1** is one import. The controls screens sit in
  `screens.options.controls` from 1.21 and plain `screens.controls` before it.
- **1.20.1 → 1.19.2** is a real port: no `GuiGraphics` (rendering takes a
  `PoseStack`, and the background is yours to draw), no `Button.builder`, and no
  `Tooltip` class — buttons carry a `Button.OnTooltip` instead. Cycle-button
  hover hints are dropped on 1.19.2 rather than reimplemented.
- **1.21.1 → 1.21.8** is one line: `getToasts()` became `getToastManager()`.
- **1.21.8 → 1.21.11** is where the input rewrite lands, and it arrives well
  before the rest of the 26.2 changes. Key presses become `KeyEvent` records
  rather than three loose ints (so `keyPressed`, `KeyMapping.matches` and
  Fabric's `ScreenKeyboardEvents` all change shape), keybind categories become a
  `KeyMapping.Category` type instead of a string, `Util` moves to
  `net.minecraft.util`, and `CycleButton` takes its starting value in
  `builder()` rather than a separate `withInitialValue()`.
- **1.21.11 → 26.2** is the rendering half of the same journey:
  `GuiGraphicsExtractor` and an extract/submit pipeline in place of drawing
  straight to a `GuiGraphics`, plus `Gui.setScreen` and `Screens.getWidgets`.

That split is worth knowing about before starting a port. 1.21.11 looks like it
should be a small step from 1.21.8 and is actually most of the way to 26.2 on
input, while still being on the old renderer — so it is a hybrid, and neither
neighbour is a clean base to copy from. Starting from 1.21.8 and taking the
input changes forward is the shorter of the two routes.

### The Key Binds screen has to be told

One more difference, and it is a behavioural one rather than an API one. Apply a
preset while the vanilla **Key Binds** screen is open — which is easy to do,
since our button is on it — and from 1.20.1 onwards that screen carries on
showing the old keys until you leave it and come back.

The binds themselves moved the moment you pressed Apply; it is only the screen
that is behind. Each row caches its key label in a button when the list is
built, and vanilla only refreshes that when vanilla itself rebinds something. On
1.19.2 the label is rebuilt every frame instead, so that build never had the
problem.

`ControlsRefresh` fixes it by calling the list's own `refreshEntries()` after an
apply and whenever a screen comes back into view. It finds the list by field
*type* rather than by name, because field names aren't remapped when the mod is
built — `getDeclaredField("keyBindsList")` would work in the dev client and
throw in the jar people download, while a `KeyBindsList.class` literal is
remapped and works in both.

That is a hook with no compile-time safety net, so the self-test opens a real
Key Binds screen on every version and fails if the list can no longer be found.

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
because undo itself takes a snapshot, pressing it again redoes the change. The
snapshot belongs to the install that made it, so undoing in one instance can't
reach into another.

**Reset** is the bigger hammer: every bind back to what Minecraft ships with.
It snapshots first like anything else here, so Undo covers it too.

## Using it

- **F8** opens QuickRebind (rebindable, and it's in the Misc category like any
  other keybind).
- **Switch to next preset** is a second, unbound keybind — see above.
- There's also a button on the vanilla **Controls** and **Key Binds** screens,
  which is where you'd go looking anyway.

Settings cover the missing-bind policy, whether to confirm before applying,
which of those two buttons to show, and **Apply on launch** — pick a preset and
that instance sets itself up correctly every time it boots. That last one is
stored per install, so a dedicated PvP instance and a modpack can each boot into
their own keys.

## Layout

The mod is split so that one copy of the logic serves every Minecraft version:

```
core/                version-independent: presets, storage, share codes,
                     and the apply rules. Plain Java 8, Gson its only
                     dependency, no Minecraft imports at all.
core/src/test/       unit tests for all of that, plus the cross-version
                     ones. Its own tiny Gradle build; no Loom, no Minecraft.
versions/26.2/       the Fabric mod for one Minecraft version: a BindHandle
                     adapter over that version's keybind class, plus its GUI.
tools/               versions.tsv is the build matrix; verify.sh builds and
                     checks every version against it.
```

Each version is its own self-contained Gradle build that pulls `core` in as a
source directory. They're separate builds rather than subprojects because Loom
can't have two different versions of itself in one build, and older Minecraft
needs an older Loom. `core` is a third standalone build for the same reason:
every version consumes it and none of them owns it.

Adding a version means writing two small things — an adapter implementing
`BindHandle`, and the screens in that version's GUI API — plus a line in
`tools/versions.tsv`. The preset format, the folder layout, the share codes, the
apply rules and the self-test all come along for free, and `tools/verify.sh
--selftest --only <version>` will tell you whether the new adapter actually
works before you upload anything.

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

To launch a dev client, swap `build` for `runClient`.

The build matrix — which JDK builds what, and which Java each jar targets —
lives in `tools/versions.tsv`, and both the script below and CI read it from
there. Adding a Minecraft version is one line in that file.

## Testing

```bash
tools/verify.sh             # everything: core tests, then build + check each jar
tools/verify.sh --core      # just the core unit tests, about a second
tools/verify.sh --only 1.20.1
tools/verify.sh --selftest  # also launch each version and let the mod test itself
```

Four layers, because they catch different things and cost wildly different
amounts of time.

**Core unit tests** (`core/src/test`, ~60 of them, no Minecraft involved). Core
is where the rules live, so this is where most of the coverage is: the apply
policies, conflict counting, the details-screen diff, the preset folder, share
codes. `CrossVersionTest` is the one that guards the claim on the download
page — a preset captured on 26.2 applied in a 1.19.2 install that has fewer
binds, checking that the entries the older version has never heard of are still
in the file afterwards rather than quietly dropped. Core is compiled at
`--release 8` here too, so the Java-8 constraint the planned 1.8.9 build depends
on is enforced now rather than discovered later.

**Building** each version proves that version's GUI port still compiles against
its own API, which is the half that actually differs between them.

**Jar checks** (`tools/check-jar.py`) read what came out — the file players
download. A jar that compiles can still be broken in ways only the player sees:
an unsubstituted `${version}`, an entrypoint naming a class that isn't in the
jar, `depends.java` claiming 17 while the classes need 21, or a build that
silently stopped including `core`.

**The self-test** is the only layer that touches a real `KeyMapping`. Everything
above tests core against a fake install; whether *this* Minecraft's keybind class
answers `getName`, `saveString` and `setKey` the way the adapter assumes is a
question about the running game. `--selftest` launches each version's dev
client, and `SelfTest` in core drives capture, diff, apply, the unknown-key path
and a disk round trip against the live binds, puts them back exactly as it found
them, writes a report and quits. No world, no human — which is what makes
running it on every supported version before a release realistic rather than
theoretical. It uses a throwaway preset folder, so it can never touch your own
presets.

The self-test lives in core so there is one copy of it and a new port inherits
the checks instead of being trusted. `SelfTestTest` runs it against deliberately
broken adapters — one that reports keys in the wrong shape, one that says yes to
every rebind and performs none of them, one that accepts keys its version does
not have — because a harness that always reports PASS is worse than no harness.

CI runs everything except `--selftest`, which needs a GPU. That one is a local
gate before uploading a release.

## Publishing

```bash
MODRINTH_TOKEN=... tools/publish.sh --dry-run   # show what would go up
MODRINTH_TOKEN=... tools/publish.sh             # upload every version
MODRINTH_TOKEN=... tools/publish.sh --only 1.20.1
```

Each version uploads as `<mod_version>+<minecraft_version>` — `1.1.0+1.20.1` and
so on — because the project ships one jar per Minecraft version and Modrinth
needs them told apart. Release notes come from `CHANGELOG.md`. Tokens come from
<https://modrinth.com/settings/pats> and need the *Create versions* scope.

Publishing is public and awkward to take back, so it is deliberately not on any
automatic path: `build` never triggers it, no CI job calls it, and the script
refuses to start without a token, checks every jar exists first, and asks you to
type `publish` before the first upload.

One wrinkle worth knowing if you add a version: the jar to upload is `remapJar`
on an obfuscated Minecraft, but 26.2 ships unobfuscated and its Loom registers
no `remapJar` at all — there, plain `jar` is the artifact. The build asks rather
than assuming.

## License

CC0-1.0. Do what you like with it.
