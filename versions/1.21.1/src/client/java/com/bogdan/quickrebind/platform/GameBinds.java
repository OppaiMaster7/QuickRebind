package com.bogdan.quickrebind.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.bogdan.quickrebind.QuickRebindClient;
import com.bogdan.quickrebind.core.ApplyEngine;
import com.bogdan.quickrebind.core.ApplyResult;
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

	/**
	 * Writes {@code preset} into the live keybinds and saves options.txt.
	 *
	 * <p>Always snapshots the current binds to the undo file first, so a wrong
	 * click is one button away from being reversed.
	 */
	public static ApplyResult apply(Minecraft minecraft, Preset preset, MissingBindPolicy policy) {
		Options options = minecraft.options;
		snapshotForUndo(options);

		// Anything held down right now would otherwise stay "pressed" on its old key.
		KeyMapping.releaseAll();

		ApplyResult result = ApplyEngine.apply(handles(options), preset, policy);

		KeyMapping.resetMapping();
		options.save();

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
