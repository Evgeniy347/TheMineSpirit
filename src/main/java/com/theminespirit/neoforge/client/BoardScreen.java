package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.BoardFilter;
import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardTileGroup;
import com.theminespirit.neoforge.net.BoardViewChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
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
 * Экран поселения, вкладка «Доска задач»: окно в рамке экрана достижений, плитки заданий в две колонки,
 * сверху кнопки фильтров по статусу и исполнителю, колесо мыши прокручивает список. Данные — снимок
 * доски, полученный с сервера при открытии; живого обновления нет.
 */
public final class BoardScreen extends Screen {
    private static final String KEY = ModIdentity.MOD_ID + ".board.";
    private static final ResourceLocation WINDOW =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png");
    private static final int BACKGROUND_TILE = 16;
    private static final int WINDOW_WIDTH = 252;
    private static final int WINDOW_HEIGHT = 140;
    private static final int INNER_LEFT = 9;
    private static final int INNER_TOP = 18;
    private static final int INNER_WIDTH = 234;
    private static final int INNER_HEIGHT = 113;
    private static final int GAP = 2;
    private static final int TILE_WIDTH = (INNER_WIDTH - 3 * GAP) / 2;
    private static final int TILE_HEIGHT = 24;
    private static final int TEXT_PAD = 4;
    private static final int STRIPE = 2;
    private static final int SCROLL_STEP = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFA0A0A0;
    private static final int TILE_COLOR = 0xC0101010;
    private static final int TILE_HOVER_COLOR = 0xE0303030;
    private static final Map<BoardTileGroup, Integer> GROUP_COLORS = Map.of(
            BoardTileGroup.DONE, 0xFF55AA55,
            BoardTileGroup.ACTIVE, 0xFFFFAA00,
            BoardTileGroup.AVAILABLE, 0xFF55AAFF,
            BoardTileGroup.UNAVAILABLE, 0xFFAA5555);

    private @Nullable List<BoardTile> tiles;
    private List<BoardTile> shown = List.of();
    private BoardFilter filter = BoardFilter.ALL;
    private @Nullable Button statusButton;
    private @Nullable Button spiritButton;
    private double scroll;
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
        int left = left();
        int top = top() - BUTTON_HEIGHT - BUTTON_GAP;
        int width = (WINDOW_WIDTH - BUTTON_GAP) / 2;
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
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        int innerLeft = left + INNER_LEFT;
        int innerTop = top + INNER_TOP;
        for (int tx = 0; tx < INNER_WIDTH; tx += BACKGROUND_TILE) {
            for (int ty = 0; ty < INNER_HEIGHT; ty += BACKGROUND_TILE) {
                int w = Math.min(BACKGROUND_TILE, INNER_WIDTH - tx);
                int h = Math.min(BACKGROUND_TILE, INNER_HEIGHT - ty);
                graphics.blit(BACKGROUND, innerLeft + tx, innerTop + ty, 0, 0, w, h, BACKGROUND_TILE, BACKGROUND_TILE);
            }
        }
        BoardTile hovered = null;
        graphics.enableScissor(innerLeft, innerTop, innerLeft + INNER_WIDTH, innerTop + INNER_HEIGHT);
        if (tiles == null || shown.isEmpty()) {
            Component message = Component.translatable(
                    KEY + (offline ? "offline" : tiles == null ? "loading" : tiles.isEmpty() ? "empty" : "filtered"));
            graphics.drawCenteredString(
                    font, message, innerLeft + INNER_WIDTH / 2, innerTop + INNER_HEIGHT / 2 - 4, MUTED_COLOR);
        } else {
            for (int i = 0; i < shown.size(); i++) {
                int x = innerLeft + GAP + (i % 2) * (TILE_WIDTH + GAP);
                int y = innerTop + GAP + (i / 2) * (TILE_HEIGHT + GAP) - (int) scroll;
                if (y + TILE_HEIGHT < innerTop || y > innerTop + INNER_HEIGHT) {
                    continue;
                }
                boolean over = mouseX >= x
                        && mouseX < x + TILE_WIDTH
                        && mouseY >= Math.max(y, innerTop)
                        && mouseY < Math.min(y + TILE_HEIGHT, innerTop + INNER_HEIGHT);
                BoardTile tile = shown.get(i);
                renderTile(graphics, tile, x, y, over);
                if (over) {
                    hovered = tile;
                }
            }
        }
        graphics.disableScissor();
        graphics.blit(WINDOW, left, top, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        graphics.drawString(font, title, left + 8, top + 6, TITLE_COLOR, false);
        if (hovered != null) {
            graphics.renderComponentTooltip(font, tooltip(hovered), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * SCROLL_STEP, 0, maxScroll());
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
        scroll = Mth.clamp(scroll, 0, maxScroll());
        if (statusButton != null) {
            statusButton.setMessage(statusLabel());
        }
        if (spiritButton != null) {
            spiritButton.setMessage(spiritLabel());
        }
    }

    private int maxScroll() {
        int rows = (shown.size() + 1) / 2;
        return Math.max(0, rows * (TILE_HEIGHT + GAP) + GAP - INNER_HEIGHT);
    }

    private void renderTile(GuiGraphics graphics, BoardTile tile, int x, int y, boolean over) {
        graphics.fill(x, y, x + TILE_WIDTH, y + TILE_HEIGHT, over ? TILE_HOVER_COLOR : TILE_COLOR);
        graphics.fill(x, y, x + STRIPE, y + TILE_HEIGHT, GROUP_COLORS.get(tile.group()));
        int textWidth = TILE_WIDTH - 2 * TEXT_PAD;
        String text = font.plainSubstrByWidth(taskText(tile).getString(), textWidth);
        graphics.drawString(font, text, x + TEXT_PAD, y + 3, TEXT_COLOR);
        String details = font.plainSubstrByWidth(detailsText(tile).getString(), textWidth);
        graphics.drawString(font, details, x + TEXT_PAD, y + 3 + font.lineHeight + 1, MUTED_COLOR);
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

    private static List<Component> tooltip(BoardTile tile) {
        List<Component> lines = new ArrayList<>();
        lines.add(taskText(tile));
        lines.add(Component.translatable(KEY + "group." + tile.group().name().toLowerCase(Locale.ROOT)));
        lines.add(Component.translatableWithFallback(
                KEY + "status." + tile.status().toLowerCase(Locale.ROOT), tile.status()));
        lines.add(detailsText(tile));
        if (!tile.missing().isEmpty()) {
            lines.add(Component.translatable(KEY + "missing"));
            for (Map.Entry<String, Integer> item : new TreeMap<>(tile.missing()).entrySet()) {
                lines.add(
                        Component.literal("  ").append(itemName(item.getKey())).append(" ×" + item.getValue()));
            }
        }
        return lines;
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

    private int left() {
        return (width - WINDOW_WIDTH) / 2;
    }

    private int top() {
        return (height - WINDOW_HEIGHT + BUTTON_HEIGHT + BUTTON_GAP) / 2;
    }
}
