package com.bogdan.quickrebind.keys;

import net.minecraft.network.chat.Component;

/**
 * What to do with a bind the current install has but the preset says nothing
 * about — typically a modded bind when you apply a vanilla preset.
 */
public enum MissingBindPolicy {
	/** Leave it exactly as it is. Safe default: applying a preset never touches a bind it doesn't mention. */
	LEAVE("quickrebind.policy.leave"),
	/** Put it back to the mod's own default, so the result matches the preset exactly. */
	RESET_TO_DEFAULT("quickrebind.policy.reset");

	private final String translationKey;

	MissingBindPolicy(String translationKey) {
		this.translationKey = translationKey;
	}

	public Component label() {
		return Component.translatable(translationKey);
	}
}
