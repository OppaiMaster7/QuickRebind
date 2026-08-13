package com.bogdan.quickrebind.keys;

/**
 * What actually happened when a preset was applied.
 *
 * @param rebound          binds moved to the key the preset asked for
 * @param alreadyCorrect   binds that were already on the right key
 * @param resetToDefault   binds the preset didn't mention, put back to default
 * @param unreadable       entries naming a key this Minecraft doesn't know
 * @param notInstalled     entries for binds this install doesn't have (kept in the file)
 * @param conflicts        binds now sharing a key with another bind
 */
public record ApplyResult(
		int rebound,
		int alreadyCorrect,
		int resetToDefault,
		int unreadable,
		int notInstalled,
		int conflicts) {

	public int changed() {
		return rebound + resetToDefault;
	}
}
