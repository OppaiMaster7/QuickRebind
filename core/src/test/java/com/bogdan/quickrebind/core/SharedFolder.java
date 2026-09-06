package com.bogdan.quickrebind.core;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Points {@link SharedPaths} at a throwaway folder for the duration of a test.
 *
 * <p>Without this, anything touching {@link PresetStore} would read and write
 * the real {@code %APPDATA%\QuickRebind} — that is, the developer's own
 * presets. The override is the same {@code -Dquickrebind.dir} switch the mod
 * documents for people keeping their folder on a synced drive, so the tests
 * exercise that path too rather than reaching past it.
 */
final class SharedFolder implements BeforeEachCallback, AfterEachCallback {
	static final String PROPERTY = "quickrebind.dir";

	private Path root;
	private String previous;

	/** The folder the current test is using. */
	Path root() {
		return root;
	}

	@Override
	public void beforeEach(ExtensionContext context) throws IOException {
		root = java.nio.file.Files.createTempDirectory("quickrebind-test");
		previous = System.setProperty(PROPERTY, root.toString());
		SharedPaths.useInstanceDir(root.resolve("instance"));
	}

	@Override
	public void afterEach(ExtensionContext context) throws IOException {
		if (previous == null) {
			System.clearProperty(PROPERTY);
		} else {
			System.setProperty(PROPERTY, previous);
		}

		deleteTree(root);
	}

	private static void deleteTree(Path path) throws IOException {
		if (path == null || !java.nio.file.Files.exists(path)) {
			return;
		}

		try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(path)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try {
					java.nio.file.Files.deleteIfExists(p);
				} catch (IOException ignored) {
					// A leftover temp folder is not worth failing a green run over.
				}
			});
		}
	}
}
