package com.bogdan.quickrebind.core;

/**
 * One keybind, as core sees it.
 *
 * <p>Every supported Minecraft has some class for this — {@code KeyMapping} on
 * modern versions, {@code KeyBinding} on 1.8.9 — and they disagree about
 * almost everything, including whether a key is a string or an int. Each
 * platform adapts its own type to this interface and core stops caring.
 *
 * <p>Keys crossing this boundary are always in the canonical modern form
 * ({@code key.keyboard.left.control}, {@code key.mouse.left}). Versions from
 * 1.13 on store exactly that already; 1.8.9 stores LWJGL2 integers instead, and
 * translating them is that adapter's problem, not core's.
 */
public interface BindHandle {
	/** The bind's stable identifier, e.g. {@code key.sprint}. */
	String id();

	/** Currently bound key, canonical form. */
	String currentKey();

	/** The key this bind ships with, canonical form. */
	String defaultKey();

	/** Whether this bind is on its shipped default. */
	boolean isDefault();

	/** Whether this bind is set to nothing. */
	boolean isUnbound();

	/**
	 * Rebinds to {@code canonicalKey}.
	 *
	 * @return false if this Minecraft has no such key, in which case the bind
	 *         must be left exactly as it was
	 */
	boolean setKey(String canonicalKey);
}
