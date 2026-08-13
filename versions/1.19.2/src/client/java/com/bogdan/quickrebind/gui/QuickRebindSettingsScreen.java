package com.bogdan.quickrebind.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.bogdan.quickrebind.QuickRebindClient;
import com.bogdan.quickrebind.config.QuickRebindConfig;
import com.bogdan.quickrebind.core.MissingBindPolicy;
import com.bogdan.quickrebind.core.Preset;
import com.bogdan.quickrebind.core.PresetStore;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class QuickRebindSettingsScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;

	private final Screen parent;

	public QuickRebindSettingsScreen(Screen parent) {
		super(Component.translatable("quickrebind.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		QuickRebindConfig config = QuickRebindClient.config();

		option(0, "quickrebind.option.missing_policy", config.missingBindPolicy,
				List.of(MissingBindPolicy.values()), QuickRebindSettingsScreen::policyLabel,
				value -> config.missingBindPolicy = value);

		toggle(1, "quickrebind.option.confirm", config.confirmBeforeApply,
				value -> config.confirmBeforeApply = value);

		autoApplyOption(2, config);

		toggle(3, "quickrebind.option.button_keybinds", config.buttonInKeyBinds,
				value -> config.buttonInKeyBinds = value);

		toggle(4, "quickrebind.option.button_controls", config.buttonInControls,
				value -> config.buttonInControls = value);

		addRenderableWidget(new Button(width / 2 - 75, height - 28, BUTTON_WIDTH, BUTTON_HEIGHT,
				Component.translatable("gui.done"), b -> onClose()));
	}

	/** Cycles through "off" plus every saved preset. */
	private void autoApplyOption(int slot, QuickRebindConfig config) {
		List<String> ids = new ArrayList<>();
		ids.add("");

		List<Preset> presets = PresetStore.list();

		for (Preset preset : presets) {
			ids.add(preset.id);
		}

		String current = ids.contains(config.autoApplyPresetId) ? config.autoApplyPresetId : "";

		option(slot, "quickrebind.option.auto_apply", current, ids,
				id -> id.isEmpty()
						? Component.translatable("quickrebind.option.auto_apply.off")
						: Component.literal(nameOf(presets, id)),
				value -> config.autoApplyPresetId = value);
	}

	/** Core has no access to Component, so the display text lives on this side. */
	private static Component policyLabel(MissingBindPolicy policy) {
		return Component.translatable(policy == MissingBindPolicy.LEAVE
				? "quickrebind.policy.leave"
				: "quickrebind.policy.reset");
	}

	private static String nameOf(List<Preset> presets, String id) {
		return presets.stream()
				.filter(preset -> preset.id.equals(id))
				.map(preset -> preset.name)
				.findFirst()
				.orElse(id);
	}

	// --------------------------------------------------------------- widgets

	private int slotY(int slot) {
		return 44 + slot * 24;
	}

	// No AbstractWidget.setTooltip on this version — the hint text that the
	// newer builds show on hover is dropped here rather than reimplemented.
	private <T> void option(int slot, String key, T current, List<T> values,
			Function<T, Component> naming, Consumer<T> setter) {
		addRenderableWidget(CycleButton.builder(naming)
				.withValues(values)
				.withInitialValue(current)
				.create(width / 2 - BUTTON_WIDTH, slotY(slot), BUTTON_WIDTH * 2, BUTTON_HEIGHT,
						Component.translatable(key),
						(widget, value) -> {
							setter.accept(value);
							QuickRebindClient.saveConfig();
						}));
	}

	private void toggle(int slot, String key, boolean current, Consumer<Boolean> setter) {
		addRenderableWidget(CycleButton.onOffBuilder(current)
				.create(width / 2 - BUTTON_WIDTH, slotY(slot), BUTTON_WIDTH * 2, BUTTON_HEIGHT,
						Component.translatable(key),
						(widget, value) -> {
							setter.accept(value);
							QuickRebindClient.saveConfig();
						}));
	}

	@Override
	public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
		renderBackground(pose);
		super.render(pose, mouseX, mouseY, partialTick);
		drawCenteredString(pose, font, title, width / 2, 16, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		QuickRebindClient.saveConfig();
		minecraft.setScreen(parent);
	}
}
