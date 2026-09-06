package com.bogdan.quickrebind;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bogdan.quickrebind.config.QuickRebindConfig;
import com.bogdan.quickrebind.core.ApplyResult;
import com.bogdan.quickrebind.core.InstanceConfig;
import com.bogdan.quickrebind.core.JsonStore;
import com.bogdan.quickrebind.core.Preset;
import com.bogdan.quickrebind.core.PresetStore;
import com.bogdan.quickrebind.core.QuickRebindLog;
import com.bogdan.quickrebind.core.SelfTest;
import com.bogdan.quickrebind.core.SharedPaths;
import com.bogdan.quickrebind.gui.MenuButtons;
import com.bogdan.quickrebind.gui.PresetDetailsScreen;
import com.bogdan.quickrebind.gui.QuickRebindScreen;
import com.bogdan.quickrebind.gui.QuickRebindSettingsScreen;
import com.bogdan.quickrebind.platform.ControlsRefresh;
import com.bogdan.quickrebind.platform.GameBinds;
import com.bogdan.quickrebind.platform.Notify;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

public class QuickRebindClient implements ClientModInitializer {
	public static final String MOD_ID = "quickrebind";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Set to a file path to run the self-test on launch and quit. */
	private static final String SELF_TEST_PROPERTY = "quickrebind.selftest";

	private static QuickRebindConfig config;
	private static InstanceConfig instance;
	private static KeyMapping openScreenKey;
	private static KeyMapping cycleKey;

	/** Preferences, shared with every other install on the machine. */
	public static QuickRebindConfig config() {
		return config;
	}

	/** The handful of settings that belong to this install alone. */
	public static InstanceConfig instance() {
		return instance;
	}

	public static void saveConfig() {
		config.sanitise();
		JsonStore.save(SharedPaths.config(), config);
	}

	public static void saveInstance() {
		instance.sanitise();
		JsonStore.save(SharedPaths.instanceConfig(), instance);
	}

	/**
	 * Records which preset this install is now on, or blank when we no longer
	 * know — after an undo or a reset, the binds match nothing you saved.
	 */
	public static void markApplied(String presetId) {
		instance.lastAppliedId = presetId == null ? "" : presetId;
		saveInstance();
	}

	/**
	 * Whether a key press on some open screen is one of ours. Needed because
	 * keybinds are only dispatched to KeyMapping when no screen is up.
	 */
	public static boolean openKeyMatches(int key, int scancode) {
		return matches(openScreenKey, key, scancode);
	}

	public static boolean cycleKeyMatches(int key, int scancode) {
		return matches(cycleKey, key, scancode);
	}

	private static boolean matches(KeyMapping mapping, int key, int scancode) {
		return mapping != null && !mapping.isUnbound() && mapping.matches(key, scancode);
	}

	/** The Minecraft version presets are stamped with, purely for display. */
	public static String gameVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}

	@Override
	public void onInitializeClient() {
		// Core can't depend on slf4j — it also has to build for 1.8.9.
		QuickRebindLog.wire(LOGGER::info, LOGGER::error);

		// Core has no idea where the game directory is; it only learns from here.
		SharedPaths.useInstanceDir(FabricLoader.getInstance().getConfigDir());

		config = JsonStore.loadOrDefault(SharedPaths.config(), QuickRebindConfig.class, new QuickRebindConfig());
		config.sanitise();
		instance = loadInstanceConfig();

		// Categories are plain strings here; 26.2 turned them into a type.
		openScreenKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.quickrebind.open", GLFW.GLFW_KEY_F8, "key.categories.misc"));

		// Unbound by default. This one rebinds your entire keyboard on a single
		// press, so it should be a key you chose on purpose rather than one we
		// picked and happened to land on something you already use.
		cycleKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.quickrebind.cycle", GLFW.GLFW_KEY_UNKNOWN, "key.categories.misc"));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> autoApply());

		// Launching with -Dquickrebind.selftest=<file> turns this into a test
		// run instead of a game: see tools/selftest.ps1.
		if (System.getProperty(SELF_TEST_PROPERTY) != null) {
			ClientLifecycleEvents.CLIENT_STARTED.register(QuickRebindClient::runSelfTest);
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Applying from our screen leaves the Key Binds screen stale behind
			// it, and pressing Done does not rebuild that screen — so catch it
			// as it comes back into view.
			ControlsRefresh.onScreenChanged(client);

			while (openScreenKey.consumeClick()) {
				client.setScreen(new QuickRebindScreen(client.screen));
			}

			while (cycleKey.consumeClick()) {
				cycleToNext(client);
			}
		});

		MenuButtons.register();

		LOGGER.info("QuickRebind ready — presets in {}", SharedPaths.presets());
	}

	/**
	 * Applies the next preset in the list without opening anything.
	 *
	 * <p>Order follows the list you see on screen, and the starting point is
	 * whatever was applied here last, so pressing it twice on a two-preset setup
	 * puts you back where you began.
	 */
	public static void cycleToNext(Minecraft client) {
		List<Preset> presets = PresetStore.list();
		Preset next = PresetStore.cycle(presets, instance.lastAppliedId, 1);

		if (next == null) {
			Notify.toast(client, Component.translatable("quickrebind.toast.title"),
					Component.translatable("quickrebind.toast.none"));
			return;
		}

		ApplyResult result = GameBinds.apply(client, next, config.missingBindPolicy);
		Notify.toast(client, Component.translatable("quickrebind.toast.applied", next.name),
				result.newConflicts() > 0
						? Component.translatable("quickrebind.toast.conflicts", result.changed(), result.newConflicts())
						: Component.translatable("quickrebind.toast.changed", result.changed()));
	}

	/**
	 * Reads this install's own settings, seeding them from the old shared ones
	 * the first time.
	 *
	 * <p>Auto-apply used to live in the shared config, which meant the whole
	 * machine had to agree on one answer. The legacy value is copied rather than
	 * moved: every instance migrates independently on its next launch, so they
	 * all start where they were instead of the first one to boot taking it.
	 */
	private static InstanceConfig loadInstanceConfig() {
		Path path = SharedPaths.instanceConfig();
		boolean fresh = !Files.exists(path);
		InstanceConfig loaded = JsonStore.loadOrDefault(path, InstanceConfig.class, new InstanceConfig());
		loaded.sanitise();

		if (fresh && !config.autoApplyPresetId.isEmpty()) {
			loaded.autoApplyPresetId = config.autoApplyPresetId;
			JsonStore.save(path, loaded);
			LOGGER.info("Moved auto-apply into this instance's own config: {}", path);
		}

		return loaded;
	}

	/**
	 * Runs the shared self-test against this version's real keybinds and quits
	 * with its result as the exit code.
	 *
	 * <p>This is the only part of the mod that can only be checked inside the
	 * game. Core's unit tests cover the rules against a fake install, but
	 * whether <em>this</em> Minecraft's KeyMapping answers getName, saveString
	 * and setKey the way the adapter assumes is a question about the running
	 * game, and asking it by hand on every supported version is how it stops
	 * being asked at all.
	 *
	 * <p>Halts rather than returning: a dev client left open needs somebody to
	 * close it, which is exactly the manual step this exists to remove. The
	 * report is on disk before the halt, so nothing depends on the log
	 * surviving the exit.
	 */
	/**
	 * The mod's screens, handed to the self-test to be opened.
	 *
	 * <p>Compiling proves a port used the right method names. Opening each
	 * screen proves the layout underneath them still works on this version,
	 * which is the half that actually moves between Minecraft releases.
	 *
	 * <p>Goes through setScreen rather than calling init directly, because that
	 * is the route a player takes and the one that lays the widgets out.
	 */
	private static List<SelfTest.ScreenCheck> screenChecks(Minecraft client) {
		Preset sample = Preset.of("Self-test", GameBinds.capture(client.options), gameVersion());

		List<SelfTest.ScreenCheck> checks = new ArrayList<>();
		checks.add(screenCheck(client, "the preset list screen opens", () -> new QuickRebindScreen(null)));
		checks.add(screenCheck(client, "the settings screen opens", () -> new QuickRebindSettingsScreen(null)));
		checks.add(screenCheck(client, "the preset details screen opens", () -> new PresetDetailsScreen(null, sample)));
		checks.add(keyBindsRefreshCheck(client));
		return checks;
	}

	/**
	 * The one check here that guards a bug rather than a screen.
	 *
	 * <p>The vanilla Key Binds screen caches each row's key label, so applying a
	 * preset while it is open leaves it showing the old keys until it is rebuilt.
	 * {@link ControlsRefresh} fixes that by reaching a private field by type —
	 * which is exactly the sort of thing that quietly stops working on a new
	 * Minecraft with no compile error to warn anyone.
	 */
	private static SelfTest.ScreenCheck keyBindsRefreshCheck(Minecraft client) {
		return new SelfTest.ScreenCheck() {
			@Override
			public String name() {
				return "the Key Binds screen refreshes when a preset is applied";
			}

			@Override
			public void open() {
				KeyBindsScreen screen = new KeyBindsScreen(null, client.options);
				client.setScreen(screen);

				try {
					if (!ControlsRefresh.refresh(screen)) {
						throw new IllegalStateException("the key bind list could not be found, so a "
								+ "preset applied from the Key Binds screen would leave it showing "
								+ "the old keys");
					}
				} finally {
					client.setScreen(null);
				}
			}
		};
	}

	private static SelfTest.ScreenCheck screenCheck(Minecraft client, String name,
			Supplier<Screen> factory) {
		return new SelfTest.ScreenCheck() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public void open() {
				client.setScreen(factory.get());
				client.setScreen(null);
			}
		};
	}

	private static void runSelfTest(Minecraft client) {
		SelfTest.Report report = SelfTest.run(GameBinds.handles(client.options), gameVersion(), screenChecks(client));
		String text = report.text();

		System.out.println(text);
		System.out.flush();
		LOGGER.info("Self-test result: {}", report.passed() ? "PASS" : "FAIL");

		String target = System.getProperty(SELF_TEST_PROPERTY, "");

		if (!target.isEmpty()) {
			try {
				Path path = Paths.get(target);

				if (path.getParent() != null) {
					Files.createDirectories(path.getParent());
				}

				Files.write(path, text.getBytes(StandardCharsets.UTF_8));
			} catch (IOException e) {
				LOGGER.error("Could not write the self-test report to {}", target, e);
			}
		}

		Runtime.getRuntime().halt(report.passed() ? 0 : 1);
	}

	/**
	 * Applies this instance's launch preset, if it has one. This is the whole
	 * point for anyone who keeps a dedicated instance: point it at your PvP
	 * preset once and the binds are right every time it boots — and the modpack
	 * next door can point at something else.
	 */
	private void autoApply() {
		if (instance.autoApplyPresetId.isEmpty()) {
			return;
		}

		Preset preset = PresetStore.byId(instance.autoApplyPresetId);

		if (preset == null) {
			LOGGER.warn("Auto-apply preset {} is gone, clearing the setting", instance.autoApplyPresetId);
			instance.autoApplyPresetId = "";
			saveInstance();
			return;
		}

		GameBinds.apply(Minecraft.getInstance(), preset, config.missingBindPolicy);
	}
}
