package com.bogdan.quickrebind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * The promise on the download page: a preset made on one Minecraft works on any
 * other, and nothing is lost on the way through.
 *
 * <p>These are the tests that would catch the failure that actually costs
 * someone their keys — a preset captured on 26.2, applied in a 1.19.2 install
 * that has fewer binds, quietly dropping every entry the older version has
 * never heard of. Each test drives a preset through a real save/load cycle
 * rather than passing the object around, because the file on disk is what
 * carries the binds between two installs that never run at the same time.
 */
class CrossVersionTest {
	@RegisterExtension
	final SharedFolder folder = new SharedFolder();

	/** A modern install: every vanilla bind plus a couple that only exist here. */
	private static FakeInstall newVersion() {
		return new FakeInstall()
				.withMoved("key.sprint", "key.keyboard.left.control", "key.keyboard.v")
				.withMoved("key.drop", "key.keyboard.q", "key.keyboard.g")
				.with("key.jump", "key.keyboard.space")
				.with("key.inventory", "key.keyboard.e")
				.with("key.attack", "key.mouse.left")
				.with("key.use", "key.mouse.right")
				// Added after 1.19.2 shipped.
				.withMoved("key.quickActions", "key.keyboard.unknown", "key.keyboard.x");
	}

	/** The same player's older install: no key.quickActions, plus a mod of its own. */
	private static FakeInstall oldVersion() {
		return new FakeInstall()
				.with("key.sprint", "key.keyboard.left.control")
				.with("key.drop", "key.keyboard.q")
				.with("key.jump", "key.keyboard.space")
				.with("key.inventory", "key.keyboard.e")
				.with("key.attack", "key.mouse.left")
				.with("key.use", "key.mouse.right")
				.withMoved("key.jei.showRecipe", "key.keyboard.r", "key.keyboard.z");
	}

	/** Captures on one install, writes the file, reads it back on another. */
	private static Preset transfer(FakeInstall from, String name, String gameVersion) {
		Map<String, String> captured = ApplyEngine.capture(from.handles());
		assertTrue(PresetStore.save(Preset.of(name, captured, gameVersion)));

		List<Preset> onDisk = PresetStore.list();
		assertEquals(1, onDisk.size());
		return onDisk.get(0);
	}

	@Test
	@DisplayName("a 26.2 preset applies on 1.19.2 and moves the binds they share")
	void newPresetAppliesOnOldVersion() {
		Preset preset = transfer(newVersion(), "PvP", "26.2");

		FakeInstall old = oldVersion();
		ApplyResult result = ApplyEngine.apply(old.handles(), preset, MissingBindPolicy.LEAVE);

		assertEquals("key.keyboard.v", old.keyOf("key.sprint"));
		assertEquals("key.keyboard.g", old.keyOf("key.drop"));
		assertEquals(2, result.rebound);
		assertEquals(4, result.alreadyCorrect);
	}

	@Test
	@DisplayName("binds the older version lacks stay in the file for next time")
	void entriesTheOlderVersionLacksSurviveInTheFile() {
		Preset preset = transfer(newVersion(), "PvP", "26.2");

		ApplyResult result = ApplyEngine.apply(oldVersion().handles(), preset, MissingBindPolicy.LEAVE);

		assertEquals(1, result.notInstalled, "key.quickActions is not in 1.19.2");

		// The whole claim rests on this: re-read the file and the entry is
		// still there, so launching 26.2 again gets its key back.
		Preset reread = PresetStore.list().get(0);
		assertEquals("key.keyboard.x", reread.binds.get("key.quickActions"));
		assertEquals(7, reread.size(), "nothing should have been dropped from the file");
	}

	@Test
	@DisplayName("a modded bind survives a trip through a vanilla install")
	void moddedBindSurvivesVanilla() {
		// Captured in a modpack, complete with a JEI bind.
		FakeInstall modpack = oldVersion();
		Preset preset = transfer(modpack, "Modded", "1.20.1");

		// Applied in a vanilla install that has never heard of JEI.
		FakeInstall vanilla = new FakeInstall()
				.with("key.sprint", "key.keyboard.left.control")
				.with("key.jump", "key.keyboard.space");
		ApplyResult result = ApplyEngine.apply(vanilla.handles(), preset, MissingBindPolicy.LEAVE);

		assertTrue(result.notInstalled > 0);
		assertEquals("key.keyboard.z", PresetStore.list().get(0).binds.get("key.jei.showRecipe"),
				"the JEI entry must still be in the file, waiting for the modpack");
	}

	@Test
	@DisplayName("applying a vanilla preset does not wipe the modded binds already here")
	void vanillaPresetLeavesModdedBindsAlone() {
		FakeInstall vanilla = new FakeInstall()
				.withMoved("key.sprint", "key.keyboard.left.control", "key.keyboard.v")
				.with("key.jump", "key.keyboard.space");
		Preset preset = transfer(vanilla, "Vanilla", "1.21.1");

		FakeInstall modpack = oldVersion();
		ApplyEngine.apply(modpack.handles(), preset, MissingBindPolicy.LEAVE);

		assertEquals("key.keyboard.z", modpack.keyOf("key.jei.showRecipe"));
		assertEquals("key.keyboard.v", modpack.keyOf("key.sprint"), "the shared bind still moves");
	}

	@Test
	@DisplayName("a key the older version cannot parse leaves that bind where it was")
	void keyTheOlderVersionLacksIsRefusedNotGuessed() {
		// F13-F25 arrived with the LWJGL3 switch; an older install has no such key.
		Set<String> withoutF13 = new HashSet<>(FakeInstall.COMMON_KEYS);
		FakeInstall modern = new FakeInstall(union(withoutF13, "key.keyboard.f13"))
				.withMoved("key.sprint", "key.keyboard.left.control", "key.keyboard.f13")
				.with("key.jump", "key.keyboard.space");

		Preset preset = transfer(modern, "Odd keys", "26.2");

		FakeInstall old = new FakeInstall(withoutF13)
				.with("key.sprint", "key.keyboard.left.control")
				.with("key.jump", "key.keyboard.space");
		ApplyResult result = ApplyEngine.apply(old.handles(), preset, MissingBindPolicy.LEAVE);

		assertEquals(1, result.unreadable);
		assertEquals("key.keyboard.left.control", old.keyOf("key.sprint"),
				"an unusable key must leave the bind alone rather than unbind it");
	}

	@Test
	@DisplayName("a round trip through two versions ends where it started")
	void roundTripIsLossless() {
		FakeInstall modern = newVersion();
		Map<String, String> before = ApplyEngine.capture(modern.handles());
		Preset preset = transfer(modern, "PvP", "26.2");

		// Off to the old version and back.
		ApplyEngine.apply(oldVersion().handles(), preset, MissingBindPolicy.LEAVE);

		FakeInstall backOnModern = newVersion();
		ApplyEngine.resetAllToDefault(backOnModern.handles());
		ApplyEngine.apply(backOnModern.handles(), PresetStore.list().get(0), MissingBindPolicy.LEAVE);

		assertEquals(before, ApplyEngine.capture(backOnModern.handles()));
	}

	@Test
	@DisplayName("a share code carries a preset to another machine unchanged")
	void shareCodeCarriesThePresetBetweenMachines() {
		Preset preset = transfer(newVersion(), "PvP", "26.2");

		Preset received = ShareCode.decode(ShareCode.encode(preset));

		assertEquals(preset.binds, received.binds);
		assertEquals("PvP", received.name);
		assertEquals("26.2", received.gameVersion);
	}

	@Test
	@DisplayName("a preset received by share code applies on an older version too")
	void sharedPresetAppliesOnAnotherVersion() {
		Preset preset = transfer(newVersion(), "PvP", "26.2");
		String code = ShareCode.encode(preset);

		// Somebody else, on 1.19.2, pastes it in.
		Preset received = ShareCode.decode(code);
		FakeInstall old = oldVersion();
		ApplyResult result = ApplyEngine.apply(old.handles(), received, MissingBindPolicy.LEAVE);

		assertEquals("key.keyboard.v", old.keyOf("key.sprint"));
		assertEquals(1, result.notInstalled);
	}

	@Test
	@DisplayName("undo puts back exactly what was there before an apply")
	void undoRestoresTheEarlierBinds() {
		FakeInstall install = oldVersion();
		Map<String, String> before = ApplyEngine.capture(install.handles());

		// What GameBinds.snapshotForUndo does, minus the Minecraft.
		Preset snapshot = Preset.of("Before last apply", before, "1.19.2");
		assertTrue(JsonStore.save(SharedPaths.undo(), snapshot));

		ApplyEngine.apply(install.handles(),
				Preset.of("Other", java.util.Collections.singletonMap("key.sprint", "key.keyboard.c"), "1.19.2"),
				MissingBindPolicy.LEAVE);
		assertEquals("key.keyboard.c", install.keyOf("key.sprint"));

		Preset undo = JsonStore.loadOrNull(SharedPaths.undo(), Preset.class);
		assertNotNull(undo);
		ApplyEngine.apply(install.handles(), undo.sanitise(), MissingBindPolicy.LEAVE);

		assertEquals(before, ApplyEngine.capture(install.handles()));
	}

	private static Set<String> union(Set<String> base, String extra) {
		Set<String> out = new HashSet<>(base);
		out.add(extra);
		return out;
	}
}
