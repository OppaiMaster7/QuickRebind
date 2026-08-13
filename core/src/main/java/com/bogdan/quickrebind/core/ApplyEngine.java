package com.bogdan.quickrebind.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deciding what a preset should do to a set of binds. Version-independent, so
 * the rules can't drift between the 1.8.9 build and the 26.2 one.
 */
public final class ApplyEngine {
	private ApplyEngine() {
	}

	/** Snapshots the binds as {@code id -> canonical key}. */
	public static Map<String, String> capture(List<? extends BindHandle> binds) {
		Map<String, String> out = new TreeMap<>();

		for (BindHandle bind : binds) {
			out.put(bind.id(), bind.currentKey());
		}

		return out;
	}

	public static ApplyResult apply(List<? extends BindHandle> binds, Preset preset, MissingBindPolicy policy) {
		int rebound = 0;
		int alreadyCorrect = 0;
		int resetToDefault = 0;
		int unreadable = 0;
		Set<String> installed = new HashSet<>();

		for (BindHandle bind : binds) {
			String id = bind.id();
			installed.add(id);
			String wanted = preset.binds.get(id);

			if (wanted == null) {
				// The preset says nothing about this bind. Leaving it alone is
				// what stops a vanilla preset from wiping someone's modded keys.
				if (policy == MissingBindPolicy.RESET_TO_DEFAULT && !bind.isDefault()) {
					bind.setKey(bind.defaultKey());
					resetToDefault++;
				}

				continue;
			}

			if (wanted.equals(bind.currentKey())) {
				alreadyCorrect++;
				continue;
			}

			if (bind.setKey(wanted)) {
				rebound++;
			} else {
				unreadable++;
			}
		}

		// Entries belonging to mods (or newer versions) that aren't here. They
		// stay in the preset file untouched, which is the whole reason a preset
		// can round-trip through a vanilla install without losing anything.
		int notInstalled = 0;

		for (String id : preset.binds.keySet()) {
			if (!installed.contains(id)) {
				notInstalled++;
			}
		}

		return new ApplyResult(rebound, alreadyCorrect, resetToDefault, unreadable, notInstalled,
				conflicts(binds));
	}

	/** How many binds share a key with at least one other bind. */
	public static int conflicts(List<? extends BindHandle> binds) {
		Map<String, Integer> counts = new HashMap<>();

		for (BindHandle bind : binds) {
			if (!bind.isUnbound()) {
				counts.merge(bind.currentKey(), 1, (a, b) -> a + b);
			}
		}

		int conflicted = 0;

		for (BindHandle bind : binds) {
			if (!bind.isUnbound()) {
				Integer count = counts.get(bind.currentKey());

				if (count != null && count > 1) {
					conflicted++;
				}
			}
		}

		return conflicted;
	}

	/** Puts every bind back on its shipped default. */
	public static int resetAllToDefault(List<? extends BindHandle> binds) {
		int changed = 0;

		for (BindHandle bind : binds) {
			if (!bind.isDefault()) {
				bind.setKey(bind.defaultKey());
				changed++;
			}
		}

		return changed;
	}
}
