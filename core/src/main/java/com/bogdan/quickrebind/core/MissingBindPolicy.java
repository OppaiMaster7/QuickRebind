package com.bogdan.quickrebind.core;

/**
 * What to do with a bind the current install has but the preset says nothing
 * about — typically a modded bind when you apply a vanilla preset.
 *
 * <p>No display strings here: core can't reach a version's text classes. The
 * GUI on each platform maps these to its own translatable components.
 */
public enum MissingBindPolicy {
	/** Leave it exactly as it is. Safe default: applying never touches a bind it doesn't mention. */
	LEAVE,
	/** Put it back to its own default, so the result matches the preset exactly. */
	RESET_TO_DEFAULT
}
