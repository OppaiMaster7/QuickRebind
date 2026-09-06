package com.bogdan.quickrebind.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Telling you what the quick-switch key just did.
 *
 * <p>A toast rather than the action bar, because toasts draw over open screens
 * too — and the switch key is meant to work from the controls screen and the
 * pause menu, not only while you are stood in the world.
 */
public final class Notify {
	private Notify() {
	}

	public static void toast(Minecraft minecraft, Component title, Component message) {
		// getToasts() here; 26.2 moved the manager onto Gui. The id enum is
		// SystemToastId from 1.21 and SystemToastIds before it.
		SystemToast.add(minecraft.getToasts(),
				SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message);
	}
}
