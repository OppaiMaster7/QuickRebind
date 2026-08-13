package com.bogdan.quickrebind.keys;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.bogdan.quickrebind.QuickRebindClient;
import com.bogdan.quickrebind.preset.Preset;
import com.bogdan.quickrebind.storage.JsonStore;
import com.bogdan.quickrebind.storage.SharedPaths;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Reads the live keybinds out of the game, and writes a preset back into them. */
public final class KeybindService {
	private KeybindService() {
	}

	/** Every bind the current install knows about, as {@code id -> bound key}. */
	public static Map<String, String> capture(Options options) {
		Map<String, String> binds = new TreeMap<>();

		for (KeyMapping mapping : options.keyMappings) {
			if (mapping != null) {
				binds.put(mapping.getName(), mapping.saveString());
			}
		}

		return binds;
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

		int rebound = 0;
		int alreadyCorrect = 0;
		int resetToDefault = 0;
		int unreadable = 0;
		Set<String> installed = new HashSet<>();

		for (KeyMapping mapping : options.keyMappings) {
			if (mapping == null) {
				continue;
			}

			String id = mapping.getName();
			installed.add(id);
			String wanted = preset.binds.get(id);

			if (wanted == null) {
				// The preset has nothing to say about this bind.
				if (policy == MissingBindPolicy.RESET_TO_DEFAULT && !mapping.isDefault()) {
					mapping.setKey(mapping.getDefaultKey());
					resetToDefault++;
				}

				continue;
			}

			InputConstants.Key key = parse(wanted);

			if (key == null) {
				unreadable++;
				continue;
			}

			if (mapping.saveString().equals(key.getName())) {
				alreadyCorrect++;
				continue;
			}

			mapping.setKey(key);
			rebound++;
		}

		// Entries for mods that aren't installed here. They stay in the file
		// untouched — that's what makes a preset survive a trip through vanilla.
		int notInstalled = 0;

		for (String id : preset.binds.keySet()) {
			if (!installed.contains(id)) {
				notInstalled++;
			}
		}

		KeyMapping.resetMapping();
		options.save();

		ApplyResult result = new ApplyResult(rebound, alreadyCorrect, resetToDefault,
				unreadable, notInstalled, conflicts(options));
		QuickRebindClient.LOGGER.info("Applied preset '{}': {}", preset.name, result);
		return result;
	}

	/** Puts every bind back to its own default. */
	public static int resetAllToDefault(Minecraft minecraft) {
		Options options = minecraft.options;
		snapshotForUndo(options);
		KeyMapping.releaseAll();

		int changed = 0;

		for (KeyMapping mapping : options.keyMappings) {
			if (mapping != null && !mapping.isDefault()) {
				mapping.setKey(mapping.getDefaultKey());
				changed++;
			}
		}

		KeyMapping.resetMapping();
		options.save();
		return changed;
	}

	/** How many binds currently share a key with at least one other bind. */
	public static int conflicts(Options options) {
		Map<String, Integer> counts = new HashMap<>();

		for (KeyMapping mapping : options.keyMappings) {
			if (mapping != null && !mapping.isUnbound()) {
				counts.merge(mapping.saveString(), 1, Integer::sum);
			}
		}

		int conflicted = 0;

		for (KeyMapping mapping : options.keyMappings) {
			if (mapping != null && !mapping.isUnbound()
					&& counts.getOrDefault(mapping.saveString(), 0) > 1) {
				conflicted++;
			}
		}

		return conflicted;
	}

	/** The binds as they were just before the last apply, or null if there hasn't been one. */
	public static Preset undoSnapshot() {
		Preset preset = JsonStore.loadOrNull(SharedPaths.undo(), Preset.class);
		return preset == null ? null : preset.sanitise();
	}

	/**
	 * Parses a saved key name such as {@code key.keyboard.left.control}.
	 *
	 * @return null if this Minecraft has never heard of that key
	 */
	public static InputConstants.Key parse(String saveString) {
		try {
			return InputConstants.getKey(saveString);
		} catch (IllegalArgumentException e) {
			QuickRebindClient.LOGGER.warn("Preset names an unknown key: {}", saveString);
			return null;
		}
	}

	private static void snapshotForUndo(Options options) {
		Preset snapshot = Preset.of("Before last apply", capture(options),
				QuickRebindClient.gameVersion());
		JsonStore.save(SharedPaths.undo(), snapshot);
	}
}
