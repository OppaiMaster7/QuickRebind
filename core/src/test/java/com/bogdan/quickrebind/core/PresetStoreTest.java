package com.bogdan.quickrebind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Reading and writing the shared preset folder. */
class PresetStoreTest {
	@RegisterExtension
	final SharedFolder folder = new SharedFolder();

	private static Map<String, String> binds(String... pairs) {
		Map<String, String> out = new LinkedHashMap<>();

		for (int i = 0; i < pairs.length; i += 2) {
			out.put(pairs[i], pairs[i + 1]);
		}

		return out;
	}

	private static Preset save(String name, String... pairs) {
		Preset preset = Preset.of(name, binds(pairs), "1.21.1");
		assertTrue(PresetStore.save(preset), "save should succeed");
		return preset;
	}

	@Test
	@DisplayName("a saved preset comes back with its binds intact")
	void savesAndReadsBack() {
		save("PvP", "key.sprint", "key.keyboard.v", "key.drop", "key.keyboard.g");

		List<Preset> presets = PresetStore.list();

		assertEquals(1, presets.size());
		assertEquals("PvP", presets.get(0).name);
		assertEquals("key.keyboard.v", presets.get(0).binds.get("key.sprint"));
		assertEquals(2, presets.get(0).size());
	}

	@Test
	@DisplayName("the file is named after the preset, so the folder stays browsable")
	void fileIsNamedAfterThePreset() {
		save("PvP / 1.8 style!", "key.sprint", "key.keyboard.v");

		assertTrue(Files.exists(SharedPaths.presets().resolve("pvp-1-8-style.json")),
				"expected a readable file name, got " + listFiles());
	}

	@Test
	@DisplayName("two presets wanting the same file name get distinct ones")
	void differentPresetsGetDifferentFiles() {
		// Both slugify to "pvp", so the second has to step aside.
		save("PvP!", "key.sprint", "key.keyboard.v");
		save("PvP?", "key.drop", "key.keyboard.g");

		assertEquals(2, PresetStore.list().size());
		assertEquals(Arrays.asList("pvp-2.json", "pvp.json"), listFiles());
	}

	@Test
	@DisplayName("renaming moves the file rather than leaving a stale copy")
	void renameMovesTheFile() {
		Preset preset = save("PvP", "key.sprint", "key.keyboard.v");

		preset.name = "Building";
		assertTrue(PresetStore.save(preset));

		assertEquals(Arrays.asList("building.json"), listFiles());
		assertEquals(1, PresetStore.list().size());
	}

	@Test
	@DisplayName("update keeps the id, the file and the creation date")
	void updateBindsIsAnEditNotAReplacement() {
		Preset preset = save("PvP", "key.sprint", "key.keyboard.v");
		String id = preset.id;
		long createdAt = preset.createdAt;

		assertTrue(PresetStore.updateBinds(preset, binds("key.sprint", "key.keyboard.c")));

		List<Preset> presets = PresetStore.list();
		assertEquals(1, presets.size());
		assertEquals(id, presets.get(0).id, "auto-apply and the last-applied marker point at the id");
		assertEquals(createdAt, presets.get(0).createdAt);
		assertEquals("key.keyboard.c", presets.get(0).binds.get("key.sprint"));
	}

	@Test
	@DisplayName("byId finds a preset and shrugs at an unknown one")
	void byId() {
		Preset preset = save("PvP", "key.sprint", "key.keyboard.v");

		assertNotNull(PresetStore.byId(preset.id));
		assertNull(PresetStore.byId("no-such-id"));
		assertNull(PresetStore.byId(""));
		assertNull(PresetStore.byId(null));
	}

	@Test
	@DisplayName("delete removes the file")
	void deleteRemovesTheFile() {
		Preset preset = save("PvP", "key.sprint", "key.keyboard.v");

		assertTrue(PresetStore.delete(preset));
		assertTrue(PresetStore.list().isEmpty());
	}

	@Test
	@DisplayName("presets are listed by name, case-insensitively")
	void listIsSortedByName() {
		save("zebra", "key.jump", "key.keyboard.space");
		save("Apple", "key.jump", "key.keyboard.space");
		save("mango", "key.jump", "key.keyboard.space");

		assertEquals(Arrays.asList("Apple", "mango", "zebra"),
				PresetStore.list().stream().map(p -> p.name).collect(Collectors.toList()));
	}

	@Test
	@DisplayName("a corrupt file is skipped, not fatal")
	void corruptFileDoesNotBreakTheList() throws IOException {
		save("PvP", "key.sprint", "key.keyboard.v");
		Files.write(SharedPaths.presets().resolve("broken.json"),
				"{ this is not json".getBytes(StandardCharsets.UTF_8));

		List<Preset> presets = PresetStore.list();

		assertEquals(1, presets.size(), "the good preset should still load");
		assertEquals("PvP", presets.get(0).name);
	}

	@Test
	@DisplayName("an empty folder lists nothing rather than failing")
	void emptyFolder() {
		assertTrue(PresetStore.list().isEmpty());
	}

	@Test
	@DisplayName("uniqueName steps around names already taken")
	void uniqueNameAvoidsCollisions() {
		save("PvP", "key.jump", "key.keyboard.space");
		List<Preset> existing = PresetStore.list();

		assertEquals("PvP 2", PresetStore.uniqueName("PvP", existing));
		// The clash is spotted ignoring case, but the name you typed is the
		// name you get back — only the suffix is ours to add.
		assertEquals("pvp 2", PresetStore.uniqueName("pvp", existing));
		assertEquals("Building", PresetStore.uniqueName("Building", existing));
	}

	@Test
	@DisplayName("uniqueName keeps the result inside the length limit")
	void uniqueNameRespectsMaxLength() {
		String long_ = "abcdefghijklmnopqrstuvwxyz0123456789";

		String name = PresetStore.uniqueName(long_, PresetStore.list());

		assertTrue(name.length() <= PresetStore.MAX_NAME_LENGTH, "got " + name.length() + " chars: " + name);
	}

	@Test
	@DisplayName("findByName ignores case and blank input")
	void findByName() {
		save("PvP", "key.jump", "key.keyboard.space");
		List<Preset> existing = PresetStore.list();

		assertNotNull(PresetStore.findByName("pvp", existing));
		assertNotNull(PresetStore.findByName("  PvP  ", existing));
		assertNull(PresetStore.findByName("Building", existing));
		assertNull(PresetStore.findByName("  ", existing));
	}

	// ------------------------------------------------------------------ cycle

	@Test
	@DisplayName("cycle walks the list and wraps")
	void cycleWraps() {
		save("a", "key.jump", "key.keyboard.space");
		save("b", "key.jump", "key.keyboard.space");
		save("c", "key.jump", "key.keyboard.space");
		List<Preset> presets = PresetStore.list();

		assertEquals("b", PresetStore.cycle(presets, presets.get(0).id, 1).name);
		assertEquals("a", PresetStore.cycle(presets, presets.get(2).id, 1).name, "wraps forwards");
		assertEquals("c", PresetStore.cycle(presets, presets.get(0).id, -1).name, "wraps backwards");
	}

	@Test
	@DisplayName("cycle starts at the top when it does not recognise the current preset")
	void cycleStartsAtTheTopWhenCurrentIsUnknown() {
		save("a", "key.jump", "key.keyboard.space");
		save("b", "key.jump", "key.keyboard.space");
		List<Preset> presets = PresetStore.list();

		assertEquals("a", PresetStore.cycle(presets, "", 1).name);
		assertEquals("a", PresetStore.cycle(presets, "gone", 1).name);
	}

	@Test
	@DisplayName("cycle on an empty list gives nothing rather than throwing")
	void cycleOnEmptyList() {
		assertNull(PresetStore.cycle(PresetStore.list(), "", 1));
	}

	@Test
	@DisplayName("a two-preset setup toggles")
	void cycleTogglesOnTwo() {
		save("a", "key.jump", "key.keyboard.space");
		save("b", "key.jump", "key.keyboard.space");
		List<Preset> presets = PresetStore.list();

		Preset first = PresetStore.cycle(presets, "", 1);
		Preset second = PresetStore.cycle(presets, first.id, 1);
		Preset third = PresetStore.cycle(presets, second.id, 1);

		assertEquals(first.id, third.id);
	}

	// --------------------------------------------------------------- sanitise

	@Test
	@DisplayName("a hand-edited file missing fields is repaired on read")
	void sanitiseFillsInWhatAHandEditedFileLeftOut() throws IOException {
		Files.createDirectories(SharedPaths.presets());
		Files.write(SharedPaths.presets().resolve("hand-written.json"),
				"{\"binds\":{\"key.sprint\":\"key.keyboard.v\"}}".getBytes(StandardCharsets.UTF_8));

		List<Preset> presets = PresetStore.list();

		assertEquals(1, presets.size());
		Preset preset = presets.get(0);
		assertFalse(Preset.isBlank(preset.id), "an id should have been generated");
		assertEquals("Unnamed", preset.name);
		assertTrue(preset.createdAt > 0);
		assertEquals("key.keyboard.v", preset.binds.get("key.sprint"));
	}

	@Test
	@DisplayName("null and blank entries in a mangled file are dropped")
	void sanitiseDropsBlankEntries() {
		Preset preset = new Preset();
		preset.name = "  Spaced  ";
		preset.binds = binds("key.sprint", "key.keyboard.v", "", "key.keyboard.g", "key.drop", "");
		preset.binds.put("key.null", null);

		preset.sanitise();

		assertEquals("Spaced", preset.name);
		assertEquals(1, preset.binds.size());
		assertEquals("key.keyboard.v", preset.binds.get("key.sprint"));
	}

	@Test
	@DisplayName("an over-long name is trimmed to the limit")
	void sanitiseTrimsLongNames() {
		Preset preset = Preset.of("abcdefghijklmnopqrstuvwxyz0123456789", binds(), "1.21.1");

		preset.sanitise();

		assertEquals(PresetStore.MAX_NAME_LENGTH, preset.name.length());
	}

	private List<String> listFiles() {
		Path dir = SharedPaths.presets();

		if (!Files.isDirectory(dir)) {
			return java.util.Collections.emptyList();
		}

		try (java.util.stream.Stream<Path> files = Files.list(dir)) {
			return files.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}
