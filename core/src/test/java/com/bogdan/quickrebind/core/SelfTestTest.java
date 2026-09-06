package com.bogdan.quickrebind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * The in-game self-test, tested on the ground.
 *
 * <p>A harness that reports PASS whatever happens is worse than no harness, so
 * these run it against a good adapter and against several broken ones and check
 * it can tell them apart.
 */
class SelfTestTest {
	@RegisterExtension
	final SharedFolder folder = new SharedFolder();

	/** The scratch key {@link SelfTest} parks a bind on while it works. */
	private static final String SCRATCH = "key.keyboard.f24";

	private static Set<String> keys() {
		Set<String> keys = new HashSet<>(FakeInstall.COMMON_KEYS);
		keys.add(SCRATCH);
		return keys;
	}

	private static FakeInstall goodInstall() {
		return new FakeInstall(keys())
				.withMoved("key.sprint", "key.keyboard.left.control", "key.keyboard.v")
				.with("key.jump", "key.keyboard.space")
				.with("key.drop", "key.keyboard.q")
				.with("key.inventory", "key.keyboard.e")
				.with("key.attack", "key.mouse.left");
	}

	@Test
	@DisplayName("a working adapter passes")
	void healthyAdapterPasses() {
		SelfTest.Report report = SelfTest.run(goodInstall().handles(), "1.21.1");

		assertTrue(report.passed(), report.text());
		assertEquals(5, report.bindCount);
		assertTrue(report.text().contains("RESULT: PASS"));
	}

	@Test
	@DisplayName("the run leaves the binds exactly as it found them")
	void runRestoresTheBinds() {
		FakeInstall install = goodInstall();
		List<BindHandle> handles = install.handles();
		Map<String, String> before = ApplyEngine.capture(handles);

		SelfTest.run(handles, "1.21.1");

		assertEquals(before, ApplyEngine.capture(handles));
	}

	@Test
	@DisplayName("an adapter with no binds fails rather than passing vacuously")
	void emptyInstallFails() {
		SelfTest.Report report = SelfTest.run(new ArrayList<BindHandle>(), "1.21.1");

		assertFalse(report.passed());
		assertTrue(report.text().contains("no binds at all"), report.text());
	}

	@Test
	@DisplayName("an adapter reporting keys in the wrong shape is caught")
	void nonCanonicalKeysAreCaught() {
		// What a botched 1.8.9 adapter would do: hand back the display name
		// instead of the canonical one. Every preset would silently mismatch.
		SelfTest.Report report = SelfTest.run(
				Arrays.<BindHandle>asList(new BrokenBind("key.sprint", "LEFT CONTROL")), "1.8.9");

		assertFalse(report.passed());
		assertTrue(report.text().contains("canonical form"), report.text());
	}

	@Test
	@DisplayName("an adapter whose ids are not bind ids is caught")
	void nonCanonicalIdsAreCaught() {
		SelfTest.Report report = SelfTest.run(
				Arrays.<BindHandle>asList(new BrokenBind("Sprint", "key.keyboard.left.control")), "1.8.9");

		assertFalse(report.passed());
		assertTrue(report.text().contains("canonical form"), report.text());
	}

	@Test
	@DisplayName("an adapter that ignores setKey is caught")
	void adapterThatSilentlyIgnoresSetKeyIsCaught() {
		// The nastiest failure: setKey returns true and does nothing, so the mod
		// reports success and the player's keys never move.
		SelfTest.Report report = SelfTest.run(
				Arrays.<BindHandle>asList(
						new DeafBind("key.sprint", "key.keyboard.left.control"),
						new DeafBind("key.jump", "key.keyboard.space")),
				"1.21.1");

		assertFalse(report.passed(), report.text());
	}

	@Test
	@DisplayName("an adapter that accepts a key the version cannot have is caught")
	void adapterThatAcceptsAnyKeyIsCaught() {
		SelfTest.Report report = SelfTest.run(
				Arrays.<BindHandle>asList(
						new CredulousBind("key.sprint", "key.keyboard.left.control"),
						new CredulousBind("key.jump", "key.keyboard.space")),
				"1.21.1");

		assertFalse(report.passed(), report.text());
		assertTrue(report.text().contains("unreadable") || report.text().contains("refused"), report.text());
	}

	// ------------------------------------------------------------------ screens

	@Test
	@DisplayName("screens that open are reported by name")
	void workingScreensPass() {
		SelfTest.Report report = SelfTest.run(goodInstall().handles(), "1.21.1",
				Arrays.asList(screen("the preset list screen opens", () -> { }),
						screen("the settings screen opens", () -> { })));

		assertTrue(report.passed(), report.text());
		assertTrue(report.text().contains("the preset list screen opens"), report.text());
		assertTrue(report.text().contains("the settings screen opens"), report.text());
	}

	@Test
	@DisplayName("a screen that throws is reported, and the rest of the run continues")
	void brokenScreenIsCaught() {
		SelfTest.Report report = SelfTest.run(goodInstall().handles(), "1.21.1",
				Arrays.asList(screen("the settings screen opens", () -> {
					throw new IllegalStateException("boom");
				})));

		assertFalse(report.passed());
		assertTrue(report.text().contains("the settings screen opens — "), report.text());
		// The keybind checks still ran, rather than being lost with the screen.
		assertTrue(report.text().contains("capture then apply is a no-op"), report.text());
	}

	@Test
	@DisplayName("a screen failing with a LinkageError is caught, not propagated")
	void screenLinkageErrorIsCaught() {
		// What a GUI ported against the wrong version actually throws.
		SelfTest.Report report = SelfTest.run(goodInstall().handles(), "1.21.1",
				Arrays.asList(screen("the preset list screen opens", () -> {
					throw new NoSuchMethodError("Button.builder");
				})));

		assertFalse(report.passed());
		assertTrue(report.text().contains("NoSuchMethodError"), report.text());
	}

	private interface Body {
		void run() throws Exception;
	}

	private static SelfTest.ScreenCheck screen(String name, Body body) {
		return new SelfTest.ScreenCheck() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public void open() throws Exception {
				body.run();
			}
		};
	}

	// ------------------------------------------------------------ broken doubles

	/** Reports whatever it was given, canonical or not. */
	private static class BrokenBind implements BindHandle {
		private final String id;
		private String key;

		BrokenBind(String id, String key) {
			this.id = id;
			this.key = key;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public String currentKey() {
			return key;
		}

		@Override
		public String defaultKey() {
			return key;
		}

		@Override
		public boolean isDefault() {
			return true;
		}

		@Override
		public boolean isUnbound() {
			return false;
		}

		@Override
		public boolean setKey(String canonicalKey) {
			key = canonicalKey;
			return true;
		}
	}

	/** Says yes to every rebind and performs none of them. */
	private static final class DeafBind extends BrokenBind {
		DeafBind(String id, String key) {
			super(id, key);
		}

		@Override
		public boolean setKey(String canonicalKey) {
			return true;
		}
	}

	/** Accepts keys this version does not have, instead of refusing them. */
	private static final class CredulousBind extends BrokenBind {
		CredulousBind(String id, String key) {
			super(id, key);
		}
	}
}
