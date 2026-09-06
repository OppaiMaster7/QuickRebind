package com.bogdan.quickrebind.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A stand-in for one Minecraft install's set of keybinds.
 *
 * <p>The point of core is that it never sees a {@code KeyMapping}, so the whole
 * apply/diff/conflict half can be tested against a handful of these instead of
 * a running game. An install here is two things the real thing also has: the
 * binds it knows about, and the set of key names its version can parse — which
 * is what makes "apply a 26.2 preset on 1.19.2" something a unit test can
 * actually reproduce.
 */
final class FakeInstall {
	/** Keys every version from 1.13 on understands. Enough for the tests that don't care. */
	static final Set<String> COMMON_KEYS = new HashSet<>(Arrays.asList(
			"key.keyboard.unknown",
			"key.keyboard.a", "key.keyboard.b", "key.keyboard.c", "key.keyboard.d",
			"key.keyboard.e", "key.keyboard.f", "key.keyboard.g", "key.keyboard.q",
			"key.keyboard.r", "key.keyboard.s", "key.keyboard.v", "key.keyboard.w",
			"key.keyboard.x", "key.keyboard.z",
			"key.keyboard.left.control", "key.keyboard.left.shift", "key.keyboard.space",
			"key.keyboard.tab", "key.keyboard.escape", "key.keyboard.f3",
			"key.mouse.left", "key.mouse.right", "key.mouse.middle"));

	private final List<Bind> binds = new ArrayList<>();
	private final Set<String> knownKeys;

	FakeInstall() {
		this(COMMON_KEYS);
	}

	FakeInstall(Set<String> knownKeys) {
		this.knownKeys = knownKeys;
	}

	/** Adds a bind sitting on its own default. */
	FakeInstall with(String id, String defaultKey) {
		binds.add(new Bind(id, defaultKey, defaultKey));
		return this;
	}

	/** Adds a bind the player has already moved off its default. */
	FakeInstall withMoved(String id, String defaultKey, String currentKey) {
		binds.add(new Bind(id, defaultKey, currentKey));
		return this;
	}

	List<BindHandle> handles() {
		return new ArrayList<BindHandle>(binds);
	}

	/** The key {@code id} is on right now, or null if this install has no such bind. */
	String keyOf(String id) {
		for (Bind bind : binds) {
			if (bind.id.equals(id)) {
				return bind.current;
			}
		}

		return null;
	}

	/**
	 * One keybind. Mirrors what the real adapters do, including the part that
	 * matters most: {@link #setKey} refuses a key this version has never heard
	 * of and leaves the bind exactly where it was.
	 */
	private final class Bind implements BindHandle {
		private final String id;
		private final String defaultKey;
		private String current;

		Bind(String id, String defaultKey, String current) {
			this.id = id;
			this.defaultKey = defaultKey;
			this.current = current;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public String currentKey() {
			return current;
		}

		@Override
		public String defaultKey() {
			return defaultKey;
		}

		@Override
		public boolean isDefault() {
			return current.equals(defaultKey);
		}

		@Override
		public boolean isUnbound() {
			return current.equals("key.keyboard.unknown");
		}

		@Override
		public boolean setKey(String canonicalKey) {
			if (!knownKeys.contains(canonicalKey)) {
				return false;
			}

			current = canonicalKey;
			return true;
		}
	}
}
