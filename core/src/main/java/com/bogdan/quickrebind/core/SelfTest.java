package com.bogdan.quickrebind.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Drives the whole mod against a real, running Minecraft and reports what broke.
 *
 * <p>The unit tests cover core against a fake install, which is most of the
 * logic but deliberately none of the part that differs per version: whether
 * <em>this</em> Minecraft's keybind class actually answers {@code getName},
 * {@code saveString} and {@code setKey} the way the adapter assumes, and
 * whether this version's screens still build. That can only be found out inside
 * the game, and finding it out by hand on every supported version is how it
 * stops getting done.
 *
 * <p>So each version's client entrypoint calls this when
 * {@code -Dquickrebind.selftest} is set: it opens each screen, then exercises
 * capture, diff, apply, the missing-key path and a disk round trip against the
 * live binds, puts everything back exactly as it found it, and hands back a
 * report the launcher can print and exit on. No world is loaded and no human is
 * needed, which is what makes running it on every version before a release
 * realistic.
 *
 * <p>Lives in core rather than in a version directory so there is one copy of
 * it, and so a new port inherits the checks instead of being trusted.
 */
public final class SelfTest {
	/** A key every version from 1.13 on has, used as a scratch target. */
	private static final String SCRATCH_KEY = "key.keyboard.f24";
	/** A key no Minecraft has, for the "preset from a newer version" path. */
	private static final String IMPOSSIBLE_KEY = "key.keyboard.does.not.exist";

	private final List<String> failures = new ArrayList<String>();
	private final List<String> checks = new ArrayList<String>();
	private final String gameVersion;

	private SelfTest(String gameVersion) {
		this.gameVersion = gameVersion;
	}

	/**
	 * One of the mod's screens, handed over by the platform to be opened.
	 *
	 * <p>The GUI is the half that genuinely differs between Minecraft versions,
	 * so a port that compiles has still only proved half of itself: a button
	 * helper can change shape, a widget list can move, a layout call can throw
	 * on the version it was not written against. Opening each screen runs its
	 * real construction and layout, which is where those land.
	 *
	 * <p>Core can't name a screen class, so each version supplies these.
	 */
	public interface ScreenCheck {
		/** How the report should refer to it. */
		String name();

		/** Builds the screen and puts it on display. Anything thrown is a failure. */
		void open() throws Exception;
	}

	/** What the run found. */
	public static final class Report {
		public final String gameVersion;
		public final int bindCount;
		public final List<String> checks;
		public final List<String> failures;

		Report(String gameVersion, int bindCount, List<String> checks, List<String> failures) {
			this.gameVersion = gameVersion;
			this.bindCount = bindCount;
			this.checks = checks;
			this.failures = failures;
		}

		public boolean passed() {
			return failures.isEmpty();
		}

		/** A plain-text report, for the log and for the file the runner reads. */
		public String text() {
			StringBuilder out = new StringBuilder();
			out.append("QuickRebind self-test — Minecraft ").append(gameVersion).append('\n');
			out.append(bindCount).append(" binds seen\n\n");

			for (String check : checks) {
				out.append("  PASS  ").append(check).append('\n');
			}

			for (String failure : failures) {
				out.append("  FAIL  ").append(failure).append('\n');
			}

			out.append('\n').append(passed()
					? "RESULT: PASS"
					: "RESULT: FAIL (" + failures.size() + ")").append('\n');
			return out.toString();
		}
	}

	/**
	 * Runs every check against the live binds and restores them afterwards.
	 *
	 * <p>Restoring is not politeness — the dev client shares nothing with a real
	 * install, but a half-applied run would make the next check on the same
	 * launch meaningless.
	 */
	public static Report run(List<? extends BindHandle> binds, String gameVersion) {
		return run(binds, gameVersion, Collections.<ScreenCheck>emptyList());
	}

	public static Report run(List<? extends BindHandle> binds, String gameVersion,
			List<ScreenCheck> screens) {
		SelfTest test = new SelfTest(gameVersion);
		Map<String, String> original = ApplyEngine.capture(binds);

		try {
			test.checkScreensOpen(screens);
			test.checkTheBindsLookRight(binds);
			test.checkCaptureAndApplyRoundTrip(binds, original);
			test.checkOnlyDifferingBindsMove(binds, original);
			test.checkUnknownKeyIsRefused(binds);
			test.checkEntriesThisVersionLacksAreKept(binds, original);
			test.checkDiffAgreesWithApply(binds, original);
			test.checkPresetSurvivesTheDisk(original);
			test.checkShareCodeRoundTrip(original);
		} catch (RuntimeException e) {
			test.fail("self-test threw " + e);
		} finally {
			restore(binds, original);
			test.verifyRestored(binds, original);
		}

		return new Report(gameVersion, original.size(), test.checks, test.failures);
	}

	// ----------------------------------------------------------------- checks

	/**
	 * Opens every screen the platform handed over.
	 *
	 * <p>Runs first, and catches {@link Throwable} rather than
	 * {@link RuntimeException}: a screen built against the wrong version tends
	 * to fail with a {@code NoSuchMethodError} or a {@code LinkageError}, and
	 * those are exactly the ones worth reporting rather than letting kill the
	 * run before the keybind checks get a turn.
	 */
	private void checkScreensOpen(List<ScreenCheck> screens) {
		for (ScreenCheck screen : screens) {
			try {
				screen.open();
				// The name is the whole sentence: core has no business deciding
				// that every one of these is a screen that "opens".
				pass(screen.name());
			} catch (Throwable t) {
				fail(screen.name() + " — " + t);
			}
		}
	}

	/** The adapter has to hand core canonical ids and keys, or nothing else works. */
	private void checkTheBindsLookRight(List<? extends BindHandle> binds) {
		if (binds.isEmpty()) {
			fail("no binds at all — the adapter found nothing in Options");
			return;
		}

		int odd = 0;

		for (BindHandle bind : binds) {
			if (Preset.isBlank(bind.id()) || !bind.id().startsWith("key.")) {
				odd++;
				continue;
			}

			String key = bind.currentKey();

			// Every version from 1.13 stores keys in this shape. An adapter
			// handing back "LEFT CONTROL" or a bare integer would break every
			// preset silently rather than loudly, so it is worth asserting.
			if (Preset.isBlank(key)
					|| !(key.startsWith("key.keyboard.") || key.startsWith("key.mouse."))) {
				odd++;
			}
		}

		if (odd > 0) {
			fail(odd + " binds have an id or key that is not in canonical form");
		} else {
			pass(binds.size() + " binds report canonical ids and keys");
		}

		if (Preset.isBlank(binds.get(0).defaultKey())) {
			fail("defaultKey() is blank — reset and the missing-bind policy both depend on it");
		} else {
			pass("defaultKey() answers");
		}
	}

	/** Capture then apply the capture: nothing should move. */
	private void checkCaptureAndApplyRoundTrip(List<? extends BindHandle> binds, Map<String, String> original) {
		Preset same = Preset.of("self-test capture", original, gameVersion);
		ApplyResult result = ApplyEngine.apply(binds, same, MissingBindPolicy.LEAVE);

		if (result.rebound != 0) {
			fail("applying a capture of the current binds moved " + result.rebound + " of them");
		} else if (result.alreadyCorrect != original.size()) {
			fail("applying a capture recognised " + result.alreadyCorrect + " of " + original.size() + " binds");
		} else {
			pass("capture then apply is a no-op");
		}
	}

	/** Move one bind and check exactly one bind moved. */
	private void checkOnlyDifferingBindsMove(List<? extends BindHandle> binds, Map<String, String> original) {
		BindHandle victim = firstMovable(binds);

		if (victim == null) {
			fail("no bind could be moved to " + SCRATCH_KEY + " — is that key missing on this version?");
			return;
		}

		Map<String, String> wanted = new TreeMap<String, String>(original);
		wanted.put(victim.id(), SCRATCH_KEY);

		ApplyResult result = ApplyEngine.apply(binds,
				Preset.of("self-test one move", wanted, gameVersion), MissingBindPolicy.LEAVE);

		if (result.rebound != 1) {
			fail("moving one bind reported " + result.rebound + " rebound, expected 1");
		} else if (!SCRATCH_KEY.equals(victim.currentKey())) {
			fail("setKey said yes but " + victim.id() + " is on " + victim.currentKey());
		} else {
			pass("a one-bind change moves exactly one bind (" + victim.id() + ")");
		}

		restore(binds, original);
	}

	/** A preset from a newer Minecraft names keys this one lacks; they must be refused. */
	private void checkUnknownKeyIsRefused(List<? extends BindHandle> binds) {
		BindHandle victim = binds.get(0);
		String before = victim.currentKey();

		Map<String, String> wanted = new LinkedHashMap<String, String>();
		wanted.put(victim.id(), IMPOSSIBLE_KEY);

		ApplyResult result = ApplyEngine.apply(binds,
				Preset.of("self-test bad key", wanted, gameVersion), MissingBindPolicy.LEAVE);

		if (result.unreadable != 1) {
			fail("a key this version cannot have was not reported unreadable");
		} else if (!before.equals(victim.currentKey())) {
			fail("a refused key still changed " + victim.id() + " from " + before + " to " + victim.currentKey());
		} else {
			pass("a key this version lacks is refused and the bind left alone");
		}
	}

	/** Entries for binds this install has never heard of are counted, not applied. */
	private void checkEntriesThisVersionLacksAreKept(List<? extends BindHandle> binds, Map<String, String> original) {
		Map<String, String> wanted = new TreeMap<String, String>(original);
		wanted.put("key.somemod.nothere", "key.keyboard.k");
		wanted.put("key.othermod.alsonothere", "key.keyboard.j");

		ApplyResult result = ApplyEngine.apply(binds,
				Preset.of("self-test foreign", wanted, gameVersion), MissingBindPolicy.LEAVE);

		if (result.notInstalled != 2) {
			fail("expected 2 not-installed entries, got " + result.notInstalled);
		} else {
			pass("binds from another install are counted and left in the preset");
		}
	}

	/** Whatever the details screen promises, apply has to deliver. */
	private void checkDiffAgreesWithApply(List<? extends BindHandle> binds, Map<String, String> original) {
		BindHandle victim = firstMovable(binds);

		if (victim == null) {
			return;
		}

		Map<String, String> wanted = new TreeMap<String, String>(original);
		wanted.put(victim.id(), SCRATCH_KEY);
		Preset preset = Preset.of("self-test diff", wanted, gameVersion);

		int predicted = 0;

		for (BindDiff line : ApplyEngine.diff(binds, preset, MissingBindPolicy.LEAVE)) {
			if (line.moves()) {
				predicted++;
			}
		}

		ApplyResult result = ApplyEngine.apply(binds, preset, MissingBindPolicy.LEAVE);

		if (predicted != result.changed()) {
			fail("the details screen predicted " + predicted + " changes but apply made " + result.changed());
		} else {
			pass("diff predicts exactly what apply does");
		}

		restore(binds, original);
	}

	/** The preset has to survive being written and read back, which is how it travels. */
	private void checkPresetSurvivesTheDisk(Map<String, String> original) {
		Preset saved = Preset.of("QuickRebind self-test", original, gameVersion);

		if (!PresetStore.save(saved)) {
			fail("could not write a preset to " + SharedPaths.presets());
			return;
		}

		Preset reread = null;

		for (Preset preset : PresetStore.list()) {
			if (preset.id.equals(saved.id)) {
				reread = preset;
			}
		}

		if (reread == null) {
			fail("a preset written to " + SharedPaths.presets() + " could not be read back");
		} else if (!reread.binds.equals(saved.binds)) {
			fail("a preset changed on the way through the disk: "
					+ saved.size() + " binds out, " + reread.size() + " back");
		} else {
			pass("a " + saved.size() + "-bind preset round-trips through the shared folder");
			PresetStore.delete(reread);
		}
	}

	private void checkShareCodeRoundTrip(Map<String, String> original) {
		Preset preset = Preset.of("QuickRebind self-test", original, gameVersion);

		try {
			Preset decoded = ShareCode.decode(ShareCode.encode(preset));

			if (!decoded.binds.equals(preset.binds)) {
				fail("a share code did not decode back to the same binds");
			} else {
				pass("a share code round-trips " + preset.size() + " binds");
			}
		} catch (RuntimeException e) {
			fail("share code round trip threw " + e);
		}
	}

	private void verifyRestored(List<? extends BindHandle> binds, Map<String, String> original) {
		if (!ApplyEngine.capture(binds).equals(original)) {
			fail("the self-test could not put the binds back as it found them");
		}
	}

	// ------------------------------------------------------------------ plumbing

	/**
	 * A bind that can be parked on the scratch key without being there already.
	 *
	 * <p>Probing means actually calling {@code setKey}, since asking whether a
	 * version knows a key is exactly what the adapter is for — so the probe puts
	 * the bind straight back, and the caller gets one that has not moved yet.
	 */
	private static BindHandle firstMovable(List<? extends BindHandle> binds) {
		for (BindHandle bind : binds) {
			String before = bind.currentKey();

			if (SCRATCH_KEY.equals(before)) {
				continue;
			}

			if (bind.setKey(SCRATCH_KEY)) {
				bind.setKey(before);
				return bind;
			}
		}

		return null;
	}

	private static void restore(List<? extends BindHandle> binds, Map<String, String> original) {
		for (BindHandle bind : binds) {
			String was = original.get(bind.id());

			if (was != null && !was.equals(bind.currentKey())) {
				bind.setKey(was);
			}
		}
	}

	private void pass(String what) {
		checks.add(what);
	}

	private void fail(String what) {
		failures.add(what);
	}
}
