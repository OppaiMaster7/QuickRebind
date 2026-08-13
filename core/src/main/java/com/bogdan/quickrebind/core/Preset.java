package com.bogdan.quickrebind.core;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One saved set of keybinds.
 *
 * <p>Binds are stored as {@code identifier -> bound key}, e.g.
 * {@code "key.sprint" -> "key.keyboard.left.control"}. Keying off the
 * identifier rather than a slot number is what lets a preset survive the trip
 * between a vanilla install and a modpack, or between 1.8.9 and 26.2: a bind
 * the current install has never heard of (say {@code key.jei.showRecipe}, or a
 * key that only exists on newer versions) is simply left in the file untouched,
 * ready for the next time you launch something that owns it.
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
	public Map<String, String> binds = new TreeMap<String, String>();

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
		preset.binds = new TreeMap<String, String>(binds);
		return preset;
	}

	/** Fills in anything a hand-edited or foreign file left out. */
	public Preset sanitise() {
		if (isBlank(id)) {
			id = UUID.randomUUID().toString();
		}

		if (isBlank(name)) {
			name = "Unnamed";
		}

		name = name.trim();

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
		Map<String, String> cleaned = new TreeMap<String, String>();

		if (binds != null) {
			for (Map.Entry<String, String> entry : binds.entrySet()) {
				if (!isBlank(entry.getKey()) && !isBlank(entry.getValue())) {
					cleaned.put(entry.getKey(), entry.getValue());
				}
			}
		}

		binds = cleaned;
		return this;
	}

	public int size() {
		return binds == null ? 0 : binds.size();
	}

	static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
