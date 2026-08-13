package com.bogdan.quickrebind.gui;

import com.bogdan.quickrebind.QuickRebindClient;
import com.bogdan.quickrebind.config.QuickRebindConfig;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
// These sit under screens.options.controls from 1.21 on, but plain
// screens.controls here.
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/**
 * Puts a QuickRebind button on the vanilla controls screens — the place you
 * already go when you want to change a bind.
 */
public final class MenuButtons {
	private MenuButtons() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			QuickRebindConfig config = QuickRebindClient.config();

			if (config == null || !wantsButton(config, screen)) {
				return;
			}

			// Bottom-left, clear of the centred Done button both screens use.
			Button button = Button.builder(
					Component.translatable("quickrebind.button.open"),
					b -> client.setScreen(new QuickRebindScreen(screen)))
					.bounds(6, scaledHeight - 30, 100, 20)
					.build();

			Screens.getButtons(screen).add(button);
		});
	}

	private static boolean wantsButton(QuickRebindConfig config, Screen screen) {
		if (screen instanceof KeyBindsScreen) {
			return config.buttonInKeyBinds;
		}

		if (screen instanceof ControlsScreen) {
			return config.buttonInControls;
		}

		return false;
	}
}
