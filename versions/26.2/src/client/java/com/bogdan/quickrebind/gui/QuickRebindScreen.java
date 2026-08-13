package com.bogdan.quickrebind.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import com.bogdan.quickrebind.QuickRebindClient;
import com.bogdan.quickrebind.config.QuickRebindConfig;
import com.bogdan.quickrebind.core.ApplyResult;
import com.bogdan.quickrebind.core.MissingBindPolicy;
import com.bogdan.quickrebind.core.Preset;
import com.bogdan.quickrebind.core.PresetStore;
import com.bogdan.quickrebind.core.ShareCode;
import com.bogdan.quickrebind.core.SharedPaths;
import com.bogdan.quickrebind.platform.GameBinds;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** The preset list: apply, rename, share and delete, one row each. */
public class QuickRebindScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int LIST_TOP = 46;
	private static final int LIST_WIDTH = 310;
	// Narrower apply button buys enough room for the row actions to be words
	// rather than cryptic abbreviations.
	private static final int APPLY_WIDTH = 172;
	private static final int SMALL_WIDTH = 42;
	private static final int MAX_ROWS = 8;

	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFA0A0A0;
	private static final int GREEN = 0xFF7FDD7F;
	private static final int YELLOW = 0xFFFFDD55;
	private static final int RED = 0xFFFF6B6B;

	private final Screen parent;

	private List<Preset> presets = List.of();
	private int page;
	private int rowsPerPage = 5;
	private Component status;
	private Component statusDetail;
	private int statusColor = WHITE;

	public QuickRebindScreen(Screen parent) {
		super(Component.translatable("quickrebind.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		presets = PresetStore.list();

		// Reserved below the list, bottom up: two footer rows, two status lines,
		// and the page nav. Held back even on a single page so the row count
		// doesn't change as presets are added.
		int listBottom = height - 100;
		rowsPerPage = Math.max(1, Math.min(MAX_ROWS, (listBottom - LIST_TOP) / ROW_HEIGHT));
		page = Math.max(0, Math.min(page, pageCount() - 1));

		int left = width / 2 - LIST_WIDTH / 2;
		int first = page * rowsPerPage;
		int last = Math.min(first + rowsPerPage, presets.size());

		for (int index = first; index < last; index++) {
			addRow(presets.get(index), left, LIST_TOP + (index - first) * ROW_HEIGHT);
		}

		if (pageCount() > 1) {
			int navY = LIST_TOP + rowsPerPage * ROW_HEIGHT;

			addRenderableWidget(Button.builder(Component.literal("<"), b -> flipPage(-1))
					.bounds(left, navY, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> flipPage(1))
					.bounds(left + LIST_WIDTH - 20, navY, 20, 20).build());
		}

		addFooter(left);
	}

	private void addRow(Preset preset, int left, int y) {
		Button apply = Button.builder(rowLabel(preset), b -> requestApply(preset))
				.bounds(left, y, APPLY_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.apply", preset.name)))
				.build();
		addRenderableWidget(apply);

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.rename"), b -> rename(preset))
				.bounds(left + APPLY_WIDTH + 4, y, SMALL_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.rename")))
				.build());

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.copy"), b -> copyCode(preset))
				.bounds(left + APPLY_WIDTH + 4 + SMALL_WIDTH + 4, y, SMALL_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.copy")))
				.build());

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.delete"), b -> confirmDelete(preset))
				.bounds(left + APPLY_WIDTH + 4 + (SMALL_WIDTH + 4) * 2, y, SMALL_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.delete")))
				.build());
	}

	private void addFooter(int left) {
		int topRow = height - 52;
		int bottomRow = height - 28;

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.save_current"), b -> saveCurrent())
				.bounds(left, topRow, 152, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.save_current")))
				.build());

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.paste"), b -> pasteCode())
				.bounds(left + 158, topRow, 152, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.paste")))
				.build());

		Button undo = Button.builder(Component.translatable("quickrebind.button.undo"), b -> undo())
				.bounds(left, bottomRow, 74, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.undo")))
				.build();
		undo.active = GameBinds.undoSnapshot() != null;
		addRenderableWidget(undo);

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.folder"), b -> openFolder())
				.bounds(left + 78, bottomRow, 74, 20)
				.tooltip(Tooltip.create(Component.translatable("quickrebind.tip.folder",
						SharedPaths.presets().toString())))
				.build());

		addRenderableWidget(Button.builder(
				Component.translatable("quickrebind.button.settings"),
				b -> minecraft.gui.setScreen(new QuickRebindSettingsScreen(this)))
				.bounds(left + 156, bottomRow, 74, 20)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(left + 234, bottomRow, 76, 20)
				.build());
	}

	private Component rowLabel(Preset preset) {
		return Component.translatable("quickrebind.row.label", preset.name, preset.size());
	}

	private int pageCount() {
		return Math.max(1, (presets.size() + rowsPerPage - 1) / rowsPerPage);
	}

	private void flipPage(int direction) {
		page = Math.floorMod(page + direction, pageCount());
		rebuildWidgets();
	}

	// ----------------------------------------------------------------- actions

	private void requestApply(Preset preset) {
		QuickRebindConfig config = QuickRebindClient.config();

		if (!config.confirmBeforeApply) {
			applyNow(preset);
			return;
		}

		minecraft.gui.setScreen(new ConfirmScreen(
				confirmed -> {
					minecraft.gui.setScreen(this);

					if (confirmed) {
						applyNow(preset);
					}
				},
				Component.translatable("quickrebind.confirm.apply.title", preset.name),
				Component.translatable("quickrebind.confirm.apply.message")));
	}

	private void applyNow(Preset preset) {
		ApplyResult result = GameBinds.apply(
				minecraft, preset, QuickRebindClient.config().missingBindPolicy);

		setStatus(Component.translatable("quickrebind.status.applied", preset.name, result.changed()),
				detailFor(result),
				result.conflicts > 0 || result.unreadable > 0 ? YELLOW : GREEN);
		rebuildWidgets();
	}

	private Component detailFor(ApplyResult result) {
		if (result.conflicts > 0) {
			return Component.translatable("quickrebind.status.conflicts", result.conflicts);
		}

		if (result.notInstalled > 0) {
			return Component.translatable("quickrebind.status.not_installed", result.notInstalled);
		}

		if (result.unreadable > 0) {
			return Component.translatable("quickrebind.status.unreadable", result.unreadable);
		}

		return Component.translatable("quickrebind.status.already_correct", result.alreadyCorrect);
	}

	private void saveCurrent() {
		String suggested = PresetStore.uniqueName(
				Component.translatable("quickrebind.preset.default_name").getString(), presets);

		minecraft.gui.setScreen(new NamePromptScreen(this,
				Component.translatable("quickrebind.prompt.save.title"),
				Component.translatable("quickrebind.prompt.save.message"),
				suggested,
				name -> {
					Preset preset = Preset.of(name, GameBinds.capture(minecraft.options),
							QuickRebindClient.gameVersion());

					if (PresetStore.save(preset)) {
						setStatus(Component.translatable("quickrebind.status.saved", preset.name, preset.size()),
								Component.translatable("quickrebind.status.saved_where"), GREEN);
					} else {
						setStatus(Component.translatable("quickrebind.status.save_failed"), null, RED);
					}

					rebuildWidgets();
				}));
	}

	private void rename(Preset preset) {
		minecraft.gui.setScreen(new NamePromptScreen(this,
				Component.translatable("quickrebind.prompt.rename.title"),
				Component.translatable("quickrebind.prompt.rename.message"),
				preset.name,
				name -> {
					preset.name = name;

					if (PresetStore.save(preset)) {
						setStatus(Component.translatable("quickrebind.status.renamed", name), null, GREEN);
					} else {
						setStatus(Component.translatable("quickrebind.status.save_failed"), null, RED);
					}

					rebuildWidgets();
				}));
	}

	private void copyCode(Preset preset) {
		minecraft.keyboardHandler.setClipboard(ShareCode.encode(preset));

		// Name the file too: sending the .json is the other way to share, and
		// otherwise you'd have to guess which one it is in the folder.
		setStatus(Component.translatable("quickrebind.status.copied", preset.name),
				Component.translatable("quickrebind.status.copied_hint",
						preset.fileName == null ? "?" : preset.fileName),
				GREEN);
	}

	private void pasteCode() {
		try {
			Preset imported = ShareCode.decode(minecraft.keyboardHandler.getClipboard());
			imported.name = PresetStore.uniqueName(imported.name, presets);

			if (PresetStore.save(imported)) {
				setStatus(Component.translatable("quickrebind.status.imported", imported.name, imported.size()),
						null, GREEN);
			} else {
				setStatus(Component.translatable("quickrebind.status.save_failed"), null, RED);
			}

			rebuildWidgets();
		} catch (IllegalArgumentException e) {
			// The message is a translation key — see ShareCode.decode.
			setStatus(Component.translatable("quickrebind.status.import_failed"),
					Component.translatable(e.getMessage()), RED);
		}
	}

	private void confirmDelete(Preset preset) {
		minecraft.gui.setScreen(new ConfirmScreen(
				confirmed -> {
					minecraft.gui.setScreen(this);

					if (confirmed) {
						if (PresetStore.delete(preset)) {
							setStatus(Component.translatable("quickrebind.status.deleted", preset.name), null, GREY);
						} else {
							setStatus(Component.translatable("quickrebind.status.delete_failed"), null, RED);
						}

						rebuildWidgets();
					}
				},
				Component.translatable("quickrebind.confirm.delete.title", preset.name),
				Component.translatable("quickrebind.confirm.delete.message")));
	}

	private void undo() {
		Preset snapshot = GameBinds.undoSnapshot();

		if (snapshot == null) {
			return;
		}

		// Deliberately LEAVE: the snapshot is a full capture of this install, so
		// there is nothing it could sensibly reset.
		ApplyResult result = GameBinds.apply(minecraft, snapshot, MissingBindPolicy.LEAVE);
		setStatus(Component.translatable("quickrebind.status.undone", result.changed()),
				Component.translatable("quickrebind.status.undone_hint"), GREEN);
		rebuildWidgets();
	}

	private void openFolder() {
		try {
			Files.createDirectories(SharedPaths.presets());
			// openFile rather than openPath: the Path overload only exists from
			// 1.21 on, and this one is present in every version we target.
			Util.getPlatform().openFile(SharedPaths.presets().toFile());
		} catch (IOException e) {
			QuickRebindClient.LOGGER.error("Could not open the presets folder", e);
			setStatus(Component.translatable("quickrebind.status.folder_failed"),
					Component.literal(SharedPaths.presets().toString()), RED);
		}
	}

	private void setStatus(Component line, Component detail, int color) {
		status = line;
		statusDetail = detail;
		statusColor = color;
	}

	// --------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.centeredText(font, title, width / 2, 12, WHITE);

		int conflicts = GameBinds.conflicts(minecraft.options);
		Component subtitle = conflicts > 0
				? Component.translatable("quickrebind.screen.subtitle_conflicts",
						minecraft.options.keyMappings.length, conflicts)
				: Component.translatable("quickrebind.screen.subtitle",
						minecraft.options.keyMappings.length);
		graphics.centeredText(font, subtitle, width / 2, 26, conflicts > 0 ? YELLOW : GREY);

		if (presets.isEmpty()) {
			graphics.centeredText(font, Component.translatable("quickrebind.screen.empty"),
					width / 2, LIST_TOP + 12, GREY);
			graphics.centeredText(font, Component.translatable("quickrebind.screen.empty_hint"),
					width / 2, LIST_TOP + 26, GREY);
		} else if (pageCount() > 1) {
			graphics.centeredText(font,
					Component.translatable("quickrebind.screen.page", page + 1, pageCount()),
					width / 2, LIST_TOP + rowsPerPage * ROW_HEIGHT + 6, GREY);
		}

		if (status != null) {
			graphics.centeredText(font, status, width / 2, height - 76, statusColor);

			if (statusDetail != null) {
				graphics.centeredText(font, statusDetail, width / 2, height - 66, GREY);
			}
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
