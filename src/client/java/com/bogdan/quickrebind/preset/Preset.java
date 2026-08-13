package com.bogdan.quickrebind.preset;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One saved set of keybinds.
 *
 * <p>Binds are stored as {@code translation key -> bound key}, e.g.
 * {@code "key.sprint" -> "key.keyboard.left.control"}. Keying off the
 * translation key rather than a slot number is what lets a preset survive the
 * trip between a vanilla install and a modpack: a bind the current install has
 * never heard of (say {@code key.jei.showRecipe}) is simply left in the file
 * untouched, ready for the next time you launch the pack that owns it.
 */
public class Preset {
	/** Bumped only if the on-disk shape changes in a way old readers can't handle. */
	public static final int CURRENT_FORMAT = 1;

	public int formatVersion = CURRENT_FORMAT;
	public String id;
	public String name;
	public long createdAt;
	public long updatedAt;
	/** Purely informational — which Minecraft the preset was captured on. */
	public String gameVersion;
	public Map<String, String> binds = new TreeMap<>();

	/** File this preset was read from, so a rename can clean up after itself. Not serialised. */
	public transient String fileName;

	public Preset() {
	}

	public static Preset of(String name, Map<String, String> binds, String gameVersion) {
		Preset preset = new Preset();
		preset.id = UUID.randomUUID().toString();
		preset.name = name;
		preset.createdAt = System.currentTimeMillis();
		preset.updatedAt = preset.createdAt;
		preset.gameVersion = gameVersion;
		preset.binds = new TreeMap<>(binds);
		return preset;
	}

	/** Fills in anything a hand-edited or foreign file left out. */
	public Preset sanitise() {
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString();
		}

		if (name == null || name.isBlank()) {
			name = "Unnamed";
		}

		name = name.strip();

		if (name.length() > PresetStore.MAX_NAME_LENGTH) {
			name = name.substring(0, PresetStore.MAX_NAME_LENGTH);
		}

		if (createdAt <= 0) {
			createdAt = System.currentTimeMillis();
		}

		if (updatedAt <= 0) {
			updatedAt = createdAt;
		}

		// Gson hands back a LinkedTreeMap; re-wrap so writes stay sorted and so
		// null keys or values from a mangled file can't reach the apply code.
		Map<String, String> cleaned = new TreeMap<>();

		if (binds != null) {
			binds.forEach((key, value) -> {
				if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
					cleaned.put(key, value);
				}
			});
		}

		binds = cleaned;
		return this;
	}

	public int size() {
		return binds == null ? 0 : binds.size();
	}

	public Preset copyForImport() {
		Preset copy = new Preset();
		copy.id = UUID.randomUUID().toString();
		copy.name = name;
		copy.createdAt = System.currentTimeMillis();
		copy.updatedAt = copy.createdAt;
		copy.gameVersion = gameVersion;
		copy.binds = new TreeMap<>(binds);
		return copy;
	}
}
