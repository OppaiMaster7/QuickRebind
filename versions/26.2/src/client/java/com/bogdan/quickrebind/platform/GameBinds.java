package com.bogdan.quickrebind.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.bogdan.quickrebind.QuickRebindClient;
import com.bogdan.quickrebind.core.ApplyEngine;
import com.bogdan.quickrebind.core.ApplyResult;
import com.bogdan.quickrebind.core.BindDiff;
import com.bogdan.quickrebind.core.BindHandle;
import com.bogdan.quickrebind.core.JsonStore;
import com.bogdan.quickrebind.core.MissingBindPolicy;
import com.bogdan.quickrebind.core.Preset;
import com.bogdan.quickrebind.core.SharedPaths;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * The 26.2 side of applying a preset: hand core a list of binds, then do the
 * version-specific bookkeeping it can't know about.
 */
public final class GameBinds {
	private GameBinds() {
	}

	public static List<BindHandle> handles(Options options) {
		List<BindHandle> handles = new ArrayList<>(options.keyMappings.length);

		for (KeyMapping mapping : options.keyMappings) {
			if (mapping != null) {
				handles.add(new KeyMappingHandle(mapping));
			}
		}

		return handles;
	}

	public static Map<String, String> capture(Options options) {
		return ApplyEngine.capture(handles(options));
	}

	public static int conflicts(Options options) {
		return ApplyEngine.conflicts(handles(options));
	}

	/** What applying {@code preset} here would change, without changing it. */
	public static List<BindDiff> diff(Options options, Preset preset, MissingBindPolicy policy) {
		return ApplyEngine.diff(handles(options), preset, policy);
	}

	/**
	 * Writes {@code preset} into the live keybinds and saves options.txt.
	 *
	 * <p>Always snapshots the current binds to the undo file first, so a wrong
	 * click is one button away from being reversed.
	 *
	 * <p>Every route into applying a preset comes through here — the list, the
	 * details screen, auto-apply, the switch key — so this is also where the
	 * "which preset am I on" marker gets written, rather than at five call sites
	 * that each have to remember. Undo passes its synthetic snapshot through the
	 * same door: that preset isn't in the store, so the marker stops matching
	 * anything and the list correctly shows you are on none of them.
	 */
	public static ApplyResult apply(Minecraft minecraft, Preset preset, MissingBindPolicy policy) {
		Options options = minecraft.options;
		snapshotForUndo(options);

		// Anything held down right now would otherwise stay "pressed" on its old key.
		KeyMapping.releaseAll();

		ApplyResult result = ApplyEngine.apply(handles(options), preset, policy);

		KeyMapping.resetMapping();
		options.save();
		QuickRebindClient.markApplied(preset.id);

		// The quick-switch key works from the Key Binds screen, so that screen
		// can be the one on display while its keys move underneath it.
		ControlsRefresh.refresh(minecraft.gui.screen());

		QuickRebindClient.LOGGER.info("Applied preset '{}': {}", preset.name, result);
		return result;
	}

	public static int resetAllToDefault(Minecraft minecraft) {
		Options options = minecraft.options;
		snapshotForUndo(options);
		KeyMapping.releaseAll();

		int changed = ApplyEngine.resetAllToDefault(handles(options));

		KeyMapping.resetMapping();
		options.save();
		ControlsRefresh.refresh(minecraft.gui.screen());
		// Vanilla defaults are not one of your presets.
		QuickRebindClient.markApplied("");

		QuickRebindClient.LOGGER.info("Reset {} binds to default", changed);
		return changed;
	}

	/** The binds as they were just before the last apply, or null if there hasn't been one. */
	public static Preset undoSnapshot() {
		Preset preset = JsonStore.loadOrNull(SharedPaths.undo(), Preset.class);
		return preset == null ? null : preset.sanitise();
	}

	private static void snapshotForUndo(Options options) {
		Preset snapshot = Preset.of("Before last apply", capture(options), QuickRebindClient.gameVersion());
		JsonStore.save(SharedPaths.undo(), snapshot);
	}
}
