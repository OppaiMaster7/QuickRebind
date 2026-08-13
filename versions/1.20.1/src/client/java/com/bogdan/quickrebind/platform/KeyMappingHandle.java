package com.bogdan.quickrebind.platform;

import com.bogdan.quickrebind.core.BindHandle;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

/**
 * Adapts 26.2's {@link KeyMapping} to core's {@link BindHandle}.
 *
 * <p>Nothing to translate here: from 1.13 onwards Minecraft already stores keys
 * as {@code key.keyboard.*} / {@code key.mouse.*} names, which is exactly the
 * canonical form core works in.
 */
public final class KeyMappingHandle implements BindHandle {
	private final KeyMapping mapping;

	public KeyMappingHandle(KeyMapping mapping) {
		this.mapping = mapping;
	}

	@Override
	public String id() {
		return mapping.getName();
	}

	@Override
	public String currentKey() {
		return mapping.saveString();
	}

	@Override
	public String defaultKey() {
		return mapping.getDefaultKey().getName();
	}

	@Override
	public boolean isDefault() {
		return mapping.isDefault();
	}

	@Override
	public boolean isUnbound() {
		return mapping.isUnbound();
	}

	@Override
	public boolean setKey(String canonicalKey) {
		InputConstants.Key key;

		try {
			key = InputConstants.getKey(canonicalKey);
		} catch (IllegalArgumentException e) {
			// A preset from a newer Minecraft can name a key this one lacks.
			// Leave the bind alone and let core count it as unreadable.
			return false;
		}

		mapping.setKey(key);
		return true;
	}
}
