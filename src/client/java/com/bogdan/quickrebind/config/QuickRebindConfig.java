package com.bogdan.quickrebind.config;

import com.bogdan.quickrebind.keys.MissingBindPolicy;

/**
 * Settings. Lives in the shared folder next to the presets, so it follows you
 * between instances the same way they do.
 */
public class QuickRebindConfig {
	/** What to do with binds the preset doesn't mention. */
	public MissingBindPolicy missingBindPolicy = MissingBindPolicy.LEAVE;

	/** Ask before overwriting the current binds. */
	public boolean confirmBeforeApply = true;

	/** Id of a preset to apply on every launch, or blank for none. */
	public String autoApplyPresetId = "";

	/** Show the QuickRebind button on the Key Binds screen. */
	public boolean buttonInKeyBinds = true;

	/** Show it on the Controls screen too. */
	public boolean buttonInControls = true;

	public void sanitise() {
		if (missingBindPolicy == null) {
			missingBindPolicy = MissingBindPolicy.LEAVE;
		}

		if (autoApplyPresetId == null) {
			autoApplyPresetId = "";
		}
	}
}
