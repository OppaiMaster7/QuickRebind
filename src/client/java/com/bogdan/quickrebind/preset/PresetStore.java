package com.bogdan.quickrebind.preset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.bogdan.quickrebind.QuickRebindClient;
import com.bogdan.quickrebind.storage.JsonStore;
import com.bogdan.quickrebind.storage.SharedPaths;

/**
 * Reads and writes preset files in the shared folder.
 *
 * <p>Files are named after the preset, not after an opaque id, because the
 * folder is meant to be openable: copying {@code pvp.json} onto a USB stick or
 * editing it in Notepad should both be reasonable things to do.
 */
public final class PresetStore {
	public static final int MAX_NAME_LENGTH = 32;

	private static final String EXTENSION = ".json";
	private static final int MAX_FILE_NAME_ATTEMPTS = 200;

	private PresetStore() {
	}

	/** Every readable preset, sorted by name. Unreadable files are logged and skipped. */
	public static List<Preset> list() {
		Path dir = SharedPaths.presets();

		if (!Files.isDirectory(dir)) {
			return new ArrayList<>();
		}

		List<Preset> presets = new ArrayList<>();

		try (Stream<Path> files = Files.list(dir)) {
			files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION))
					.sorted()
					.forEach(path -> {
						Preset preset = JsonStore.loadOrNull(path, Preset.class);

						if (preset != null) {
							preset.sanitise();
							preset.fileName = path.getFileName().toString();
							presets.add(preset);
						}
					});
		} catch (IOException e) {
			QuickRebindClient.LOGGER.error("Could not list presets in {}", dir, e);
		}

		presets.sort(Comparator.comparing((Preset p) -> p.name.toLowerCase(Locale.ROOT))
				.thenComparing(p -> p.id));
		return presets;
	}

	public static Optional<Preset> byId(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}

		return list().stream().filter(preset -> id.equals(preset.id)).findFirst();
	}

	/** Writes the preset, moving its file if the name changed. */
	public static boolean save(Preset preset) {
		preset.sanitise();
		preset.updatedAt = System.currentTimeMillis();

		String previous = preset.fileName;
		String target = fileNameFor(preset);

		if (!JsonStore.save(SharedPaths.presets().resolve(target), preset)) {
			return false;
		}

		preset.fileName = target;

		if (previous != null && !previous.equals(target)) {
			deleteFile(previous);
		}

		return true;
	}

	public static boolean delete(Preset preset) {
		return preset.fileName != null && deleteFile(preset.fileName);
	}

	/** Makes a name that no other preset is already using, for the "Copy" / import cases. */
	public static String uniqueName(String wanted, List<Preset> existing) {
		String trimmedInput = wanted == null || wanted.isBlank() ? "Preset" : wanted.strip();
		final String base = trimmedInput.length() > MAX_NAME_LENGTH
				? trimmedInput.substring(0, MAX_NAME_LENGTH).strip()
				: trimmedInput;

		if (isFree(base, existing)) {
			return base;
		}

		for (int suffix = 2; suffix < MAX_FILE_NAME_ATTEMPTS; suffix++) {
			String tail = " " + suffix;
			String head = base.length() + tail.length() > MAX_NAME_LENGTH
					? base.substring(0, MAX_NAME_LENGTH - tail.length()).strip()
					: base;
			String candidate = head + tail;

			if (isFree(candidate, existing)) {
				return candidate;
			}
		}

		return base + " " + System.currentTimeMillis();
	}

	private static boolean isFree(String name, List<Preset> existing) {
		return existing.stream().noneMatch(preset -> preset.name.equalsIgnoreCase(name));
	}

	// ----------------------------------------------------------------- naming

	private static String fileNameFor(Preset preset) {
		String base = slug(preset.name);
		Map<String, String> owners = owners();
		String candidate = base + EXTENSION;

		// Keep our own file if we already hold the name; otherwise step aside.
		for (int suffix = 2; isTakenByOther(owners, candidate, preset.id)
				&& suffix < MAX_FILE_NAME_ATTEMPTS; suffix++) {
			candidate = base + "-" + suffix + EXTENSION;
		}

		return isTakenByOther(owners, candidate, preset.id)
				? base + "-" + System.currentTimeMillis() + EXTENSION
				: candidate;
	}

	private static boolean isTakenByOther(Map<String, String> owners, String fileName, String id) {
		String owner = owners.get(fileName);
		return owner != null && !owner.equals(id);
	}

	/** file name -> preset id, for collision checks. */
	private static Map<String, String> owners() {
		Map<String, String> owners = new HashMap<>();

		for (Preset preset : list()) {
			if (preset.fileName != null) {
				owners.put(preset.fileName, preset.id);
			}
		}

		return owners;
	}

	/** Turns "PvP / 1.8 style!" into "pvp-1-8-style". */
	static String slug(String name) {
		StringBuilder out = new StringBuilder();
		boolean lastWasDash = false;

		for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
			if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
				out.append(c);
				lastWasDash = false;
			} else if (!lastWasDash && !out.isEmpty()) {
				out.append('-');
				lastWasDash = true;
			}
		}

		while (!out.isEmpty() && out.charAt(out.length() - 1) == '-') {
			out.setLength(out.length() - 1);
		}

		if (out.length() > MAX_NAME_LENGTH) {
			out.setLength(MAX_NAME_LENGTH);
		}

		return out.isEmpty() ? "preset" : out.toString();
	}

	private static boolean deleteFile(String fileName) {
		try {
			return Files.deleteIfExists(SharedPaths.presets().resolve(fileName));
		} catch (IOException e) {
			QuickRebindClient.LOGGER.error("Could not delete {}", fileName, e);
			return false;
		}
	}
}
