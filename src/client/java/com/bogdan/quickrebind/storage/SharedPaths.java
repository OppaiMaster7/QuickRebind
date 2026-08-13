package com.bogdan.quickrebind.storage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Where QuickRebind keeps its data.
 *
 * <p>Deliberately <em>not</em> the instance's config folder. The whole point of
 * the mod is that a preset saved in your PvP install shows up in a modpack you
 * launched from a different launcher five minutes later, so everything lives in
 * one per-user folder outside any game directory. That also means every
 * Minecraft account on the machine shares the same presets, since the folder
 * belongs to the OS user, not the game.
 *
 * <p>Override with {@code -Dquickrebind.dir=...} or the {@code QUICKREBIND_DIR}
 * environment variable — useful if you keep the folder on a synced drive.
 */
public final class SharedPaths {
	private static final String FOLDER = "QuickRebind";

	private SharedPaths() {
	}

	public static Path root() {
		Path override = override();

		if (override != null) {
			return override;
		}

		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

		if (os.contains("win")) {
			String appData = System.getenv("APPDATA");

			if (isUsable(appData)) {
				return Paths.get(appData).resolve(FOLDER);
			}
		} else if (os.contains("mac")) {
			return home().resolve("Library").resolve("Application Support").resolve(FOLDER);
		} else {
			String xdg = System.getenv("XDG_DATA_HOME");

			if (isUsable(xdg)) {
				return Paths.get(xdg).resolve(FOLDER);
			}

			return home().resolve(".local").resolve("share").resolve(FOLDER);
		}

		return home().resolve("." + FOLDER.toLowerCase(Locale.ROOT));
	}

	public static Path presets() {
		return root().resolve("presets");
	}

	public static Path config() {
		return root().resolve("config.json");
	}

	/** Snapshot of the binds as they were before the last apply, for the undo button. */
	public static Path undo() {
		return root().resolve("undo.json");
	}

	private static Path override() {
		String property = System.getProperty("quickrebind.dir");

		if (isUsable(property)) {
			return Paths.get(property);
		}

		String env = System.getenv("QUICKREBIND_DIR");
		return isUsable(env) ? Paths.get(env) : null;
	}

	private static Path home() {
		return Paths.get(System.getProperty("user.home", "."));
	}

	private static boolean isUsable(String value) {
		return value != null && !value.isBlank();
	}
}
