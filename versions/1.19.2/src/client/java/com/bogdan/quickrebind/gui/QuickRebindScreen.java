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
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The preset list: one row each, apply on the left and everything else behind
 * Details.
 *
 * <p>The row used to carry Apply plus Rename, Share and Delete squeezed into
 * 42 pixels apiece. Four buttons per row on a screen that can show eight rows
 * is thirty-two things to aim at, and the three small ones are not what anybody
 * opened this screen to do. They moved to {@link PresetDetailsScreen}, which
 * has room to label them properly.
 */
public class QuickRebindScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int LIST_TOP = 46;
	private static final int LIST_WIDTH = 320;
	private static final int DETAILS_WIDTH = 66;
	private static final int APPLY_WIDTH = LIST_WIDTH - DETAILS_WIDTH - 4;
	private static final int FOOTER_WIDTH = 60;
	private static final int FOOTER_GAP = 5;
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

	/**
	 * Hover text. There is no Tooltip class before 1.19.4, so buttons carry an
	 * OnTooltip that draws through the screen instead.
	 */
	private Button.OnTooltip tip(Component text) {
		return (button, pose, mouseX, mouseY) -> renderTooltip(pose, text, mouseX, mouseY);
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

			addRenderableWidget(new Button(left, navY, 20, 20,
					Component.literal("<"), b -> flipPage(-1)));
			addRenderableWidget(new Button(left + LIST_WIDTH - 20, navY, 20, 20,
					Component.literal(">"), b -> flipPage(1)));
		}

		addFooter(left);
	}

	private void addRow(Preset preset, int left, int y) {
		boolean active = isActive(preset);

		addRenderableWidget(new Button(left, y, APPLY_WIDTH, 20,
				rowLabel(preset, active), b -> requestApply(preset),
				tip(active
						? Component.translatable("quickrebind.tip.apply_active", preset.name)
						: Component.translatable("quickrebind.tip.apply", preset.name))));

		addRenderableWidget(new Button(left + APPLY_WIDTH + 4, y, DETAILS_WIDTH, 20,
				Component.translatable("quickrebind.button.details"), b -> openDetails(preset),
				tip(Component.translatable("quickrebind.tip.details"))));
	}

	private void addFooter(int left) {
		int topRow = height - 52;
		int bottomRow = height - 28;
		int half = (LIST_WIDTH - 4) / 2;

		addRenderableWidget(new Button(left, topRow, half, 20,
				Component.translatable("quickrebind.button.save_current"), b -> saveCurrent(),
				tip(Component.translatable("quickrebind.tip.save_current"))));

		addRenderableWidget(new Button(left + half + 4, topRow, half, 20,
				Component.translatable("quickrebind.button.paste"), b -> pasteCode(),
				tip(Component.translatable("quickrebind.tip.paste"))));

		Button undo = new Button(footerX(left, 0), bottomRow, FOOTER_WIDTH, 20,
				Component.translatable("quickrebind.button.undo"), b -> undo(),
				tip(Component.translatable("quickrebind.tip.undo")));
		undo.active = GameBinds.undoSnapshot() != null;
		addRenderableWidget(undo);

		addRenderableWidget(new Button(footerX(left, 1), bottomRow, FOOTER_WIDTH, 20,
				Component.translatable("quickrebind.button.reset"), b -> confirmReset(),
				tip(Component.translatable("quickrebind.tip.reset"))));

		addRenderableWidget(new Button(footerX(left, 2), bottomRow, FOOTER_WIDTH, 20,
				Component.translatable("quickrebind.button.folder"), b -> openFolder(),
				tip(Component.translatable("quickrebind.tip.folder", SharedPaths.presets().toString()))));

		addRenderableWidget(new Button(footerX(left, 3), bottomRow, FOOTER_WIDTH, 20,
				Component.translatable("quickrebind.button.settings"),
				b -> minecraft.setScreen(new QuickRebindSettingsScreen(this))));

		addRenderableWidget(new Button(footerX(left, 4), bottomRow, FOOTER_WIDTH, 20,
				Component.translatable("gui.done"), b -> onClose()));
	}

	private int footerX(int left, int slot) {
		return left + slot * (FOOTER_WIDTH + FOOTER_GAP);
	}

	/** Whether this is the preset this install was last set to. */
	private boolean isActive(Preset preset) {
		return preset.id.equals(QuickRebindClient.instance().lastAppliedId);
	}

	/**
	 * The active preset is drawn green rather than badged with a marker glyph,
	 * which keeps it legible in every font the game might be running.
	 */
	private Component rowLabel(Preset preset, boolean active) {
		Component label = Component.translatable("quickrebind.row.label", preset.name, preset.size());
		return active ? label.copy().withStyle(ChatFormatting.GREEN) : label;
	}

	private int pageCount() {
		return Math.max(1, (presets.size() + rowsPerPage - 1) / rowsPerPage);
	}

	private void flipPage(int direction) {
		page = Math.floorMod(page + direction, pageCount());
		rebuildWidgets();
	}

	// ----------------------------------------------------------------- actions

	private void openDetails(Preset preset) {
		minecraft.setScreen(new PresetDetailsScreen(this, preset));
	}

	private void requestApply(Preset preset) {
		QuickRebindConfig config = QuickRebindClient.config();

		if (!config.confirmBeforeApply) {
			applyNow(preset);
			return;
		}

		minecraft.setScreen(new ConfirmScreen(
				confirmed -> {
					minecraft.setScreen(this);

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
				result.newConflicts() > 0 || result.unreadable > 0 ? YELLOW : GREEN);
		rebuildWidgets();
	}

	private Component detailFor(ApplyResult result) {
		if (result.newConflicts() > 0) {
			return Component.translatable("quickrebind.status.conflicts", result.newConflicts());
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

		minecraft.setScreen(new NamePromptScreen(this,
				Component.translatable("quickrebind.prompt.save.title"),
				Component.translatable("quickrebind.prompt.save.message"),
				suggested,
				name -> {
					// Typing the name of a preset that already exists used to make
					// a second one with an identical label, and no way to tell the
					// two apart in the list. Offer the thing that was almost
					// certainly meant instead.
					Preset existing = PresetStore.findByName(name, presets);

					if (existing != null) {
						confirmOverwrite(existing);
						return;
					}

					saveNew(name);
				}));
	}

	private void saveNew(String name) {
		Preset preset = Preset.of(name, GameBinds.capture(minecraft.options),
				QuickRebindClient.gameVersion());

		if (PresetStore.save(preset)) {
			setStatus(Component.translatable("quickrebind.status.saved", preset.name, preset.size()),
					Component.translatable("quickrebind.status.saved_where"), GREEN);
		} else {
			setStatus(Component.translatable("quickrebind.status.save_failed"), null, RED);
		}

		rebuildWidgets();
	}

	private void confirmOverwrite(Preset existing) {
		minecraft.setScreen(new ConfirmScreen(
				confirmed -> {
					minecraft.setScreen(this);

					if (!confirmed) {
						return;
					}

					if (PresetStore.updateBinds(existing, GameBinds.capture(minecraft.options))) {
						setStatus(Component.translatable("quickrebind.status.updated",
								existing.name, existing.size()), null, GREEN);
					} else {
						setStatus(Component.translatable("quickrebind.status.save_failed"), null, RED);
					}

					rebuildWidgets();
				},
				Component.translatable("quickrebind.confirm.overwrite.title", existing.name),
				Component.translatable("quickrebind.confirm.overwrite.message", existing.size())));
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

	/**
	 * Back to the keys Minecraft ships with. Snapshots first like any other
	 * apply, so this is as reversible as everything else here.
	 */
	private void confirmReset() {
		minecraft.setScreen(new ConfirmScreen(
				confirmed -> {
					minecraft.setScreen(this);

					if (confirmed) {
						int changed = GameBinds.resetAllToDefault(minecraft);
						setStatus(Component.translatable("quickrebind.status.reset", changed),
								Component.translatable("quickrebind.status.reset_hint"), GREY);
						rebuildWidgets();
					}
				},
				Component.translatable("quickrebind.confirm.reset.title"),
				Component.translatable("quickrebind.confirm.reset.message")));
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
	public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
		// Pre-GuiGraphics: the background is ours to draw.
		renderBackground(pose);
		super.render(pose, mouseX, mouseY, partialTick);

		drawCenteredString(pose, font, title, width / 2, 12, WHITE);

		int conflicts = GameBinds.conflicts(minecraft.options);
		Component subtitle = conflicts > 0
				? Component.translatable("quickrebind.screen.subtitle_conflicts",
						minecraft.options.keyMappings.length, conflicts)
				: Component.translatable("quickrebind.screen.subtitle",
						minecraft.options.keyMappings.length);
		drawCenteredString(pose, font, subtitle, width / 2, 26, GREY);

		if (presets.isEmpty()) {
			drawCenteredString(pose, font, Component.translatable("quickrebind.screen.empty"),
					width / 2, LIST_TOP + 12, GREY);
			drawCenteredString(pose, font, Component.translatable("quickrebind.screen.empty_hint"),
					width / 2, LIST_TOP + 26, GREY);
		} else if (pageCount() > 1) {
			drawCenteredString(pose, font,
					Component.translatable("quickrebind.screen.page", page + 1, pageCount()),
					width / 2, LIST_TOP + rowsPerPage * ROW_HEIGHT + 6, GREY);
		}

		if (status != null) {
			drawCenteredString(pose, font, status, width / 2, height - 76, statusColor);

			if (statusDetail != null) {
				drawCenteredString(pose, font, statusDetail, width / 2, height - 66, GREY);
			}
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
