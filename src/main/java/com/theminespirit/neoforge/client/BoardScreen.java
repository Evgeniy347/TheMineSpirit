package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.BoardColumns;
import com.theminespirit.core.ui.BoardFilter;
import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardTileGroup;
import com.theminespirit.core.ui.BoardWaitReasons;
import com.theminespirit.neoforge.net.BoardViewChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Экран поселения, вкладка «Доска задач»: окно в рамке экрана достижений растягивается по размеру окна,
 * четыре колонки групп (завершённые, активные, доступные, недоступные) со своими списками и прокруткой
 * колесом под курсором, сверху кнопки фильтров по статусу и исполнителю. Данные — снимок доски,
 * полученный с сервера при открытии; живого обновления нет.
 */
public final class BoardScreen extends Screen {
    private static final String KEY = ModIdentity.MOD_ID + ".board.";
    private static final ResourceLocation WINDOW =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png");
    private static final int BACKGROUND_TILE = 16;
    private static final int MARGIN = 8;
    private static final int WINDOW_CONTENT_WIDTH = 252;
    private static final int WINDOW_CONTENT_HEIGHT = 140;
    private static final int WINDOW_TEXTURE = 256;
    private static final int BORDER_LEFT = 9;
    private static final int BORDER_TOP = 18;
    private static final int BORDER_RIGHT = 9;
    private static final int BORDER_BOTTOM = 9;
    private static final int GAP = 2;
    private static final int COLUMN_GAP = 4;
    private static final int TILE_HEIGHT = 32;
    private static final int TEXT_PAD = 4;
    private static final int STRIPE = 2;
    private static final int SCROLL_STEP = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTON_MAX_WIDTH = 150;
    private static final int COLUMN_HEADER = 12;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFA0A0A0;
    private static final int TILE_COLOR = 0xC0101010;
    private static final int TILE_HOVER_COLOR = 0xE0303030;
    private static final int UNRESOLVED_COLOR = 0xFFFFAAAA;
    private static final Map<BoardTileGroup, Integer> GROUP_COLORS = Map.of(
            BoardTileGroup.DONE, 0xFF55AA55,
            BoardTileGroup.ACTIVE, 0xFFFFAA00,
            BoardTileGroup.AVAILABLE, 0xFF55AAFF,
            BoardTileGroup.UNAVAILABLE, 0xFFAA5555);

    private @Nullable List<BoardTile> tiles;
    private List<BoardTile> shown = List.of();
    private List<BoardColumns.BoardColumn> columns = List.of();
    private Map<String, BoardTile> tilesById = Map.of();
    private Map<String, List<BoardWaitReasons.Wait>> waitReasons = Map.of();
    private final double[] scroll = new double[BoardTileGroup.values().length];
    private BoardFilter filter = BoardFilter.ALL;
    private @Nullable Button statusButton;
    private @Nullable Button spiritButton;
    private boolean offline;

    public BoardScreen() {
        super(Component.translatable(KEY + "title"));
    }

    public void show(List<BoardTile> snapshot) {
        tiles = List.copyOf(snapshot);
        refilter();
    }

    @Override
    protected void init() {
        int left = innerLeft() + 2;
        int top = MARGIN;
        int width = Math.min(BUTTON_MAX_WIDTH, Math.max(60, (innerWidth() - BUTTON_GAP) / 2));
        statusButton = addRenderableWidget(Button.builder(statusLabel(), button -> {
                    filter = filter.nextGroup();
                    refilter();
                })
                .bounds(left, top, width, BUTTON_HEIGHT)
                .build());
        spiritButton = addRenderableWidget(Button.builder(spiritLabel(), button -> {
                    filter = filter.nextSpirit(tiles == null ? List.of() : tiles);
                    refilter();
                })
                .bounds(left + width + BUTTON_GAP, top, width, BUTTON_HEIGHT)
                .build());
        if (tiles == null) {
            requestSnapshot();
        }
        for (int i = 0; i < scroll.length; i++) {
            scroll[i] = Mth.clamp(scroll[i], 0, maxScroll(i));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int innerLeft = innerLeft();
        int innerTop = innerTop();
        int innerWidth = innerWidth();
        int innerHeight = innerHeight();
        for (int tx = 0; tx < innerWidth; tx += BACKGROUND_TILE) {
            for (int ty = 0; ty < innerHeight; ty += BACKGROUND_TILE) {
                int w = Math.min(BACKGROUND_TILE, innerWidth - tx);
                int h = Math.min(BACKGROUND_TILE, innerHeight - ty);
                graphics.blit(BACKGROUND, innerLeft + tx, innerTop + ty, 0, 0, w, h, BACKGROUND_TILE, BACKGROUND_TILE);
            }
        }
        BoardTile hovered = null;
        if (tiles == null || shown.isEmpty()) {
            Component message = Component.translatable(
                    KEY + (offline ? "offline" : tiles == null ? "loading" : tiles.isEmpty() ? "empty" : "filtered"));
            graphics.drawCenteredString(
                    font, message, innerLeft + innerWidth / 2, innerTop + innerHeight / 2 - 4, MUTED_COLOR);
        } else {
            hovered = renderColumns(graphics, mouseX, mouseY);
        }
        blitPanel(graphics, left(), top(), windowWidth(), windowHeight());
        graphics.drawString(font, title, innerLeft, top() + 6, TITLE_COLOR, false);
        if (hovered != null) {
            graphics.renderComponentTooltip(font, tooltip(hovered), mouseX, mouseY);
        }
    }

    private @Nullable BoardTile renderColumns(GuiGraphics graphics, int mouseX, int mouseY) {
        BoardTile hovered = null;
        int listTop = listTop();
        int listBottom = innerBottom() - 2;
        for (int i = 0; i < columns.size(); i++) {
            BoardColumns.BoardColumn column = columns.get(i);
            int x = columnLeft(i);
            int width = columnWidth();
            Component header = Component.translatable(
                            KEY + "group." + column.group().name().toLowerCase(Locale.ROOT))
                    .append(" " + column.tiles().size());
            graphics.drawString(font, header, x, columnsTop(), GROUP_COLORS.get(column.group()), false);
            graphics.enableScissor(x, listTop, x + width, listBottom);
            for (int j = 0; j < column.tiles().size(); j++) {
                int y = listTop + j * (TILE_HEIGHT + GAP) - (int) scroll[i];
                if (y + TILE_HEIGHT < listTop || y > listBottom) {
                    continue;
                }
                boolean over = mouseX >= x
                        && mouseX < x + width
                        && mouseY >= Math.max(y, listTop)
                        && mouseY < Math.min(y + TILE_HEIGHT, listBottom);
                BoardTile tile = column.tiles().get(j);
                renderTile(graphics, tile, x, y, width, over);
                if (over) {
                    hovered = tile;
                }
            }
            graphics.disableScissor();
        }
        return hovered;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int column = columnAt(mouseX, mouseY);
        if (column < 0) {
            return false;
        }
        scroll[column] = Mth.clamp(scroll[column] - scrollY * SCROLL_STEP, 0, maxScroll(column));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void requestSnapshot() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(BoardViewChannel.REQUEST)) {
            offline = true;
            return;
        }
        PacketDistributor.sendToServer(new BoardViewChannel.Request());
    }

    private void refilter() {
        shown = tiles == null ? List.of() : filter.apply(tiles);
        columns = BoardColumns.split(shown);
        Map<String, BoardTile> byId = new HashMap<>();
        if (tiles != null) {
            for (BoardTile tile : tiles) {
                byId.put(tile.id(), tile);
            }
        }
        tilesById = Map.copyOf(byId);
        Map<String, List<BoardWaitReasons.Wait>> reasons = new HashMap<>();
        for (BoardTile tile : shown) {
            List<BoardWaitReasons.Wait> waits = BoardWaitReasons.of(tile, tilesById);
            if (!waits.isEmpty()) {
                reasons.put(tile.id(), waits);
            }
        }
        waitReasons = Map.copyOf(reasons);
        for (int i = 0; i < scroll.length; i++) {
            scroll[i] = Mth.clamp(scroll[i], 0, maxScroll(i));
        }
        if (statusButton != null) {
            statusButton.setMessage(statusLabel());
        }
        if (spiritButton != null) {
            spiritButton.setMessage(spiritLabel());
        }
    }

    private int maxScroll(int column) {
        if (column < 0 || column >= columns.size()) {
            return 0;
        }
        int rows = columns.get(column).tiles().size();
        return Math.max(0, rows * (TILE_HEIGHT + GAP) - listHeight());
    }

    private int columnAt(double mouseX, double mouseY) {
        if (columns.isEmpty()
                || mouseX < innerLeft()
                || mouseX >= innerRight()
                || mouseY < columnsTop()
                || mouseY >= innerBottom() - 2) {
            return -1;
        }
        int column = (int) ((mouseX - innerLeft()) / (columnWidth() + COLUMN_GAP));
        return Mth.clamp(column, 0, columns.size() - 1);
    }

    private void renderTile(GuiGraphics graphics, BoardTile tile, int x, int y, int width, boolean over) {
        graphics.fill(x, y, x + width, y + TILE_HEIGHT, over ? TILE_HOVER_COLOR : TILE_COLOR);
        graphics.fill(x, y, x + STRIPE, y + TILE_HEIGHT, GROUP_COLORS.get(tile.group()));
        int textWidth = width - 2 * TEXT_PAD;
        String text = font.plainSubstrByWidth(taskText(tile).getString(), textWidth);
        graphics.drawString(font, text, x + TEXT_PAD, y + 3, TEXT_COLOR);
        String details = font.plainSubstrByWidth(detailsText(tile).getString(), textWidth);
        graphics.drawString(font, details, x + TEXT_PAD, y + 3 + font.lineHeight + 1, MUTED_COLOR);
        List<BoardWaitReasons.Wait> waits = waitReasons.get(tile.id());
        if (waits != null && !waits.isEmpty()) {
            String reason = font.plainSubstrByWidth(waitText(waits.get(0)).getString(), textWidth);
            graphics.drawString(font, reason, x + TEXT_PAD, y + 3 + 2 * (font.lineHeight + 1), UNRESOLVED_COLOR);
        }
    }

    private static MutableComponent taskText(BoardTile tile) {
        String name = tile.boardCase().toLowerCase(Locale.ROOT);
        MutableComponent text = Component.translatableWithFallback(KEY + "case." + name, tile.boardCase());
        if (tile.level() > 1) {
            text.append(" ").append(Component.translatable(KEY + "level", tile.level()));
        }
        return text;
    }

    private static Component detailsText(BoardTile tile) {
        Component spirit = tile.spirit() == null ? Component.literal("…") : Component.literal(tile.spirit());
        return Component.translatable(KEY + "details", tile.priority(), spirit);
    }

    private List<Component> tooltip(BoardTile tile) {
        List<Component> lines = new ArrayList<>();
        lines.add(taskText(tile));
        lines.add(Component.translatable(KEY + "group." + tile.group().name().toLowerCase(Locale.ROOT)));
        lines.add(Component.translatableWithFallback(
                KEY + "status." + tile.status().toLowerCase(Locale.ROOT), tile.status()));
        lines.add(detailsText(tile));
        for (BoardWaitReasons.Wait wait : waitReasons.getOrDefault(tile.id(), List.of())) {
            lines.add(waitText(wait));
        }
        return lines;
    }

    private Component waitText(BoardWaitReasons.Wait wait) {
        return switch (wait.kind()) {
            case FREE_SPIRIT -> Component.translatable(KEY + "wait.free_spirit");
            case SPIRIT -> Component.translatable(KEY + "wait.spirit", Component.literal(wait.subject()));
            case RESOURCE ->
                Component.translatable(
                        KEY + "wait.resource",
                        itemName(wait.subject()),
                        Component.literal(String.valueOf(wait.count())));
            case TOOL ->
                Component.translatable(
                        KEY + "wait.tool", itemName(wait.subject()), Component.literal(String.valueOf(wait.count())));
            case DEPENDENCY -> Component.translatable(KEY + "wait.dependency", dependencyText(wait.subject()));
            case BLOCKED -> Component.translatable(KEY + "wait.blocked");
        };
    }

    private Component dependencyText(String id) {
        BoardTile dependency = tilesById.get(id);
        return dependency == null ? Component.literal(id) : taskText(dependency);
    }

    private static Component itemName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return Component.literal(id);
        }
        return BuiltInRegistries.ITEM.get(key).getDescription();
    }

    private Component statusLabel() {
        BoardTileGroup group = filter.group();
        Component value = group == null
                ? Component.translatable(KEY + "filter.all")
                : Component.translatable(KEY + "group." + group.name().toLowerCase(Locale.ROOT));
        return Component.translatable(KEY + "filter.status", value);
    }

    private Component spiritLabel() {
        Component value = switch (filter.spirit()) {
            case ANY -> Component.translatable(KEY + "filter.all");
            case NOBODY -> Component.literal("…");
            case NAMED -> Component.literal(String.valueOf(filter.spiritName()));
        };
        return Component.translatable(KEY + "filter.spirit", value);
    }

    private static void blitPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        int innerWidth = width - BORDER_LEFT - BORDER_RIGHT;
        int innerHeight = height - BORDER_TOP - BORDER_BOTTOM;
        blit(graphics, x, y, 0, 0, BORDER_LEFT, BORDER_TOP);
        blit(graphics, x + width - BORDER_RIGHT, y, WINDOW_CONTENT_WIDTH - BORDER_RIGHT, 0, BORDER_RIGHT, BORDER_TOP);
        blit(
                graphics,
                x,
                y + height - BORDER_BOTTOM,
                0,
                WINDOW_CONTENT_HEIGHT - BORDER_BOTTOM,
                BORDER_LEFT,
                BORDER_BOTTOM);
        blit(
                graphics,
                x + width - BORDER_RIGHT,
                y + height - BORDER_BOTTOM,
                WINDOW_CONTENT_WIDTH - BORDER_RIGHT,
                WINDOW_CONTENT_HEIGHT - BORDER_BOTTOM,
                BORDER_RIGHT,
                BORDER_BOTTOM);
        if (innerWidth > 0) {
            blit(graphics, x + BORDER_LEFT, y, BORDER_LEFT, 0, innerWidth, BORDER_TOP);
            blit(
                    graphics,
                    x + BORDER_LEFT,
                    y + height - BORDER_BOTTOM,
                    BORDER_LEFT,
                    WINDOW_CONTENT_HEIGHT - BORDER_BOTTOM,
                    innerWidth,
                    BORDER_BOTTOM);
        }
        if (innerHeight > 0) {
            blit(graphics, x, y + BORDER_TOP, 0, BORDER_TOP, BORDER_LEFT, innerHeight);
            blit(
                    graphics,
                    x + width - BORDER_RIGHT,
                    y + BORDER_TOP,
                    WINDOW_CONTENT_WIDTH - BORDER_RIGHT,
                    BORDER_TOP,
                    BORDER_RIGHT,
                    innerHeight);
        }
        if (innerWidth > 0 && innerHeight > 0) {
            blit(graphics, x + BORDER_LEFT, y + BORDER_TOP, BORDER_LEFT, BORDER_TOP, innerWidth, innerHeight);
        }
    }

    private static void blit(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        graphics.blit(WINDOW, x, y, u, v, width, height, WINDOW_TEXTURE, WINDOW_TEXTURE);
    }

    private int left() {
        return MARGIN;
    }

    private int top() {
        return MARGIN + BUTTON_HEIGHT + BUTTON_GAP;
    }

    private int windowWidth() {
        return width - 2 * MARGIN;
    }

    private int windowHeight() {
        return height - top() - MARGIN;
    }

    private int innerLeft() {
        return left() + BORDER_LEFT;
    }

    private int innerTop() {
        return top() + BORDER_TOP;
    }

    private int innerRight() {
        return left() + windowWidth() - BORDER_RIGHT;
    }

    private int innerBottom() {
        return top() + windowHeight() - BORDER_BOTTOM;
    }

    private int innerWidth() {
        return innerRight() - innerLeft();
    }

    private int innerHeight() {
        return innerBottom() - innerTop();
    }

    private int columnWidth() {
        return Math.max(24, (innerWidth() - 3 * COLUMN_GAP) / 4);
    }

    private int columnLeft(int column) {
        return innerLeft() + column * (columnWidth() + COLUMN_GAP);
    }

    private int columnsTop() {
        return innerTop() + 2;
    }

    private int listTop() {
        return columnsTop() + COLUMN_HEADER + GAP;
    }

    private int listHeight() {
        return Math.max(1, innerBottom() - 2 - listTop());
    }
}
