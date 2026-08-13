package com.bogdan.quickrebind;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bogdan.quickrebind.config.QuickRebindConfig;
import com.bogdan.quickrebind.gui.MenuButtons;
import com.bogdan.quickrebind.gui.QuickRebindScreen;
import com.bogdan.quickrebind.keys.KeybindService;
import com.bogdan.quickrebind.preset.Preset;
import com.bogdan.quickrebind.preset.PresetStore;
import com.bogdan.quickrebind.storage.JsonStore;
import com.bogdan.quickrebind.storage.SharedPaths;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class QuickRebindClient implements ClientModInitializer {
	public static final String MOD_ID = "quickrebind";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static QuickRebindConfig config;
	private static KeyMapping openScreenKey;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static QuickRebindConfig config() {
		return config;
	}

	public static void saveConfig() {
		config.sanitise();
		JsonStore.save(SharedPaths.config(), config);
	}

	/** The Minecraft version presets are stamped with, purely for display. */
	public static String gameVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}

	@Override
	public void onInitializeClient() {
		config = JsonStore.load(SharedPaths.config(), QuickRebindConfig.class, QuickRebindConfig::new);
		config.sanitise();

		// F8 is free in vanilla, and stays clear of the F6 the playtime mod uses.
		openScreenKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.quickrebind.open", GLFW.GLFW_KEY_F8, KeyMapping.Category.MISC));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> autoApply());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openScreenKey.consumeClick()) {
				client.gui.setScreen(new QuickRebindScreen(client.gui.screen()));
			}
		});

		MenuButtons.register();

		LOGGER.info("QuickRebind ready — presets in {}", SharedPaths.presets());
	}

	/**
	 * Applies the configured launch preset, if there is one. This is the whole
	 * point for anyone who keeps a dedicated modded instance: point it at your
	 * PvP preset once and the binds are right every time it boots.
	 */
	private void autoApply() {
		if (config.autoApplyPresetId.isBlank()) {
			return;
		}

		Preset preset = PresetStore.byId(config.autoApplyPresetId).orElse(null);

		if (preset == null) {
			LOGGER.warn("Auto-apply preset {} is gone, clearing the setting", config.autoApplyPresetId);
			config.autoApplyPresetId = "";
			saveConfig();
			return;
		}

		KeybindService.apply(net.minecraft.client.Minecraft.getInstance(), preset, config.missingBindPolicy);
	}
}
