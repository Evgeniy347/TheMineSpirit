package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.BoardColumns;
import com.theminespirit.core.ui.BoardFeed;
import com.theminespirit.core.ui.BoardFilter;
import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.BoardPageQuery;
import com.theminespirit.core.ui.BoardStage;
import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardTileGroup;
import com.theminespirit.core.ui.BoardWaitReasons;
import com.theminespirit.core.ui.ScreenTabs;
import com.theminespirit.neoforge.net.BoardViewChannel;
import com.theminespirit.neoforge.net.BuildFingerprint;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

public final class BoardScreen extends Screen {
    private static final String KEY = ModIdentity.MOD_ID + ".board.";
    private static final int SCROLL_STEP = 12;
    private static final int REFRESH_TICKS = 20;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFA0A0A0;
    private static final List<BoardTileGroup> GROUPS = List.of(BoardTileGroup.values());

    private BoardLayout layout = BoardLayout.of(0, 0);
    private final BoardFeed feed = new BoardFeed();
    private @Nullable BoardStage stage;
    private List<BoardColumns.BoardColumn> columns = List.of();
    private Map<String, BoardTile> tilesById = Map.of();
    private Map<String, List<BoardWaitReasons.Wait>> waitReasons = Map.of();
    private final double[] scroll = new double[GROUPS.size()];
    private @Nullable Button statusButton;
    private @Nullable Button spiritButton;
    private boolean offline;
    private int refreshTicks;

    public BoardScreen() {
        super(Component.translatable(KEY + "title"));
    }

    public void show(BoardViewChannel.Reply reply) {
        stage = reply.stage();
        feed.apply(reply.page());
        rebuild();
    }

    @Override
    protected void init() {
        layout = BoardLayout.of(width, height);
        BoardLayout.Rect status = layout.statusButton();
        statusButton = addRenderableWidget(Button.builder(
                        statusLabel(), button -> applyFilter(feed.filter().nextGroup()))
                .bounds(status.x(), status.y(), status.width(), status.height())
                .build());
        BoardLayout.Rect spirit = layout.spiritButton();
        spiritButton = addRenderableWidget(Button.builder(
                        spiritLabel(), button -> applyFilter(feed.filter().nextSpiritNames(feed.spirits())))
                .bounds(spirit.x(), spirit.y(), spirit.width(), spirit.height())
                .build());
        ScreenTabButtons.add(this::addRenderableWidget, width, ScreenTabs.Tab.BOARD);
        if (!feed.seen()) {
            requestAll();
        }
        clampScroll();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        BoardLayout.Rect stageRect = layout.stage();
        graphics.drawCenteredString(
                font,
                Component.translatable(stage == null ? KEY + "stage.none" : stage.nameKey()),
                stageRect.x() + stageRect.width() / 2,
                stageRect.y(),
                TEXT_COLOR);
        BoardLayout.Rect window = layout.window();
        BoardLayout.Rect inner = layout.inner();
        BoardPanel.blitPanel(graphics, window);
        BoardPanel.drawBackground(graphics, inner);
        BoardTileView.Hover hovered = null;
        if (!feed.seen() || totalAll() == 0) {
            Component message =
                    Component.translatable(KEY + (offline ? "offline" : !feed.seen() ? "loading" : "empty"));
            graphics.drawCenteredString(
                    font, message, inner.x() + inner.width() / 2, inner.y() + inner.height() / 2 - 4, MUTED_COLOR);
        } else {
            hovered = BoardColumnView.render(
                    graphics,
                    font,
                    new BoardColumnView.Context(layout, feed, columns, waitReasons, tilesById),
                    scroll,
                    mouseX,
                    mouseY);
        }
        graphics.drawString(font, title, inner.x(), window.y() + 6, TITLE_COLOR, false);
        BoardPanel.drawVersion(graphics, font, inner, window, "v" + BuildFingerprint.version());
        if (hovered != null) {
            List<Component> lines = hovered.material() == null
                    ? BoardTileView.tooltip(hovered.tile(), waitReasons, tilesById)
                    : BoardTileView.materialTooltip(hovered.material());
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int column = layout.columnAt(mouseX, mouseY);
        if (column < 0) {
            return false;
        }
        scroll[column] = Mth.clamp(scroll[column] - scrollY * SCROLL_STEP, 0, maxScroll(column));
        if (atBottom(GROUPS.get(column))) {
            ask(GROUPS.get(column), true);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!feed.seen() || offline) {
            return;
        }
        if (++refreshTicks >= REFRESH_TICKS) {
            refreshTicks = 0;
            for (BoardTileGroup group : GROUPS) {
                ask(group, false);
            }
        }
        for (BoardTileGroup group : GROUPS) {
            if (atBottom(group)) {
                ask(group, true);
            }
        }
    }

    private void applyFilter(BoardFilter next) {
        for (int index = 0; index < scroll.length; index++) {
            scroll[index] = 0;
        }
        feed.filter(next);
        rebuild();
        requestAll();
    }

    private void requestAll() {
        for (BoardTileGroup group : GROUPS) {
            ask(group, false);
        }
    }

    private void ask(BoardTileGroup group, boolean grow) {
        if (feed.pending(group)) {
            return;
        }
        BoardPageQuery query = feed.query(group, grow);
        if (query == null) {
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(BoardViewChannel.REQUEST)) {
            offline = true;
            return;
        }
        offline = false;
        feed.markPending(group);
        PacketDistributor.sendToServer(new BoardViewChannel.Request(query));
    }

    private boolean atBottom(BoardTileGroup group) {
        int column = GROUPS.indexOf(group);
        if (feed.complete(group)) {
            return false;
        }
        return scroll[column] >= maxScroll(column) - 2;
    }

    private void rebuild() {
        columns = feed.columns();
        Map<String, BoardTile> byId = new HashMap<>();
        for (BoardColumns.BoardColumn column : columns) {
            for (BoardTile tile : column.tiles()) {
                byId.put(tile.id(), tile);
            }
        }
        tilesById = Map.copyOf(byId);
        Map<String, List<BoardWaitReasons.Wait>> reasons = new HashMap<>();
        for (BoardTile tile : byId.values()) {
            List<BoardWaitReasons.Wait> waits = BoardWaitReasons.of(tile, tilesById);
            if (!waits.isEmpty()) {
                reasons.put(tile.id(), waits);
            }
        }
        waitReasons = Map.copyOf(reasons);
        clampScroll();
        if (statusButton != null) {
            statusButton.setMessage(statusLabel());
        }
        if (spiritButton != null) {
            spiritButton.setMessage(spiritLabel());
        }
    }

    private void clampScroll() {
        for (int index = 0; index < scroll.length; index++) {
            scroll[index] = Mth.clamp(scroll[index], 0, maxScroll(index));
        }
    }

    private int totalAll() {
        int total = 0;
        for (BoardTileGroup group : GROUPS) {
            total += feed.total(group);
        }
        return total;
    }

    private int maxScroll(int column) {
        if (column < 0 || column >= columns.size()) {
            return 0;
        }
        int count = columns.get(column).tiles().size();
        int content = count * (BoardLayout.TILE_HEIGHT + BoardLayout.TILE_GAP) - BoardLayout.TILE_GAP;
        return Math.max(0, content - layout.list().height());
    }

    private Component statusLabel() {
        BoardTileGroup group = feed.filter().group();
        Component value = group == null
                ? Component.translatable(KEY + "filter.all")
                : Component.translatable(KEY + "group." + group.name().toLowerCase(Locale.ROOT));
        return Component.translatable(KEY + "filter.status", value);
    }

    private Component spiritLabel() {
        Component value = switch (feed.filter().spirit()) {
            case ANY -> Component.translatable(KEY + "filter.all");
            case NOBODY -> Component.literal("…");
            case NAMED -> Component.literal(String.valueOf(feed.filter().spiritName()));
        };
        return Component.translatable(KEY + "filter.spirit", value);
    }
}
