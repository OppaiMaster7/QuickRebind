package com.bogdan.quickrebind.platform;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.network.chat.Component;

/**
 * Turning core's identifiers into something a person would recognise.
 *
 * <p>Core deals in {@code key.sprint} and {@code key.keyboard.left.control}
 * because those are stable across versions and installs. Nobody wants to read a
 * screenful of them, and conveniently both are already translatable: vanilla
 * ships the bind ids as lang keys, and every key knows its own display name.
 */
public final class KeyNames {
	private KeyNames() {
	}

	/**
	 * "Sprint" for {@code key.sprint}.
	 *
	 * <p>A bind belonging to a mod that isn't installed has no translation here,
	 * so it renders as the raw id — which is the most useful thing we could show
	 * about it anyway.
	 */
	public static Component bindLabel(String id) {
		return Component.translatable(id);
	}

	/** "Left Control" for {@code key.keyboard.left.control}. */
	public static Component keyLabel(String canonicalKey) {
		if (canonicalKey == null) {
			return Component.translatable("quickrebind.details.no_key");
		}

		try {
			return InputConstants.getKey(canonicalKey).getDisplayName();
		} catch (IllegalArgumentException e) {
			// A preset from a newer Minecraft naming a key this one lacks. Showing
			// the raw name beats showing nothing — it tells you what to go and bind.
			return Component.literal(canonicalKey);
		}
	}
}
