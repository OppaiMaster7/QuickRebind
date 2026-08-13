package com.bogdan.quickrebind.storage;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

import com.bogdan.quickrebind.QuickRebindClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Tiny Gson wrapper that never throws at the caller and never half-writes a file. */
public final class JsonStore {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final Gson COMPACT = new GsonBuilder().create();

	private JsonStore() {
	}

	/** Reads {@code path}, falling back to a fresh object if it is missing or corrupt. */
	public static <T> T load(Path path, Class<T> type, Supplier<T> fallback) {
		if (!Files.isRegularFile(path)) {
			return fallback.get();
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			T value = GSON.fromJson(reader, type);
			return value != null ? value : fallback.get();
		} catch (Exception e) {
			// A broken file shouldn't cost the user their session, but it also
			// shouldn't be silently overwritten — keep a copy and start clean.
			QuickRebindClient.LOGGER.error("Could not read {}, starting from defaults", path.getFileName(), e);
			backup(path);
			return fallback.get();
		}
	}

	/** Reads {@code path}, returning null if it is missing or unreadable. */
	public static <T> T loadOrNull(Path path, Class<T> type) {
		if (!Files.isRegularFile(path)) {
			return null;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, type);
		} catch (Exception e) {
			QuickRebindClient.LOGGER.error("Could not read {}", path.getFileName(), e);
			return null;
		}
	}

	/** Writes to a temp file and moves it into place, so a crash can't truncate the real one. */
	public static boolean save(Path path, Object value) {
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");

			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(value, writer);
			}

			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException e) {
			QuickRebindClient.LOGGER.error("Could not write {}", path.getFileName(), e);
			return false;
		}
	}

	private static void backup(Path path) {
		try {
			Files.move(path, path.resolveSibling(path.getFileName() + ".broken"),
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			QuickRebindClient.LOGGER.warn("Could not back up {}", path.getFileName(), e);
		}
	}
}
