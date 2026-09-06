package com.bogdan.quickrebind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The "paste it into Discord" half. */
class ShareCodeTest {
	private static Preset preset() {
		Map<String, String> binds = new LinkedHashMap<>();
		binds.put("key.sprint", "key.keyboard.left.control");
		binds.put("key.drop", "key.keyboard.g");
		binds.put("key.jei.showRecipe", "key.keyboard.r");
		return Preset.of("PvP", binds, "1.21.1");
	}

	@Test
	@DisplayName("a code decodes back to the same binds")
	void roundTrip() {
		Preset original = preset();

		Preset decoded = ShareCode.decode(ShareCode.encode(original));

		assertEquals(original.binds, decoded.binds);
		assertEquals(original.name, decoded.name);
		assertEquals(original.gameVersion, decoded.gameVersion);
	}

	@Test
	@DisplayName("the code carries no id and no timestamps")
	void codeDropsLocalIdentity() {
		Preset original = preset();

		Preset decoded = ShareCode.decode(ShareCode.encode(original));

		assertNotEquals(original.id, decoded.id, "the receiver gets their own id");
		assertFalse(Preset.isBlank(decoded.id));
	}

	@Test
	@DisplayName("codes start with the format marker")
	void codeIsRecognisable() {
		assertTrue(ShareCode.encode(preset()).startsWith("QRB1."));
	}

	@Test
	@DisplayName("line breaks and spaces from a chat app are tolerated")
	void survivesChatAppMangling() {
		String code = ShareCode.encode(preset());

		String mangled = "  " + code.substring(0, 12) + "\n" + code.substring(12, 20)
				+ " \r\n " + code.substring(20) + "  ";

		assertEquals(preset().binds, ShareCode.decode(mangled).binds);
	}

	@Test
	@DisplayName("the prefix is matched case-insensitively")
	void prefixIsCaseInsensitive() {
		String code = ShareCode.encode(preset());

		assertEquals(preset().binds, ShareCode.decode("qrb1." + code.substring(5)).binds);
	}

	@Test
	@DisplayName("junk is rejected with a message the GUI can translate")
	void rejectsJunk() {
		assertEquals("quickrebind.import.error.empty",
				assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("")).getMessage());
		assertEquals("quickrebind.import.error.empty",
				assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("   ")).getMessage());
		assertEquals("quickrebind.import.error.empty",
				assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(null)).getMessage());
		assertEquals("quickrebind.import.error.prefix",
				assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("hello there")).getMessage());
		assertEquals("quickrebind.import.error.malformed",
				assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("QRB1.not-base64-$$$")).getMessage());
		assertEquals("quickrebind.import.error.malformed",
				assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("QRB1.aGVsbG8")).getMessage(),
				"valid base64 that is not gzip");
	}

	@Test
	@DisplayName("a code carrying no binds is rejected rather than imported empty")
	void rejectsEmptyPreset() {
		Preset empty = Preset.of("Empty", new LinkedHashMap<>(), "1.21.1");

		assertEquals("quickrebind.import.error.empty_preset",
				assertThrows(IllegalArgumentException.class,
						() -> ShareCode.decode(ShareCode.encode(empty))).getMessage());
	}

	@Test
	@DisplayName("an unnamed preset arrives as Imported rather than blank")
	void unnamedPresetGetsAName() {
		Preset unnamed = preset();
		unnamed.name = "";

		assertEquals("Imported", ShareCode.decode(ShareCode.encode(unnamed)).name);
	}

	@Test
	@DisplayName("a big preset still fits in a pasteable line")
	void codeStaysPasteable() {
		Map<String, String> binds = new LinkedHashMap<>();

		// Roughly a heavily modded install.
		for (int i = 0; i < 200; i++) {
			binds.put("key.somemod.action" + i, "key.keyboard.f" + (i % 12 + 1));
		}

		String code = ShareCode.encode(Preset.of("Huge", binds, "1.21.1"));

		assertTrue(code.length() < 4000, "a 200-bind preset encoded to " + code.length() + " chars");
		assertEquals(binds, ShareCode.decode(code).binds);
	}
}
