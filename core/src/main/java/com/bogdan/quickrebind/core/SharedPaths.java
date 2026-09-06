package com.bogdan.quickrebind.core;

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
 * belongs to the OS user, not the game — and every Minecraft <em>version</em>
 * shares them too, which is what makes the 1.8.9 and 26.2 builds talk to each
 * other.
 *
 * <p>Override with {@code -Dquickrebind.dir=...} or the {@code QUICKREBIND_DIR}
 * environment variable — useful if you keep the folder on a synced drive.
 */
public final class SharedPaths {
	private static final String FOLDER = "QuickRebind";

	private static Path instanceDir;

	private SharedPaths() {
	}

	/**
	 * Where this particular install keeps its config, told to us at startup.
	 *
	 * <p>Core has no way to find the game directory itself, so the platform layer
	 * hands it over — {@code FabricLoader.getInstance().getConfigDir()}.
	 */
	public static void useInstanceDir(Path dir) {
		instanceDir = dir;
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

	/**
	 * The settings that stay with this install — see {@link InstanceConfig}.
	 *
	 * <p>Falls back to the shared folder if nobody called {@link #useInstanceDir},
	 * which should not happen in a real launch and only keeps the store working
	 * rather than throwing.
	 */
	public static Path instanceConfig() {
		return instanceDir == null
				? root().resolve("instance.json")
				: instanceDir.resolve("quickrebind.json");
	}

	/**
	 * Snapshot of the binds as they were before the last apply, for the undo button.
	 *
	 * <p>Instance-scoped for the same reason auto-apply is: it records what
	 * <em>this</em> install looked like a moment ago. A snapshot taken in your PvP
	 * instance has nothing to say about the modpack you opened afterwards, and
	 * restoring it there would undo a change that install never made.
	 */
	public static Path undo() {
		return instanceDir == null
				? root().resolve("undo.json")
				: instanceDir.resolve("quickrebind-undo.json");
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
		return value != null && !value.trim().isEmpty();
	}
}
