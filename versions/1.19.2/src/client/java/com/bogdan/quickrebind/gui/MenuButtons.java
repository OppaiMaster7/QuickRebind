package com.bogdan.quickrebind.gui;

import com.bogdan.quickrebind.QuickRebindClient;
import com.bogdan.quickrebind.config.QuickRebindConfig;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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

			// No Button.builder until 1.19.4 — plain constructor here.
			Button button = new Button(6, scaledHeight - 30, 100, 20,
					Component.translatable("quickrebind.button.open"),
					b -> client.setScreen(new QuickRebindScreen(screen)));

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
