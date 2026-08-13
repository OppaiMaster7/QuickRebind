package com.bogdan.quickrebind.gui;

import java.util.function.Consumer;

import com.bogdan.quickrebind.core.PresetStore;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/** Asks for a preset name. Used both by "save current binds" and by rename. */
public class NamePromptScreen extends Screen {
	private static final int FIELD_WIDTH = 200;

	private final Screen parent;
	private final Component prompt;
	private final String initialValue;
	private final Consumer<String> onConfirm;

	private EditBox nameField;
	private Button confirmButton;

	public NamePromptScreen(Screen parent, Component title, Component prompt,
			String initialValue, Consumer<String> onConfirm) {
		super(title);
		this.parent = parent;
		this.prompt = prompt;
		this.initialValue = initialValue == null ? "" : initialValue;
		this.onConfirm = onConfirm;
	}

	@Override
	protected void init() {
		nameField = new EditBox(font, width / 2 - FIELD_WIDTH / 2, height / 2 - 10,
				FIELD_WIDTH, 20, Component.translatable("quickrebind.prompt.field"));
		nameField.setMaxLength(PresetStore.MAX_NAME_LENGTH);
		nameField.setValue(initialValue);
		nameField.setResponder(value -> refreshConfirmState());
		addRenderableWidget(nameField);
		setInitialFocus(nameField);

		confirmButton = addRenderableWidget(Button.builder(
				Component.translatable("gui.done"), b -> confirm())
				.bounds(width / 2 - 154, height / 2 + 24, 150, 20)
				.build());

		addRenderableWidget(Button.builder(
				Component.translatable("gui.cancel"), b -> onClose())
				.bounds(width / 2 + 4, height / 2 + 24, 150, 20)
				.build());

		refreshConfirmState();
	}

	private void refreshConfirmState() {
		confirmButton.active = !nameField.getValue().trim().isEmpty();
	}

	private void confirm() {
		String value = nameField.getValue().trim();

		if (value.isEmpty()) {
			return;
		}

		// Hand control back before running the action, so the action is free to
		// open a screen of its own.
		minecraft.setScreen(parent);
		onConfirm.accept(value);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
				&& !nameField.getValue().trim().isEmpty()) {
			confirm();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 1.20.1's Screen.render does not draw a background (1.21 added that),
		// so without this the screen is see-through over whatever opened it.
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 60, 0xFFFFFFFF);
		graphics.drawCenteredString(font, prompt, width / 2, height / 2 - 34, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
