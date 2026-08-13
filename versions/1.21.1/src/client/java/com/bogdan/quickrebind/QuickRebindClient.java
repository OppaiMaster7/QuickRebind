package com.bogdan.quickrebind;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bogdan.quickrebind.config.QuickRebindConfig;
import com.bogdan.quickrebind.core.JsonStore;
import com.bogdan.quickrebind.core.Preset;
import com.bogdan.quickrebind.core.PresetStore;
import com.bogdan.quickrebind.core.QuickRebindLog;
import com.bogdan.quickrebind.core.SharedPaths;
import com.bogdan.quickrebind.gui.MenuButtons;
import com.bogdan.quickrebind.gui.QuickRebindScreen;
import com.bogdan.quickrebind.platform.GameBinds;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public class QuickRebindClient implements ClientModInitializer {
	public static final String MOD_ID = "quickrebind";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static QuickRebindConfig config;
	private static KeyMapping openScreenKey;

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
		// Core can't depend on slf4j — it also has to build for 1.8.9.
		QuickRebindLog.wire(LOGGER::info, LOGGER::error);

		config = JsonStore.loadOrDefault(SharedPaths.config(), QuickRebindConfig.class, new QuickRebindConfig());
		config.sanitise();

		// Categories are plain strings here; 26.2 turned them into a type.
		openScreenKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.quickrebind.open", GLFW.GLFW_KEY_F8, "key.categories.misc"));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> autoApply());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openScreenKey.consumeClick()) {
				client.setScreen(new QuickRebindScreen(client.screen));
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
		if (config.autoApplyPresetId.isEmpty()) {
			return;
		}

		Preset preset = PresetStore.byId(config.autoApplyPresetId);

		if (preset == null) {
			LOGGER.warn("Auto-apply preset {} is gone, clearing the setting", config.autoApplyPresetId);
			config.autoApplyPresetId = "";
			saveConfig();
			return;
		}

		GameBinds.apply(Minecraft.getInstance(), preset, config.missingBindPolicy);
	}
}
