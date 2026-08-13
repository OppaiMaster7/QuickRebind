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

## Older Minecraft versions

The preset *format* is version-agnostic. Bind names (`key.sprint`) and key names
(`key.keyboard.left.control`) haven't changed since 1.13, so a preset captured
on 26.2 is readable by any version, and applying it somewhere older simply finds
fewer binds — the extra entries stay in the file rather than being dropped.

The *jar* is not. It declares `"minecraft": "~26.2"` and builds against 26.2-only
APIs, so Fabric Loader will refuse to load it on anything else. Running on an
older version needs a separate build targeting that version; the shared folder
then does the rest, because both builds read the same files.

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

## Building

Needs JDK 25.

```bash
./gradlew build
```

The jar lands in `build/libs/`.

## License

CC0-1.0. Do what you like with it.
