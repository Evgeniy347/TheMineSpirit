package com.theminespirit.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.RefreshClock;
import com.theminespirit.core.ui.ScreenTabs;
import com.theminespirit.core.ui.SettlementTree;
import com.theminespirit.core.ui.SettlementTreeStyle;
import com.theminespirit.core.ui.ShellLayout;
import com.theminespirit.core.ui.TreeDock;
import com.theminespirit.core.ui.TreeViewport;
import com.theminespirit.neoforge.net.SettlementTreeChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static final int STAGE_WIDTH = 60;
    private static final int STAGE_HEIGHT = 16;
    private static final int DEFAULT_STAGES = 4;
    private static final int DOCK_WIDTH = 236;
    private static final int DOCK_MIN_SCREEN = 860;
    private static final int DOCK_GAP = 8;
    private static final int MUTED = 0xFF98A0AB;
    private static final int DIM = 0xFF6B7480;
    private static final int TEXT = 0xFFE8ECF1;
    private static final int GOLD = 0xFFE0B050;
    private static final int LINE = 0xFF2B323B;
    private static final int CHIP_BG = 0xFF171B21;
    private static final int CHIP_ACTIVE = 0xFF262116;

    private final List<ShellTabButton> stageButtons = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private @Nullable SettlementTree tree;
    private SettlementTree.@Nullable Node hoveredNode;
    private @Nullable String selectedNodeId;
    private final @Nullable String focusNodeId;
    private int selectedStage = 1;
    private int refreshTicks;
    private int ageTicks;
    private RefreshClock clock = RefreshClock.NEVER;
    private boolean answered;
    private boolean offline;
    private boolean showRequiredOnly;
    private boolean panning;
    private int zoomSteps;
    private double panX;
    private double panY;

    private record Hit(BoardLayout.Rect rect, Runnable action) {}

    public SettlementTreeScreen() {
        this(null);
    }

    public SettlementTreeScreen(@Nullable String focusNodeId) {
        super(Component.translatable(KEY + "title"));
        this.focusNodeId = focusNodeId;
    }

    void show(SettlementTree received) {
        boolean changed = received.stage() != selectedStage;
        selectedStage = received.stage();
        tree = received;
        if (selectedNodeId == null
                && focusNodeId != null
                && received.node(focusNodeId).isPresent()) {
            selectedNodeId = focusNodeId;
        }
        answered = true;
        offline = false;
        clock = RefreshClock.at(ageTicks);
        if (changed || received.stages() != Math.max(1, stageButtons.size())) {
            rebuildWidgets();
        }
        if (selectedNodeId == null) {
            selectedNodeId = received.nodes().get(0).id();
        }
        clampPan();
    }

    @Override
    protected void init() {
        stageButtons.clear();
        ScreenTabButtons.add(this::addRenderableWidget, width, ScreenTabs.Tab.TREE);
        int stages = tree == null ? DEFAULT_STAGES : tree.stages();
        for (int stage = 1; stage <= stages; stage++) {
            final int target = stage;
            BoardLayout.Rect rect = stageRect(stage);
            Button.Builder builder = Button.builder(
                            Component.translatable(KEY + "tab", stage), pressed -> select(target))
                    .bounds(rect.x(), rect.y(), rect.width(), rect.height());
            ShellTabButton button = new ShellTabButton(builder, stage == selectedStage);
            button.active = stage != selectedStage;
            stageButtons.add(button);
            addRenderableWidget(button);
        }
        if (!answered || tree == null) {
            requestStage();
        }
        clampPan();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ShellPainter.background(graphics, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        SettlementTree model = tree;
        ShellPainter.titles(
                graphics,
                font,
                title,
                Component.translatable(
                        KEY + "subtitle", selectedStage, model == null ? DEFAULT_STAGES : model.stages()));
        ShellPainter.footer(
                graphics, font, Component.translatable(ShellPainter.KEY + "hint.tree"), clock, ageTicks, width, height);
        hits.clear();
        drawToolbar(graphics, model);
        BoardLayout.Rect area = treeArea();
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), 0xFF0E1114);
        graphics.fill(area.x(), area.y(), area.right(), area.y() + 1, 0xFF20262D);
        graphics.fill(area.x(), area.bottom() - 1, area.right(), area.bottom(), 0xFF20262D);
        hoveredNode = null;
        if (model == null) {
            String state = offline ? KEY + "offline" : answered ? KEY + "empty" : KEY + "loading";
            graphics.drawCenteredString(
                    font,
                    Component.translatable(state),
                    area.x() + area.width() / 2,
                    area.y() + area.height() / 2,
                    SettlementTreeStyle.MUTED);
            drawDock(graphics, null);
            return;
        }
        SettlementTree shown = showRequiredOnly ? model.withoutOptional() : model;
        TreeViewport viewport = viewport(area, shown);
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        hoveredNode = SettlementTreeView.draw(
                graphics,
                font,
                shown,
                area,
                viewport.scale(),
                (int) Math.round(panX),
                (int) Math.round(panY),
                selectedNodeId,
                new SettlementTreeView.Viewpoint(mouseX, mouseY));
        graphics.disableScissor();
        drawDock(graphics, shown);
        if (hoveredNode != null) {
            graphics.renderComponentTooltip(font, SettlementTreeView.tooltip(hoveredNode), mouseX, mouseY);
        }
    }

    private void drawToolbar(GuiGraphics graphics, @Nullable SettlementTree model) {
        BoardLayout.Rect toolbar = ShellLayout.of(width, height).toolbar();
        int x = toolbar.x() + Math.max(1, stageButtons.size()) * (STAGE_WIDTH + ScreenTabs.GAP) + 6;
        int y = toolbar.y();
        if (model == null) {
            return;
        }
        SettlementTree shown = showRequiredOnly ? model.withoutOptional() : model;
        int requiredTotal = 0;
        int requiredDone = 0;
        int optionalTotal = 0;
        int optionalDone = 0;
        int requiredPercent = 0;
        for (SettlementTree.Node node : shown.nodes()) {
            if (node.optional()) {
                optionalTotal++;
                if (node.state() == SettlementTree.State.READY) {
                    optionalDone++;
                }
            } else if (node.kind() != SettlementTree.Kind.ROOT) {
                requiredTotal++;
                if (node.state() == SettlementTree.State.READY) {
                    requiredDone++;
                }
            }
        }
        if (requiredTotal > 0) {
            requiredPercent = requiredDone * 100 / requiredTotal;
        }
        boolean narrow = width < 860;
        String summary = Component.translatable(KEY + "summary.required", requiredDone, requiredTotal)
                .getString();
        graphics.drawString(font, summary, x, y + 1, TEXT, false);
        int summaryWidth = font.width(summary);
        graphics.fill(x, y + 12, x + Math.min(110, summaryWidth), y + 15, 0xFF0C0E11);
        graphics.fill(x + 1, y + 13, x + 1 + Math.min(108, summaryWidth) * requiredPercent / 100, y + 14, 0xFF6FA548);
        x += summaryWidth + 10;
        if (!narrow) {
            String optional = Component.translatable(KEY + "summary.optional", optionalDone, optionalTotal)
                    .getString();
            graphics.drawString(font, optional, x, y + 1, MUTED, false);
            x += font.width(optional) + 10;
        }
        int zoomX = toolbar.right() - 120;
        x = Math.max(x, zoomX);
        x += chip(graphics, x, y, Component.literal("−"), false, () -> zoom(-1));
        String percent = viewport(treeArea(), shown).percent() + "%";
        int percentWidth = font.width(percent) + 12;
        graphics.fill(x, y, x + percentWidth, y + 16, CHIP_BG);
        graphics.fill(x, y, x + percentWidth, y + 1, LINE);
        graphics.drawString(font, percent, x + 6, y + 4, TEXT, false);
        x += percentWidth + 5;
        x += chip(graphics, x, y, Component.literal("+"), false, () -> zoom(1));
        if (!narrow && x + 110 < toolbar.right()) {
            chip(
                    graphics,
                    x,
                    y,
                    Component.translatable(KEY + "filter.required"),
                    showRequiredOnly,
                    () -> showRequiredOnly = !showRequiredOnly);
        }
    }

    private int chip(GuiGraphics graphics, int x, int y, Component label, boolean active, Runnable action) {
        int width = font.width(label) + 12;
        graphics.fill(x, y, x + width, y + 16, active ? CHIP_ACTIVE : CHIP_BG);
        graphics.fill(x, y, x + width, y + 1, active ? GOLD : LINE);
        graphics.fill(x, y + 15, x + width, y + 16, active ? GOLD : LINE);
        graphics.drawString(font, label.getString(), x + 6, y + 4, active ? 0xFFF0DFAE : MUTED, false);
        hits.add(new Hit(new BoardLayout.Rect(x, y, width, 16), action));
        return width + 5;
    }

    private void drawDock(GuiGraphics graphics, @Nullable SettlementTree model) {
        BoardLayout.Rect panel = dock();
        if (panel.width() == 0) {
            return;
        }
        TreeDock dock = TreeDock.of(panel);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF00F1216);
        graphics.fill(panel.x(), panel.y(), panel.x() + 1, panel.bottom(), LINE);
        int x = dock.content().x();
        int width = dock.content().width();
        int y = dock.content().y();
        if (model == null) {
            graphics.drawString(font, Component.translatable(KEY + "loading"), x, y, DIM, false);
            return;
        }
        SettlementTree.Node selected = selectedNode(model);
        graphics.drawString(font, Component.translatable(KEY + "dock.title").getString(), x, y, DIM, false);
        y += 13;
        if (selected != null) {
            graphics.drawString(
                    font, TextClip.text(font, Component.literal(selected.title()), width), x, y, TEXT, false);
            y += 13;
            int color = SettlementTreeView.stateColor(selected.state());
            graphics.fill(x, y + 3, x + 5, y + 8, color);
            String state = SettlementTreeView.stateText(selected).getString();
            graphics.drawString(
                    font, TextClip.text(font, Component.literal(state), width - 50), x + 9, y, color, false);
            String level = selected.level() > 0
                    ? Component.translatable(KEY + "level.short", selected.level())
                            .getString()
                    : Component.translatable(KEY + "gate.short").getString();
            graphics.drawString(font, level, x + width - font.width(level), y, MUTED, false);
            y += 14;
            graphics.fill(x, y, x + width, y + 4, 0xFF0C0E11);
            int filled = Math.min(width - 2, (width - 2) * selected.percent() / 100);
            if (filled > 0) {
                graphics.fill(x + 1, y + 1, x + 1 + filled, y + 3, color);
            }
            String percent = selected.percent() + "%";
            graphics.drawString(font, percent, x + width - font.width(percent), y + 5, color, false);
            y += 14;
            SettlementTree.Position at = selected.at();
            if (at != null) {
                graphics.drawString(
                        font,
                        Component.translatable(KEY + "where", at.x(), at.y(), at.z())
                                .getString(),
                        x,
                        y,
                        MUTED,
                        false);
                y += 12;
            }
            if (!selected.missing().isEmpty()) {
                graphics.drawString(
                        font, Component.translatable(KEY + "dock.missing").getString(), x, y, DIM, false);
                y += 12;
                int shown = 0;
                for (Map.Entry<String, Integer> entry : selected.missing().entrySet()) {
                    if (shown >= 4) {
                        graphics.drawString(font, "…", x, y, DIM, false);
                        y += 11;
                        break;
                    }
                    String line = componentName(entry.getKey()) + " ×" + entry.getValue();
                    graphics.drawString(font, TextClip.text(font, Component.literal(line), width), x, y, MUTED, false);
                    y += 11;
                    shown++;
                }
            } else {
                graphics.drawString(
                        font, Component.translatable(KEY + "dock.none").getString(), x, y, DIM, false);
                y += 13;
            }
            y += 4;
            int buttonWidth = width;
            graphics.fill(x, y, x + buttonWidth, y + 14, CHIP_BG);
            graphics.fill(x, y, x + buttonWidth, y + 1, LINE);
            graphics.fill(x, y + 13, x + buttonWidth, y + 14, LINE);
            graphics.drawString(
                    font,
                    TextClip.text(font, Component.translatable(KEY + "dock.map"), buttonWidth - 6),
                    x + 4,
                    y + 3,
                    TEXT,
                    false);
            SettlementTree.Position where = selected.at();
            hits.add(new Hit(
                    new BoardLayout.Rect(x, y, buttonWidth, 14),
                    where == null
                            ? () -> Minecraft.getInstance().setScreen(new TownMapScreen())
                            : () -> Minecraft.getInstance().setScreen(new TownMapScreen(where.x(), where.z()))));
            y += 22;
        }
        graphics.drawString(font, Component.translatable(KEY + "dock.objects").getString(), x, y, DIM, false);
        y += 12;
        int rows = dock.rows(y);
        int shown = 0;
        for (SettlementTree.Node node : model.nodes()) {
            if (shown >= rows) {
                graphics.drawString(
                        font,
                        Component.translatable(KEY + "dock.more", model.nodes().size() - shown)
                                .getString(),
                        x,
                        y,
                        DIM,
                        false);
                break;
            }
            shown++;
            boolean on = node.id().equals(selectedNodeId);
            if (on) {
                graphics.fill(x - 3, y - 2, x + width + 3, y + 14, 0x14E0B050);
            }
            int color = SettlementTreeView.stateColor(node.state());
            graphics.fill(x, y + 4, x + 4, y + 8, color);
            graphics.drawString(
                    font,
                    TextClip.text(font, Component.literal(node.title()), width - 48),
                    x + 9,
                    y,
                    on ? TEXT : MUTED,
                    false);
            String tail = node.optional()
                    ? Component.translatable(KEY + "dock.optional_short").getString()
                    : node.percent() + "%";
            graphics.drawString(font, tail, x + width - font.width(tail), y, node.optional() ? DIM : color, false);
            hits.add(new Hit(new BoardLayout.Rect(x - 3, y - 2, width + 6, 17), () -> selectedNodeId = node.id()));
            y += 18;
        }
        graphics.drawString(
                font,
                TextClip.text(
                        font,
                        Component.translatable(KEY + "dock.legend"),
                        dock.legend().width()),
                dock.legend().x(),
                dock.legend().y(),
                DIM,
                false);
    }

    private SettlementTree.@Nullable Node selectedNode(SettlementTree model) {
        if (selectedNodeId != null) {
            for (SettlementTree.Node node : model.nodes()) {
                if (node.id().equals(selectedNodeId)) {
                    return node;
                }
            }
        }
        List<SettlementTree.Node> nodes = model.nodes();
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    private String componentName(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1).replace('_', ' ') : id.replace('_', ' ');
    }

    private void zoom(int direction) {
        TreeViewport viewport =
                viewport(treeArea(), tree == null ? null : (showRequiredOnly ? tree.withoutOptional() : tree));
        if (viewport == null) {
            return;
        }
        zoomSteps = (direction > 0 ? viewport.zoomIn() : viewport.zoomOut()).steps();
        clampPan();
    }

    private @Nullable TreeViewport viewport(BoardLayout.Rect area, @Nullable SettlementTree model) {
        if (model == null) {
            return null;
        }
        return zoomSteps == 0
                ? TreeViewport.fit(area.width(), area.height(), model.width(), model.height())
                : new TreeViewport(area.width(), area.height(), model.width(), model.height(), zoomSteps);
    }

    private void clampPan() {
        SettlementTree model = tree;
        if (model == null) {
            panX = 0;
            panY = 0;
            return;
        }
        SettlementTree shown = showRequiredOnly ? model.withoutOptional() : model;
        TreeViewport viewport =
                new TreeViewport(treeArea().width(), treeArea().height(), shown.width(), shown.height(), zoomSteps);
        panX = TreeViewport.clampOffset(panX, treeArea().width(), viewport.scaledWidth());
        panY = TreeViewport.clampOffset(panY, treeArea().height(), viewport.scaledHeight());
    }

    private BoardLayout.Rect treeArea() {
        BoardLayout.Rect body = ShellLayout.of(width, height).body();
        int dock = dockWidth(body);
        int gap = dock > 0 ? DOCK_GAP : 0;
        return new BoardLayout.Rect(body.x(), body.y(), Math.max(1, body.width() - dock - gap), body.height());
    }

    private BoardLayout.Rect dock() {
        BoardLayout.Rect body = ShellLayout.of(width, height).body();
        int dock = dockWidth(body);
        return new BoardLayout.Rect(body.right() - dock, body.y(), dock, body.height());
    }

    private int dockWidth(BoardLayout.Rect body) {
        if (width < DOCK_MIN_SCREEN) {
            return 0;
        }
        return Math.min(DOCK_WIDTH, Math.max(0, body.width() - 120));
    }

    private BoardLayout.Rect stageRect(int stage) {
        BoardLayout.Rect toolbar = ShellLayout.of(width, height).toolbar();
        return new BoardLayout.Rect(
                toolbar.x() + (stage - 1) * (STAGE_WIDTH + ScreenTabs.GAP), toolbar.y(), STAGE_WIDTH, STAGE_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : hits) {
                if (hit.rect().contains((int) Math.floor(mouseX), (int) Math.floor(mouseY))) {
                    hit.action().run();
                    return true;
                }
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        SettlementTree.Node hovered = hoveredNode;
        if (button == 0 && hovered != null) {
            selectedNodeId = hovered.id();
            return true;
        }
        panning = button == 0 && treeArea().contains((int) mouseX, (int) mouseY);
        return panning;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning && button == 0 && tree != null) {
            panX += dragX;
            panY += dragY;
            clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tree != null && treeArea().contains((int) Math.floor(mouseX), (int) Math.floor(mouseY))) {
            zoom(scrollY > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        ageTicks++;
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
        selectedNodeId = null;
        zoomSteps = 0;
        panX = 0;
        panY = 0;
        refreshTicks = 0;
        rebuildWidgets();
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
