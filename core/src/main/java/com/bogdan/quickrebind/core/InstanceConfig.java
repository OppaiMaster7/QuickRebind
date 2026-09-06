package com.bogdan.quickrebind.core;

/**
 * The settings that belong to one Minecraft install rather than to you.
 *
 * <p>Presets are deliberately shared across every instance on the machine — that
 * is the entire point of the mod — and so are your preferences. These two are
 * different: "which preset should <em>this</em> instance boot with" only makes
 * sense per install, because the answer for your PvP instance is not the answer
 * for the modpack you play on Sundays. Keeping them in the shared folder would
 * allow exactly one answer for the whole computer.
 *
 * <p>So this file lives in the instance's own config folder, next to every other
 * mod's config, and is the one piece of QuickRebind state that does not travel.
 */
public class InstanceConfig {
	/** Id of a preset to apply every time this instance launches, or blank for none. */
	public String autoApplyPresetId = "";

	/**
	 * Id of the preset most recently applied here.
	 *
	 * <p>Feeds the marker in the list and the starting point for the quick-switch
	 * key. Blank after a manual edit in the controls screen would be more honest,
	 * but we cannot see those, so treat it as "what you last asked for" rather
	 * than "what is definitely bound right now".
	 */
	public String lastAppliedId = "";

	public void sanitise() {
		if (autoApplyPresetId == null) {
			autoApplyPresetId = "";
		}

		if (lastAppliedId == null) {
			lastAppliedId = "";
		}
	}
}
