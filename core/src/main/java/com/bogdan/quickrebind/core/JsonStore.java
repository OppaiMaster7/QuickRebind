package com.bogdan.quickrebind.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Tiny Gson wrapper that never throws at the caller and never half-writes a file. */
public final class JsonStore {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final Gson COMPACT = new GsonBuilder().create();

	private JsonStore() {
	}

	/** Reads {@code path}, returning null if it is missing or unreadable. */
	public static <T> T loadOrNull(Path path, Class<T> type) {
		if (!Files.isRegularFile(path)) {
			return null;
		}

		Reader reader = null;

		try {
			reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
			return GSON.fromJson(reader, type);
		} catch (Exception e) {
			QuickRebindLog.error("Could not read " + path.getFileName(), e);
			return null;
		} finally {
			close(reader);
		}
	}

	/**
	 * Reads {@code path}, falling back to a fresh object if it is missing or
	 * corrupt. A corrupt file is moved aside rather than overwritten.
	 */
	public static <T> T loadOrDefault(Path path, Class<T> type, T fallback) {
		if (!Files.isRegularFile(path)) {
			return fallback;
		}

		Reader reader = null;

		try {
			reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
			T value = GSON.fromJson(reader, type);
			return value != null ? value : fallback;
		} catch (Exception e) {
			// A broken file shouldn't cost the user their session, but it also
			// shouldn't be silently overwritten — keep a copy and start clean.
			QuickRebindLog.error("Could not read " + path.getFileName() + ", starting from defaults", e);
			close(reader);
			reader = null;
			backup(path);
			return fallback;
		} finally {
			close(reader);
		}
	}

	/** Writes to a temp file and moves it into place, so a crash can't truncate the real one. */
	public static boolean save(Path path, Object value) {
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Writer writer = null;

			try {
				writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8);
				GSON.toJson(value, writer);
			} finally {
				close(writer);
			}

			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException e) {
			QuickRebindLog.error("Could not write " + path.getFileName(), e);
			return false;
		}
	}

	private static void backup(Path path) {
		try {
			Files.move(path, path.resolveSibling(path.getFileName() + ".broken"),
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			QuickRebindLog.error("Could not back up " + path.getFileName(), e);
		}
	}

	private static void close(java.io.Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException ignored) {
				// Nothing useful to do about a failed close on a file we're done with.
			}
		}
	}
}
