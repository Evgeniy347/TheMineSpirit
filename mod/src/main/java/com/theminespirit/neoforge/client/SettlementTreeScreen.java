package com.theminespirit.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.ScreenTabs;
import com.theminespirit.core.ui.SettlementTree;
import com.theminespirit.core.ui.SettlementTreeStyle;
import com.theminespirit.neoforge.net.SettlementTreeChannel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

public final class SettlementTreeScreen extends Screen {
    private static final String KEY = "theminespirit.tree.";
    private static final int REFRESH_TICKS = 20;
    private static final int STAGE_TOP = 24;
    private static final int STAGE_WIDTH = 64;
    private static final int STAGE_HEIGHT = 16;
    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int SCROLL_STEP = 20;
    private static final int DEFAULT_STAGES = 4;

    private final List<Button> stageButtons = new ArrayList<>();
    private @Nullable BoardLayout layout;
    private @Nullable SettlementTree tree;
    private int selectedStage = 1;
    private int refreshTicks;
    private boolean answered;
    private boolean offline;
    private boolean panning;
    private double offsetX;
    private double offsetY;

    public SettlementTreeScreen() {
        super(Component.translatable(KEY + "title"));
    }

    void show(SettlementTree received) {
        boolean changed = received.stage() != selectedStage;
        selectedStage = received.stage();
        tree = received;
        answered = true;
        offline = false;
        if (changed || received.stages() != Math.max(1, stageButtons.size())) {
            rebuildWidgets();
        }
        clampOffsets();
    }

    @Override
    protected void init() {
        layout = BoardLayout.of(width, height);
        stageButtons.clear();
        ScreenTabButtons.add(this::addRenderableWidget, width, ScreenTabs.Tab.TREE);
        int stages = tree == null ? DEFAULT_STAGES : tree.stages();
        for (int stage = 1; stage <= stages; stage++) {
            final int target = stage;
            BoardLayout.Rect rect = stageRect(stage);
            Button button = Button.builder(Component.translatable(KEY + "tab", stage), pressed -> select(target))
                    .bounds(rect.x(), rect.y(), rect.width(), rect.height())
                    .build();
            button.active = stage != selectedStage;
            stageButtons.add(button);
            addRenderableWidget(button);
        }
        if (!answered || tree == null) {
            requestStage();
        }
        clampOffsets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        BoardLayout current = layout;
        if (current == null) {
            return;
        }
        BoardLayout.Rect window = current.window();
        BoardLayout.Rect inner = current.inner();
        BoardPanel.blitPanel(graphics, window);
        BoardPanel.drawBackground(graphics, inner);
        graphics.drawString(font, title, inner.x() + 4, window.y() + TITLE_Y, TITLE_COLOR, false);
        SettlementTree model = tree;
        if (model == null) {
            String state = offline ? KEY + "offline" : answered ? KEY + "empty" : KEY + "loading";
            graphics.drawCenteredString(
                    font,
                    Component.translatable(state),
                    inner.x() + inner.width() / 2,
                    inner.y() + inner.height() / 2,
                    SettlementTreeStyle.MUTED);
            return;
        }
        graphics.enableScissor(inner.x(), inner.y(), inner.x() + inner.width(), inner.y() + inner.height());
        SettlementTree.Node hovered = SettlementTreeView.draw(
                graphics,
                font,
                model,
                inner,
                new SettlementTreeView.Viewpoint((int) Math.round(offsetX), (int) Math.round(offsetY)),
                new SettlementTreeView.Viewpoint(mouseX, mouseY));
        graphics.disableScissor();
        if (hovered != null) {
            graphics.renderComponentTooltip(font, SettlementTreeView.tooltip(hovered), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        BoardLayout current = layout;
        panning = button == 0 && current != null && current.inner().contains((int) mouseX, (int) mouseY);
        return panning;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        BoardLayout current = layout;
        if (panning && button == 0 && current != null && tree != null) {
            offsetX += dragX;
            offsetY += dragY;
            clampOffsets();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        BoardLayout current = layout;
        if (tree != null && current != null && current.inner().contains((int) mouseX, (int) mouseY)) {
            offsetY += scrollY * SCROLL_STEP;
            offsetX += scrollX * SCROLL_STEP;
            clampOffsets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        if (offline) {
            return;
        }
        if (++refreshTicks >= REFRESH_TICKS) {
            refreshTicks = 0;
            requestStage();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_J || keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void select(int stage) {
        if (selectedStage == stage) {
            return;
        }
        selectedStage = stage;
        tree = null;
        answered = false;
        offsetX = 0;
        offsetY = 0;
        refreshTicks = 0;
        rebuildWidgets();
    }

    private BoardLayout.Rect stageRect(int stage) {
        return new BoardLayout.Rect(
                BoardLayout.MARGIN + (stage - 1) * (STAGE_WIDTH + ScreenTabs.GAP),
                STAGE_TOP,
                STAGE_WIDTH,
                STAGE_HEIGHT);
    }

    private void clampOffsets() {
        BoardLayout current = layout;
        SettlementTree model = tree;
        if (current == null || model == null) {
            offsetX = 0;
            offsetY = 0;
            return;
        }
        BoardLayout.Rect inner = current.inner();
        int areaWidth = Math.max(1, inner.width() - 2 * SettlementTreeStyle.PAD);
        int areaHeight = Math.max(1, inner.height() - 2 * SettlementTreeStyle.PAD);
        offsetX = clamp(offsetX, areaWidth, model.width());
        offsetY = clamp(offsetY, areaHeight, model.height());
    }

    private static double clamp(double offset, int area, int content) {
        if (content <= area) {
            return (area - content) / 2.0;
        }
        return Math.max(area - content, Math.min(0, offset));
    }

    private void requestStage() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(SettlementTreeChannel.REQUEST)) {
            offline = true;
            answered = true;
            return;
        }
        offline = false;
        PacketDistributor.sendToServer(new SettlementTreeChannel.Request(selectedStage));
    }
}
