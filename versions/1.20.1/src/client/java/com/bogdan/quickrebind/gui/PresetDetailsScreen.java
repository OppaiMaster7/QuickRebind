package com.bogdan.quickrebind.gui;

import java.util.List;

import com.bogdan.quickrebind.QuickRebindClient;
import com.bogdan.quickrebind.config.QuickRebindConfig;
import com.bogdan.quickrebind.core.ApplyResult;
import com.bogdan.quickrebind.core.BindDiff;
import com.bogdan.quickrebind.core.Preset;
import com.bogdan.quickrebind.core.PresetStore;
import com.bogdan.quickrebind.core.ShareCode;
import com.bogdan.quickrebind.platform.GameBinds;
import com.bogdan.quickrebind.platform.KeyNames;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What is actually inside a preset, and what applying it would do to you.
 *
 * <p>The list screen can only tell you a preset has 94 binds, which is no help
 * at all in deciding whether it is the one you want. This shows every bind
 * against the key it is on right now, with the ones that would move sorted to
 * the top — so the first thing you read is the answer to "what changes if I
 * press Apply".
 *
 * <p>It also owns the per-preset actions. They used to sit on the list rows as
 * three 42-pixel buttons, which left no room for their labels to say anything;
 * down here there is space to spell them out.
 */
public class PresetDetailsScreen extends Screen {
	private static final int LIST_WIDTH = 320;
	private static final int ROW_HEIGHT = 11;
	private static final int LIST_TOP = 52;
	private static final int KEY_COLUMN = 158;
	private static final int SCROLL_BAR_WIDTH = 4;
	private static final int BUTTON_WIDTH = 104;

	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFA0A0A0;
	private static final int DIM = 0xFF6E6E6E;
	private static final int FAINT = 0xFF555555;
	private static final int GREEN = 0xFF7FDD7F;
	private static final int YELLOW = 0xFFFFDD55;
	private static final int ORANGE = 0xFFFFAA55;
	private static final int RED = 0xFFFF6B6B;

	private final Screen parent;
	private final Preset preset;

	private List<BindDiff> rows = List.of();
	private int moving;
	private int scroll;
	private int visibleRows = 1;
	private Component status;
	private int statusColor = WHITE;

	public PresetDetailsScreen(Screen parent, Preset preset) {
		super(Component.literal(preset.name));
		this.parent = parent;
		this.preset = preset;
	}

	@Override
	protected void init() {
		refreshRows();

		int left = width / 2 - LIST_WIDTH / 2;
		int topRow = height - 52;
		int bottomRow = height - 28;

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.apply"), b -> requestApply())
				.bounds(left, topRow, BUTTON_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.apply", preset.name)))
				.build());

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.update"), b -> requestUpdate())
				.bounds(left + BUTTON_WIDTH + 4, topRow, BUTTON_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.update")))
				.build());

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.rename"), b -> rename())
				.bounds(left + (BUTTON_WIDTH + 4) * 2, topRow, BUTTON_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.rename")))
				.build());

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.copy"), b -> copyCode())
				.bounds(left, bottomRow, BUTTON_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.copy")))
				.build());

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.delete"), b -> confirmDelete())
				.bounds(left + BUTTON_WIDTH + 4, bottomRow, BUTTON_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.delete")))
				.build());

		addRenderableWidget(Button.builder(
				Component.translatable("gui.back"), b -> onClose())
				.bounds(left + (BUTTON_WIDTH + 4) * 2, bottomRow, BUTTON_WIDTH, 20)
				.build());
	}

	private void refreshRows() {
		QuickRebindConfig config = QuickRebindClient.config();
		rows = GameBinds.diff(minecraft.options, preset, config.missingBindPolicy);

		moving = 0;

		for (BindDiff row : rows) {
			if (row.moves()) {
				moving++;
			}
		}

		visibleRows = Math.max(1, (listBottom() - LIST_TOP) / ROW_HEIGHT);
		clampScroll();
	}

	private int listBottom() {
		return height - 60;
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows)));
	}

	// ----------------------------------------------------------------- actions

	private void requestApply() {
		if (!QuickRebindClient.config().confirmBeforeApply) {
			applyNow();
			return;
		}

		minecraft.setScreen(new ConfirmScreen(
				confirmed -> {
					minecraft.setScreen(this);

					if (confirmed) {
						applyNow();
					}
				},
				Component.translatable("quickrebind.confirm.apply.title", preset.name),
				Component.translatable("quickrebind.confirm.apply.message")));
	}

	private void applyNow() {
		ApplyResult result = GameBinds.apply(
				minecraft, preset, QuickRebindClient.config().missingBindPolicy);

		setStatus(Component.translatable("quickrebind.status.applied", preset.name, result.changed()),
				result.newConflicts() > 0 ? YELLOW : GREEN);
		// Everything on screen is measured against the live binds, which just moved.
		rebuildWidgets();
	}

	/**
	 * Re-captures the current keys into this preset.
	 *
	 * <p>The gap this fills: before it existed, changing one key and keeping it
	 * meant saving a second preset, deleting the first and renaming the survivor.
	 */
	private void requestUpdate() {
		minecraft.setScreen(new ConfirmScreen(
				confirmed -> {
					minecraft.setScreen(this);

					if (confirmed) {
						updateNow();
					}
				},
				Component.translatable("quickrebind.confirm.update.title", preset.name),
				Component.translatable("quickrebind.confirm.update.message", moving)));
	}

	private void updateNow() {
		if (PresetStore.updateBinds(preset, GameBinds.capture(minecraft.options))) {
			setStatus(Component.translatable("quickrebind.status.updated", preset.name, preset.size()), GREEN);
		} else {
			setStatus(Component.translatable("quickrebind.status.save_failed"), RED);
		}

		rebuildWidgets();
	}

	private void rename() {
		minecraft.setScreen(new NamePromptScreen(this,
				Component.translatable("quickrebind.prompt.rename.title"),
				Component.translatable("quickrebind.prompt.rename.message"),
				preset.name,
				name -> {
					Preset clash = PresetStore.findByName(name, PresetStore.list());

					if (clash != null && !clash.id.equals(preset.id)) {
						setStatus(Component.translatable("quickrebind.status.name_taken", name), RED);
						rebuildWidgets();
						return;
					}

					preset.name = name;

					if (PresetStore.save(preset)) {
						setStatus(Component.translatable("quickrebind.status.renamed", name), GREEN);
					} else {
						setStatus(Component.translatable("quickrebind.status.save_failed"), RED);
					}

					rebuildWidgets();
				}));
	}

	private void copyCode() {
		minecraft.keyboardHandler.setClipboard(ShareCode.encode(preset));
		setStatus(Component.translatable("quickrebind.status.copied", preset.name), GREEN);
	}

	private void confirmDelete() {
		minecraft.setScreen(new ConfirmScreen(
				confirmed -> {
					if (confirmed && PresetStore.delete(preset)) {
						// The thing this screen is about is gone, so there is
						// nothing left here to come back to.
						minecraft.setScreen(parent);
						return;
					}

					minecraft.setScreen(this);

					if (confirmed) {
						setStatus(Component.translatable("quickrebind.status.delete_failed"), RED);
					}
				},
				Component.translatable("quickrebind.confirm.delete.title", preset.name),
				Component.translatable("quickrebind.confirm.delete.message")));
	}

	private void setStatus(Component line, int color) {
		status = line;
		statusColor = color;
	}

	// ----------------------------------------------------------------- input

	// One scroll amount rather than the x/y pair 1.21 split it into.
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (delta != 0) {
			scroll -= (int) Math.signum(delta) * 3;
			clampScroll();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	// --------------------------------------------------------------- rendering

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 1.20.1's Screen.render does not draw a background (1.21 added that),
		// so without this the screen is see-through over whatever opened it.
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);

		int left = width / 2 - LIST_WIDTH / 2;

		// preset.name rather than the title field, which was fixed at construction
		// and would still read the old name after a rename.
		graphics.drawCenteredString(font, preset.name, width / 2, 14, WHITE);
		graphics.drawCenteredString(font, subtitle(), width / 2, 28, GREY);

		graphics.drawString(font, Component.translatable("quickrebind.details.column_bind").getString(),
				left, 40, DIM);
		graphics.drawString(font, Component.translatable("quickrebind.details.column_key").getString(),
				left + KEY_COLUMN, 40, DIM);

		int last = Math.min(scroll + visibleRows, rows.size());

		for (int index = scroll; index < last; index++) {
			drawRow(graphics, rows.get(index), left, LIST_TOP + (index - scroll) * ROW_HEIGHT);
		}

		drawScrollBar(graphics, left);

		if (status != null) {
			graphics.drawCenteredString(font, status, width / 2, height - 66, statusColor);
		}
	}

	private Component subtitle() {
		Component change = moving == 0
				? Component.translatable("quickrebind.details.no_changes")
				: Component.translatable("quickrebind.details.changes", moving);

		return preset.gameVersion == null || preset.gameVersion.isEmpty()
				? Component.translatable("quickrebind.details.subtitle", preset.size(), change)
				: Component.translatable("quickrebind.details.subtitle_version",
						preset.size(), preset.gameVersion, change);
	}

	private void drawRow(GuiGraphics graphics, BindDiff row, int left, int y) {
		graphics.drawString(font, fit(KeyNames.bindLabel(row.id), KEY_COLUMN - 6), left, y, labelColor(row));
		graphics.drawString(font, fit(keyText(row), LIST_WIDTH - KEY_COLUMN - SCROLL_BAR_WIDTH - 6),
				left + KEY_COLUMN, y, keyColor(row));
	}

	/**
	 * The right-hand column: where the bind is now, and where it is going if
	 * those differ. Only the rows that move earn an arrow, so the ones that do
	 * stand out at a glance.
	 */
	private Component keyText(BindDiff row) {
		switch (row.status) {
			case CHANGED:
			case WOULD_RESET:
				return Component.translatable("quickrebind.details.move",
						KeyNames.keyLabel(row.currentKey), KeyNames.keyLabel(row.targetKey));
			case NOT_INSTALLED:
				return Component.translatable("quickrebind.details.not_here",
						KeyNames.keyLabel(row.targetKey));
			default:
				return KeyNames.keyLabel(row.currentKey);
		}
	}

	private int labelColor(BindDiff row) {
		switch (row.status) {
			case CHANGED:
				return WHITE;
			case WOULD_RESET:
				return WHITE;
			case UNCHANGED:
				return GREY;
			case NOT_IN_PRESET:
				return DIM;
			default:
				return FAINT;
		}
	}

	private int keyColor(BindDiff row) {
		switch (row.status) {
			case CHANGED:
				return YELLOW;
			case WOULD_RESET:
				return ORANGE;
			case UNCHANGED:
				return GREY;
			case NOT_IN_PRESET:
				return DIM;
			default:
				return FAINT;
		}
	}

	private String fit(Component text, int maxWidth) {
		String raw = text.getString();

		if (font.width(raw) <= maxWidth) {
			return raw;
		}

		return font.plainSubstrByWidth(raw, maxWidth - font.width("...")) + "...";
	}

	private void drawScrollBar(GuiGraphics graphics, int left) {
		if (rows.size() <= visibleRows) {
			return;
		}

		int x = left + LIST_WIDTH - SCROLL_BAR_WIDTH;
		int top = LIST_TOP;
		int barHeight = visibleRows * ROW_HEIGHT;
		int thumb = Math.max(8, barHeight * visibleRows / rows.size());
		int travel = barHeight - thumb;
		int offset = travel * scroll / Math.max(1, rows.size() - visibleRows);

		graphics.fill(x, top, x + SCROLL_BAR_WIDTH, top + barHeight, 0x40000000);
		graphics.fill(x, top + offset, x + SCROLL_BAR_WIDTH, top + offset + thumb, 0xFF8B8B8B);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
