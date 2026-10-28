package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.BoardColumns;
import com.theminespirit.core.ui.BoardFilter;
import com.theminespirit.core.ui.BoardLayout;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

public final class BoardScreen extends Screen {
    private static final String KEY = ModIdentity.MOD_ID + ".board.";
    private static final ResourceLocation WINDOW =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png");
    private static final int BACKGROUND_TILE = 16;
    private static final int WINDOW_CONTENT_WIDTH = 252;
    private static final int WINDOW_CONTENT_HEIGHT = 140;
    private static final int WINDOW_TEXTURE = 256;
    private static final int SCROLL_STEP = 12;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFA0A0A0;
    private static final int HEADER_BG_COLOR = 0xE0101010;

    private BoardLayout layout = BoardLayout.of(0, 0);
    private @Nullable List<BoardTile> tiles;
    private @Nullable BoardStage stage;
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

    public void showStage(BoardStage settlementStage) {
        stage = settlementStage;
    }

    public void show(List<BoardTile> snapshot) {
        tiles = List.copyOf(snapshot);
        refilter();
    }

    @Override
    protected void init() {
        layout = BoardLayout.of(width, height);
        BoardLayout.Rect status = layout.statusButton();
        statusButton = addRenderableWidget(Button.builder(statusLabel(), button -> {
                    filter = filter.nextGroup();
                    refilter();
                })
                .bounds(status.x(), status.y(), status.width(), status.height())
                .build());
        BoardLayout.Rect spirit = layout.spiritButton();
        spiritButton = addRenderableWidget(Button.builder(spiritLabel(), button -> {
                    filter = filter.nextSpirit(tiles == null ? List.of() : tiles);
                    refilter();
                })
                .bounds(spirit.x(), spirit.y(), spirit.width(), spirit.height())
                .build());
        ScreenTabButtons.add(this::addRenderableWidget, width, ScreenTabs.Tab.BOARD);
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
        BoardLayout.Rect stageRect = layout.stage();
        graphics.drawCenteredString(
                font,
                Component.translatable(stage == null ? KEY + "stage.none" : stage.nameKey()),
                stageRect.x() + stageRect.width() / 2,
                stageRect.y(),
                TEXT_COLOR);
        BoardLayout.Rect window = layout.window();
        BoardLayout.Rect inner = layout.inner();
        blitPanel(graphics, window);
        drawBackground(graphics, inner);
        BoardTileView.Hover hovered = null;
        if (tiles == null || shown.isEmpty()) {
            Component message = Component.translatable(
                    KEY + (offline ? "offline" : tiles == null ? "loading" : tiles.isEmpty() ? "empty" : "filtered"));
            graphics.drawCenteredString(
                    font, message, inner.x() + inner.width() / 2, inner.y() + inner.height() / 2 - 4, MUTED_COLOR);
        } else {
            hovered = renderColumns(graphics, mouseX, mouseY);
        }
        graphics.drawString(font, title, inner.x(), window.y() + 6, TITLE_COLOR, false);
        drawVersion(graphics, inner, window, "v" + BuildFingerprint.version());
        if (hovered != null) {
            List<Component> lines = hovered.material() == null
                    ? BoardTileView.tooltip(hovered.tile(), waitReasons, tilesById)
                    : BoardTileView.materialTooltip(hovered.material());
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void drawVersion(GuiGraphics graphics, BoardLayout.Rect inner, BoardLayout.Rect window, String text) {
        float scale = 0.5F;
        float x = inner.x() + inner.width() - font.width(text) * scale - 2;
        graphics.pose().pushPose();
        graphics.pose().translate(x, window.y() + 7, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, MUTED_COLOR, false);
        graphics.pose().popPose();
    }

    private static void drawBackground(GuiGraphics graphics, BoardLayout.Rect inner) {
        for (int tx = 0; tx < inner.width(); tx += BACKGROUND_TILE) {
            for (int ty = 0; ty < inner.height(); ty += BACKGROUND_TILE) {
                int w = Math.min(BACKGROUND_TILE, inner.width() - tx);
                int h = Math.min(BACKGROUND_TILE, inner.height() - ty);
                graphics.blit(BACKGROUND, inner.x() + tx, inner.y() + ty, 0, 0, w, h, BACKGROUND_TILE, BACKGROUND_TILE);
            }
        }
    }

    private BoardTileView.@Nullable Hover renderColumns(GuiGraphics graphics, int mouseX, int mouseY) {
        BoardTileView.Hover hovered = null;
        BoardLayout.Rect list = layout.list();
        List<BoardLayout.Rect> columnRects = layout.columns();
        List<BoardLayout.Rect> headers = layout.headers();
        for (int i = 0; i < columns.size(); i++) {
            BoardColumns.BoardColumn column = columns.get(i);
            BoardLayout.Rect header = headers.get(i);
            BoardLayout.Rect area = columnRects.get(i);
            Component headerText = Component.translatable(
                            KEY + "group." + column.group().name().toLowerCase(Locale.ROOT))
                    .append(" " + column.tiles().size());
            graphics.fill(header.x(), header.y(), header.right(), header.bottom(), HEADER_BG_COLOR);
            String headerLine =
                    font.plainSubstrByWidth(headerText.getString(), header.width() - 2 * BoardLayout.CONTENT_PAD);
            graphics.drawString(
                    font,
                    headerLine,
                    header.x() + BoardLayout.CONTENT_PAD,
                    header.y() + BoardLayout.CONTENT_PAD,
                    BoardTileView.groupColor(column.group()),
                    true);
            graphics.enableScissor(area.x(), list.y(), area.right(), list.bottom());
            for (int j = 0; j < column.tiles().size(); j++) {
                BoardLayout.Rect rect = layout.tile(i, j);
                int y = rect.y() - (int) scroll[i];
                if (y + rect.height() < list.y() || y > list.bottom()) {
                    continue;
                }
                boolean over = mouseX >= rect.x()
                        && mouseX < rect.right()
                        && mouseY >= Math.max(y, list.y())
                        && mouseY < Math.min(y + rect.height(), list.bottom());
                BoardTile tile = column.tiles().get(j);
                BoardLayout.Rect row = new BoardLayout.Rect(rect.x(), y, rect.width(), rect.height());
                BoardTileView.Hover hover = BoardTileView.render(
                        graphics,
                        font,
                        tile,
                        row,
                        over,
                        new BoardTileView.Cursor(mouseX, mouseY),
                        new BoardTileView.Context(waitReasons, tilesById));
                if (hover != null) {
                    hovered = hover;
                } else if (over) {
                    hovered = new BoardTileView.Hover(tile, null);
                }
            }
            graphics.disableScissor();
        }
        return hovered;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int column = layout.columnAt(mouseX, mouseY);
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
        int count = columns.get(column).tiles().size();
        int content = count * (BoardLayout.TILE_HEIGHT + BoardLayout.TILE_GAP) - BoardLayout.TILE_GAP;
        return Math.max(0, content - layout.list().height());
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

    private static void blitPanel(GuiGraphics graphics, BoardLayout.Rect window) {
        int x = window.x();
        int y = window.y();
        int width = window.width();
        int height = window.height();
        int left = BoardLayout.BORDER_LEFT;
        int top = BoardLayout.BORDER_TOP;
        int right = BoardLayout.BORDER_RIGHT;
        int bottom = BoardLayout.BORDER_BOTTOM;
        int innerWidth = width - left - right;
        int innerHeight = height - top - bottom;
        int sourceInnerWidth = WINDOW_CONTENT_WIDTH - left - right;
        int sourceInnerHeight = WINDOW_CONTENT_HEIGHT - top - bottom;
        if (width >= left + right && height >= top + bottom) {
            blit(graphics, x, y, 0, 0, left, top);
            blit(graphics, x + width - right, y, WINDOW_CONTENT_WIDTH - right, 0, right, top);
            blit(graphics, x, y + height - bottom, 0, WINDOW_CONTENT_HEIGHT - bottom, left, bottom);
            blit(
                    graphics,
                    x + width - right,
                    y + height - bottom,
                    WINDOW_CONTENT_WIDTH - right,
                    WINDOW_CONTENT_HEIGHT - bottom,
                    right,
                    bottom);
        }
        if (innerWidth > 0 && height >= top + bottom) {
            blitTiledX(graphics, x + left, y, left, 0, innerWidth, top);
            blitTiledX(
                    graphics, x + left, y + height - bottom, left, WINDOW_CONTENT_HEIGHT - bottom, innerWidth, bottom);
        }
        if (innerHeight > 0 && width >= left + right) {
            blitTiledY(graphics, x, y + top, 0, top, left, innerHeight);
            blitTiledY(graphics, x + width - right, y + top, WINDOW_CONTENT_WIDTH - right, top, right, innerHeight);
        }
        if (innerWidth > 0 && innerHeight > 0) {
            for (int dx = 0; dx < innerWidth; dx += sourceInnerWidth) {
                int partWidth = Math.min(sourceInnerWidth, innerWidth - dx);
                for (int dy = 0; dy < innerHeight; dy += sourceInnerHeight) {
                    int partHeight = Math.min(sourceInnerHeight, innerHeight - dy);
                    blit(graphics, x + left + dx, y + top + dy, left, top, partWidth, partHeight);
                }
            }
        }
    }

    private static void blitTiledX(GuiGraphics graphics, int destX, int destY, int u, int v, int width, int height) {
        int step = WINDOW_CONTENT_WIDTH - BoardLayout.BORDER_LEFT - BoardLayout.BORDER_RIGHT;
        for (int dx = 0; dx < width; dx += step) {
            blit(graphics, destX + dx, destY, u, v, Math.min(step, width - dx), height);
        }
    }

    private static void blitTiledY(GuiGraphics graphics, int destX, int destY, int u, int v, int width, int height) {
        int step = WINDOW_CONTENT_HEIGHT - BoardLayout.BORDER_TOP - BoardLayout.BORDER_BOTTOM;
        for (int dy = 0; dy < height; dy += step) {
            blit(graphics, destX, destY + dy, u, v, width, Math.min(step, height - dy));
        }
    }

    private static void blit(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        graphics.blit(WINDOW, x, y, u, v, width, height, WINDOW_TEXTURE, WINDOW_TEXTURE);
    }
}
