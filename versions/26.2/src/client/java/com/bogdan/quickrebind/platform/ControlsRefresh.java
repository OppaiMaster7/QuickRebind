package com.bogdan.quickrebind.platform;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import com.bogdan.quickrebind.QuickRebindClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;

/**
 * Makes the vanilla Key Binds screen notice that a preset moved its keys.
 *
 * <p>Each row on that screen caches the key name in its button when the list is
 * built, and vanilla only refreshes it when vanilla itself rebinds something.
 * We move the KeyMapping objects underneath, so without this the screen keeps
 * showing the old keys until you leave it and come back. The binds are already
 * live either way — but nobody believes that while the screen says otherwise,
 * and the obvious reading is that the mod did nothing.
 */
public final class ControlsRefresh {
	private static WeakReference<Screen> lastSeen = new WeakReference<Screen>(null);
	private static Class<?> cachedOwner;
	private static Field cachedField;

	private ControlsRefresh() {
	}

	/**
	 * Refreshes {@code screen} if it is one showing a list of key binds.
	 *
	 * @return whether a list was found. The self-test asserts this, because a
	 *         silent no-op is exactly how this would rot on the next version
	 *         without anybody noticing until a player did.
	 */
	public static boolean refresh(Screen screen) {
		KeyBindsList list = listOf(screen);

		if (list == null) {
			return false;
		}

		list.refreshEntries();

		return true;
	}

	/**
	 * Refreshes the current screen whenever it changes.
	 *
	 * <p>Applying from our own screen leaves the Key Binds screen stale
	 * underneath it, and vanilla does not rebuild that screen when you press
	 * Done — it is the same object, still holding the labels it built earlier.
	 * So the moment it comes back into view is the moment to refresh it.
	 */
	public static void onScreenChanged(Minecraft client) {
		Screen current = client.gui.screen();

		if (current == lastSeen.get()) {
			return;
		}

		lastSeen = new WeakReference<Screen>(current);
		refresh(current);
	}

	/**
	 * The screen's key bind list, or null if it hasn't got one.
	 *
	 * <p>Found by field <em>type</em> rather than by name on purpose. Field
	 * names are not remapped when the mod is built, so
	 * {@code getDeclaredField("keyBindsList")} would work in the dev client
	 * and throw in the jar people download; a class literal is remapped, so
	 * matching on {@code KeyBindsList.class} works in both.
	 */
	private static KeyBindsList listOf(Screen screen) {
		if (screen == null) {
			return null;
		}

		try {
			Field field = fieldFor(screen.getClass());
			return field == null ? null : (KeyBindsList) field.get(screen);
		} catch (ReflectiveOperationException e) {
			QuickRebindClient.LOGGER.warn("Could not read the key bind list to refresh it", e);
			return null;
		} catch (RuntimeException e) {
			QuickRebindClient.LOGGER.warn("Could not read the key bind list to refresh it", e);
			return null;
		}
	}

	/** Cached per screen class, since this runs on every screen change. */
	private static Field fieldFor(Class<?> type) {
		if (type == cachedOwner) {
			return cachedField;
		}

		Field found = null;

		for (Class<?> owner = type; owner != null && found == null; owner = owner.getSuperclass()) {
			for (Field field : owner.getDeclaredFields()) {
				if (KeyBindsList.class.isAssignableFrom(field.getType())) {
					field.setAccessible(true);
					found = field;
					break;
				}
			}
		}

		cachedOwner = type;
		cachedField = found;
		return found;
	}
}
