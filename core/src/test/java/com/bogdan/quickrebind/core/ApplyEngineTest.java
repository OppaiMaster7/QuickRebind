package com.bogdan.quickrebind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The apply rules, which every version shares and none of them may re-decide. */
class ApplyEngineTest {
	private static Preset preset(String... pairs) {
		Map<String, String> binds = new LinkedHashMap<>();

		for (int i = 0; i < pairs.length; i += 2) {
			binds.put(pairs[i], pairs[i + 1]);
		}

		return Preset.of("Test", binds, "1.21.1");
	}

	@Test
	@DisplayName("capture reads every bind as id to current key")
	void captureReadsEveryBind() {
		FakeInstall install = new FakeInstall()
				.with("key.jump", "key.keyboard.space")
				.withMoved("key.sprint", "key.keyboard.left.control", "key.keyboard.v");

		Map<String, String> captured = ApplyEngine.capture(install.handles());

		assertEquals(2, captured.size());
		assertEquals("key.keyboard.space", captured.get("key.jump"));
		assertEquals("key.keyboard.v", captured.get("key.sprint"));
	}

	@Test
	@DisplayName("only binds that differ are moved")
	void movesOnlyWhatDiffers() {
		FakeInstall install = new FakeInstall()
				.with("key.jump", "key.keyboard.space")
				.with("key.sprint", "key.keyboard.left.control");

		ApplyResult result = ApplyEngine.apply(install.handles(),
				preset("key.jump", "key.keyboard.space", "key.sprint", "key.keyboard.v"),
				MissingBindPolicy.LEAVE);

		assertEquals(1, result.rebound);
		assertEquals(1, result.alreadyCorrect);
		assertEquals("key.keyboard.v", install.keyOf("key.sprint"));
		assertEquals("key.keyboard.space", install.keyOf("key.jump"));
	}

	@Test
	@DisplayName("LEAVE keeps binds the preset says nothing about")
	void leavePolicyKeepsUnmentionedBinds() {
		FakeInstall install = new FakeInstall()
				.with("key.jump", "key.keyboard.space")
				.withMoved("key.jei.showRecipe", "key.keyboard.r", "key.keyboard.g");

		ApplyResult result = ApplyEngine.apply(install.handles(),
				preset("key.jump", "key.keyboard.space"), MissingBindPolicy.LEAVE);

		assertEquals(0, result.resetToDefault);
		assertEquals("key.keyboard.g", install.keyOf("key.jei.showRecipe"),
				"a vanilla preset must not quietly wipe a modded bind");
	}

	@Test
	@DisplayName("RESET_TO_DEFAULT puts unmentioned binds back")
	void resetPolicyRestoresUnmentionedBinds() {
		FakeInstall install = new FakeInstall()
				.with("key.jump", "key.keyboard.space")
				.withMoved("key.jei.showRecipe", "key.keyboard.r", "key.keyboard.g");

		ApplyResult result = ApplyEngine.apply(install.handles(),
				preset("key.jump", "key.keyboard.space"), MissingBindPolicy.RESET_TO_DEFAULT);

		assertEquals(1, result.resetToDefault);
		assertEquals("key.keyboard.r", install.keyOf("key.jei.showRecipe"));
	}

	@Test
	@DisplayName("a bind already on its default is not counted as reset")
	void resetPolicySkipsBindsAlreadyDefault() {
		FakeInstall install = new FakeInstall()
				.with("key.jump", "key.keyboard.space")
				.with("key.drop", "key.keyboard.q");

		ApplyResult result = ApplyEngine.apply(install.handles(),
				preset("key.jump", "key.keyboard.space"), MissingBindPolicy.RESET_TO_DEFAULT);

		assertEquals(0, result.resetToDefault);
	}

	@Test
	@DisplayName("preset entries this install lacks are counted, not applied")
	void countsEntriesThisInstallLacks() {
		FakeInstall install = new FakeInstall().with("key.jump", "key.keyboard.space");

		ApplyResult result = ApplyEngine.apply(install.handles(),
				preset("key.jump", "key.keyboard.space",
						"key.jei.showRecipe", "key.keyboard.r",
						"key.sodium.options", "key.keyboard.x"),
				MissingBindPolicy.LEAVE);

		assertEquals(2, result.notInstalled);
		assertEquals(0, result.rebound);
	}

	@Test
	@DisplayName("a key this version cannot parse leaves the bind untouched")
	void unreadableKeyLeavesBindAlone() {
		FakeInstall install = new FakeInstall().with("key.jump", "key.keyboard.space");

		ApplyResult result = ApplyEngine.apply(install.handles(),
				preset("key.jump", "key.keyboard.f13"), MissingBindPolicy.LEAVE);

		assertEquals(1, result.unreadable);
		assertEquals(0, result.rebound);
		assertEquals("key.keyboard.space", install.keyOf("key.jump"));
	}

	// -------------------------------------------------------------- conflicts

	@Test
	@DisplayName("two binds on one key count as two conflicts")
	void countsBindsSharingAKey() {
		FakeInstall install = new FakeInstall()
				.with("key.jump", "key.keyboard.space")
				.withMoved("key.sprint", "key.keyboard.left.control", "key.keyboard.space")
				.with("key.drop", "key.keyboard.q");

		assertEquals(2, ApplyEngine.conflicts(install.handles()));
	}

	@Test
	@DisplayName("debug binds are excluded, so a stock install reports zero")
	void debugBindsAreNotConflicts() {
		// key.debug.* are the F3+X combos: vanilla ships them overlapping the
		// movement keys, so counting them reports clashes on an untouched
		// profile and trains you to ignore the number.
		FakeInstall install = new FakeInstall()
				.with("key.right", "key.keyboard.d")
				.with("key.debug.clearChat", "key.keyboard.d");

		assertEquals(0, ApplyEngine.conflicts(install.handles()));
	}

	@Test
	@DisplayName("unbound binds do not conflict with each other")
	void unboundBindsAreNotConflicts() {
		FakeInstall install = new FakeInstall()
				.with("key.quickrebind.cycle", "key.keyboard.unknown")
				.with("key.spectatorOutlines", "key.keyboard.unknown");

		assertEquals(0, ApplyEngine.conflicts(install.handles()));
	}

	@Test
	@DisplayName("newConflicts reports only the clashes this apply introduced")
	void newConflictsIgnoresPreExistingOnes() {
		// Two binds already share B before anything happens.
		FakeInstall install = new FakeInstall()
				.withMoved("key.use", "key.mouse.right", "key.keyboard.b")
				.withMoved("key.attack", "key.mouse.left", "key.keyboard.b")
				.with("key.jump", "key.keyboard.space");

		ApplyResult result = ApplyEngine.apply(install.handles(),
				preset("key.jump", "key.keyboard.space"), MissingBindPolicy.LEAVE);

		assertEquals(2, result.conflictsBefore);
		assertEquals(2, result.conflicts);
		assertEquals(0, result.newConflicts(), "an overlap the player already had is not news");
	}

	@Test
	@DisplayName("newConflicts catches a clash the preset created")
	void newConflictsCatchesOnesTheApplyCaused() {
		FakeInstall install = new FakeInstall()
				.with("key.jump", "key.keyboard.space")
				.with("key.drop", "key.keyboard.q");

		ApplyResult result = ApplyEngine.apply(install.handles(),
				preset("key.drop", "key.keyboard.space"), MissingBindPolicy.LEAVE);

		assertEquals(0, result.conflictsBefore);
		assertEquals(2, result.conflicts);
		assertEquals(2, result.newConflicts());
	}

	// ------------------------------------------------------------------- diff

	@Test
	@DisplayName("diff sorts the binds that would move to the top")
	void diffPutsMovingBindsFirst() {
		FakeInstall install = new FakeInstall()
				.with("key.attack", "key.mouse.left")
				.with("key.jump", "key.keyboard.space")
				.with("key.sprint", "key.keyboard.left.control")
				.withMoved("key.jei.showRecipe", "key.keyboard.r", "key.keyboard.g");

		List<BindDiff> diff = ApplyEngine.diff(install.handles(),
				preset("key.sprint", "key.keyboard.v", "key.zoom", "key.keyboard.c"),
				MissingBindPolicy.LEAVE);

		assertEquals(BindDiff.Status.CHANGED, diff.get(0).status);
		assertEquals("key.sprint", diff.get(0).id);
		assertTrue(diff.get(0).moves());

		// Everything after the moving ones stays put.
		for (int i = 1; i < diff.size(); i++) {
			assertFalse(diff.get(i).moves(), diff.get(i).id + " should not move");
		}

		BindDiff notInstalled = diff.get(diff.size() - 1);
		assertEquals(BindDiff.Status.NOT_INSTALLED, notInstalled.status);
		assertEquals("key.zoom", notInstalled.id);
		assertNull(notInstalled.currentKey);
	}

	@Test
	@DisplayName("diff ranks a would-reset above things staying put")
	void diffRanksWouldResetSecond() {
		FakeInstall install = new FakeInstall()
				.with("key.jump", "key.keyboard.space")
				.withMoved("key.drop", "key.keyboard.q", "key.keyboard.g");

		List<BindDiff> diff = ApplyEngine.diff(install.handles(),
				preset("key.jump", "key.keyboard.space"), MissingBindPolicy.RESET_TO_DEFAULT);

		assertEquals(BindDiff.Status.WOULD_RESET, diff.get(0).status);
		assertEquals("key.keyboard.q", diff.get(0).targetKey);
		assertEquals(BindDiff.Status.UNCHANGED, diff.get(1).status);
	}

	@Test
	@DisplayName("diff changes nothing")
	void diffIsReadOnly() {
		FakeInstall install = new FakeInstall().with("key.sprint", "key.keyboard.left.control");

		ApplyEngine.diff(install.handles(), preset("key.sprint", "key.keyboard.v"),
				MissingBindPolicy.RESET_TO_DEFAULT);

		assertEquals("key.keyboard.left.control", install.keyOf("key.sprint"));
	}

	@Test
	@DisplayName("diff order is stable between openings")
	void diffOrderIsStable() {
		FakeInstall install = new FakeInstall()
				.with("key.zoom", "key.keyboard.c")
				.with("key.attack", "key.mouse.left")
				.with("key.jump", "key.keyboard.space");

		Preset p = preset("key.jump", "key.keyboard.space");
		List<String> first = ids(ApplyEngine.diff(install.handles(), p, MissingBindPolicy.LEAVE));
		List<String> second = ids(ApplyEngine.diff(install.handles(), p, MissingBindPolicy.LEAVE));

		assertEquals(first, second);
		assertEquals(Arrays.asList("key.jump", "key.attack", "key.zoom"), first);
	}

	@Test
	@DisplayName("reset puts every moved bind back and leaves the rest")
	void resetAllToDefault() {
		FakeInstall install = new FakeInstall()
				.withMoved("key.sprint", "key.keyboard.left.control", "key.keyboard.v")
				.withMoved("key.drop", "key.keyboard.q", "key.keyboard.g")
				.with("key.jump", "key.keyboard.space");

		assertEquals(2, ApplyEngine.resetAllToDefault(install.handles()));
		assertEquals("key.keyboard.left.control", install.keyOf("key.sprint"));
		assertEquals("key.keyboard.q", install.keyOf("key.drop"));
		assertEquals("key.keyboard.space", install.keyOf("key.jump"));
	}

	private static List<String> ids(List<BindDiff> diff) {
		return diff.stream().map(d -> d.id).collect(Collectors.toList());
	}
}
