# Minecraft 1.21.11 API notes

1.21.11 is the awkward one. It reads like a patch release after 1.21.8 and is
actually the version where Mojang's input rewrite lands — most of the way to
26.2 on how key presses and keybinds work, while still drawing through the old
`GuiGraphics`. Neither neighbour is a clean base to copy from.

Verified against the actual 1.21.11 jars, the same way as
[api-notes-26.2.md](api-notes-26.2.md):

```bash
JAR=$(find ~/.gradle/caches/fabric-loom/minecraftMaven \
  -name "minecraft-clientonly-1.21.11-*.jar" ! -name "*intermediary*" ! -name "*sources*" | head -1)
mkdir -p /tmp/mc && unzip -oq "$JAR" -d /tmp/mc
javap -classpath /tmp/mc net.minecraft.client.KeyMapping
```

## What moved, coming from 1.21.8

| Thing | 1.21.8 | 1.21.11 |
|---|---|---|
| Key presses | `keyPressed(int, int, int)` | `keyPressed(KeyEvent)` |
| Bind matching | `mapping.matches(key, scancode)` | `mapping.matches(KeyEvent)` |
| Fabric screen events | `(screen, key, scancode, modifiers)` | `(screen, event)` |
| Keybind category | `"key.categories.misc"` string | `KeyMapping.Category.MISC` |
| `Util` | `net.minecraft.Util` | `net.minecraft.util.Util` |
| Cycle buttons | `builder(naming).withInitialValue(v)` | `builder(naming, v)` |

`KeyEvent` is a record in `net.minecraft.client.input`, carrying `key()`,
`scancode()` and `modifiers()` — the same three ints, so call sites unpack
rather than rethink.

`KeyMapping.Category` is a record wrapping an `Identifier`, with the vanilla set
as constants (`MOVEMENT`, `MISC`, `MULTIPLAYER`, `GAMEPLAY`, `INVENTORY`,
`CREATIVE`, `SPECTATOR`, `DEBUG`) and `Category.register(Identifier)` for
your own.

## What has *not* moved yet

These all arrive in 26.x, and reaching for them on 1.21.11 fails to compile:

- `GuiGraphicsExtractor` and the extract/submit render pipeline. 1.21.11 still
  overrides `render(GuiGraphics, int, int, float)`.
- `Gui.setScreen`. It is still `Minecraft.setScreen`.
- `Screens.getWidgets` in Fabric API. It is still `Screens.getButtons`.
- `KeyMappingHelper`. It is still `KeyBindingHelper`
  (`net.fabricmc.fabric.api.client.keybinding.v1`).

## Toolchain

1.21.11 is still obfuscated, so it takes the `officialMojangMappings()` route
like 1.21.1 rather than 26.2's. It needs Loom 1.13.6, which needs Gradle 8.14,
and it runs on Java 21 — see `tools/versions.tsv`.

## The cheap way to find this out

Don't port blind. Copy the nearest version, build, and read the compiler
errors — there were nine, and each one named the exact symbol that moved. Then
`tools/verify.sh --selftest --only 1.21.11` opens every screen in a real client,
which is what catches the half that compiles and still doesn't work.
