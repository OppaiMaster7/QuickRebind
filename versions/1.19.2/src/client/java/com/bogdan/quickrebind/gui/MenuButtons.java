package com.bogdan.quickrebind.gui;

import com.bogdan.quickrebind.QuickRebindClient;
import com.bogdan.quickrebind.config.QuickRebindConfig;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/**
 * The two ways in from someone else's screen: a button on the vanilla controls
 * screens, and the open key working while a screen is up.
 */
public final class MenuButtons {
	private static final int BUTTON_WIDTH = 90;
	private static final int BUTTON_HEIGHT = 20;
	private static final int MARGIN = 6;

	private MenuButtons() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			// Minecraft only feeds key presses to KeyMapping while no screen is
			// open, so the open key does nothing on the title screen or in any
			// menu unless it is also handled per-screen like this.
			ScreenKeyboardEvents.afterKeyPress(screen).register((pressedOn, key, scancode, modifiers) -> {
				if (!isOurs(pressedOn) && QuickRebindClient.openKeyMatches(key, scancode)) {
					client.setScreen(new QuickRebindScreen(pressedOn));
				}
			});

			QuickRebindConfig config = QuickRebindClient.config();

			if (config == null || !wantsButton(config, screen)) {
				return;
			}

			// Top-left, not bottom-left: the footer row on these screens spans
			// width/2 +/- 155, so a button in the bottom corner lands on top of
			// vanilla's own buttons as soon as the window is anything but wide.
			// No Button.builder until 1.19.4 — plain constructor here.
			Button button = new Button(MARGIN, MARGIN, BUTTON_WIDTH, BUTTON_HEIGHT,
					Component.translatable("quickrebind.button.open"),
					b -> client.setScreen(new QuickRebindScreen(screen)));

			Screens.getButtons(screen).add(button);
		});
	}

	/** Our own screens already handle the key; reopening on top of them is nonsense. */
	private static boolean isOurs(Screen screen) {
		return screen instanceof QuickRebindScreen
				|| screen instanceof QuickRebindSettingsScreen
				|| screen instanceof NamePromptScreen;
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
