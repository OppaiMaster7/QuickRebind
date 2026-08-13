# Minecraft 26.2 API notes

Things that changed and that older tutorials (and most of the internet) still
get wrong. Verified against the actual 26.2 jars, not from memory.

## Finding the truth yourself

Loom caches deobfuscated jars and Fabric API **sources**. When in doubt, read
them instead of guessing:

```bash
# Minecraft, deobfuscated (class list + signatures via javap)
CP="$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar"
javap -cp "$CP" net.minecraft.client.Minecraft

# Fabric API sources, per module
find ~/.gradle/caches/modules-2 -ipath "*fabric-api*" -name "*-sources.jar"
```

`./gradlew genSources` also produces readable Minecraft sources for the IDE.

## Screen state moved off `Minecraft`

`Minecraft.screen` and `Minecraft.setScreen()` **no longer exist**. Screen
management lives on `Gui`:

```java
Screen current = minecraft.gui.screen();
minecraft.gui.setScreen(new MyScreen());
```

## `GuiGraphics` is gone

Replaced by `GuiGraphicsExtractor`, and rendering moved to an extract/submit
pipeline. `Screen.render(...)` is now:

```java
@Override
public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    // your drawing here
}
```

Useful `GuiGraphicsExtractor` methods:

| Call | Notes |
|---|---|
| `text(Font, Component/String, x, y, argb)` | `argb` needs its alpha byte set, or nothing shows |
| `centeredText(Font, Component, centerX, y, argb)` | |
| `fill(x1, y1, x2, y2, argb)` | |
| `guiWidth()` / `guiHeight()` | scaled GUI size |
| `pose()` | `org.joml.Matrix3x2fStack` — `pushMatrix()` / `scale()` / `popMatrix()` |
| `enableScissor` / `disableScissor` | clipping |

`Screen.extractBackground(...)` is separate and called for you.

## HUD rendering

`HudRenderCallback` is gone. Use `HudElementRegistry` from
`fabric-rendering-v1`:

```java
HudElementRegistry.addLast(id("overlay"), new MyHud());

class MyHud implements HudElement {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) { ... }
}
```

Positions relative to vanilla elements come from `VanillaHudElements`
(`CHAT`, `HOTBAR`, ...) with `attachElementBefore` / `attachElementAfter`.

There is no `Options.hideGui` to check — vanilla skips the whole HUD stack when
the GUI is hidden or a screen is open.

## Keybinds

The module is `fabric-key-mapping-api-v1` (not `key-binding`), and categories
are now a type rather than a string:

```java
KeyMappingHelper.registerKeyMapping(
    new KeyMapping("key.mymod.thing", GLFW.GLFW_KEY_F6, KeyMapping.Category.MISC));
```

## Input events

Key handling takes event objects instead of loose ints:

```java
public boolean keyPressed(KeyEvent event)     // was (int keyCode, int scanCode, int modifiers)
```

`Button.OnPress` still receives the `Button`, but `AbstractButton.onPress` takes
an `InputWithModifiers`.

## Assorted renames

| Old | 26.2 |
|---|---|
| `MinecraftClient` | `Minecraft` |
| `Identifier.of(...)` | `Identifier.fromNamespaceAndPath(...)` / `withDefaultNamespace(...)` |
| `net.minecraft.client.gui.screens.OptionsScreen` | `net.minecraft.client.gui.screens.options.OptionsScreen` |
| `KeyBindingHelper` | `KeyMappingHelper` |
| `KeyBinding` | `KeyMapping` |

## Misc

- Current world name (singleplayer): `minecraft.getSingleplayerServer().getWorldData().getLevelName()`
- Current server: `minecraft.getCurrentServer()` → `ServerData` with public `name` / `ip`
- Raw mouse position: `minecraft.mouseHandler.xpos()` / `ypos()`; buttons via `isLeftPressed()` etc.
- Player look: `player.getYRot()` / `getXRot()`
- `CycleButton.builder(valueToText, initialValue).withValues(list).create(x, y, w, h, label, onChange)`
- `CycleButton.onOffBuilder(initial)` for booleans
- Adding widgets to someone else's screen: `Screens.getWidgets(screen).add(widget)` inside `ScreenEvents.AFTER_INIT`
