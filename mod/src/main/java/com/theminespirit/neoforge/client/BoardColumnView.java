package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardWaitReasons;
import com.theminespirit.core.ui.api.BoardTileGroup;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

final class BoardColumnView {
    private static final String KEY = ModIdentity.MOD_ID + ".board.";
    private static final int HEADER_BG = 0xE012151A;
    private static final int HEADER_BORDER = 0x14FFFFFF;
    private static final int COUNT_BG = 0x2AFFFFFF;
    private static final int TRACK = 0xFF1B2026;
    private static final int THUMB = 0xFF3A434E;

    private BoardColumnView() {}

    record Context(
            BoardLayout layout,
            List<List<BoardTile>> slotTiles,
            Map<BoardTileGroup, Integer> totals,
            Map<String, List<BoardWaitReasons.Wait>> waitReasons,
            Map<String, BoardTile> tilesById) {}

    static BoardTileView.@Nullable Hover render(
            GuiGraphics graphics,
            Font font,
            Context context,
            Map<BoardTileGroup, Double> scroll,
            int mouseX,
            int mouseY,
            @Nullable String selectedId) {
        BoardLayout layout = context.layout();
        BoardTileView.Hover hovered = null;
        for (int i = 0; i < layout.slotCount(); i++) {
            BoardLayout.Slot slot = layout.slots().get(i);
            BoardLayout.Rect header = slot.header();
            BoardLayout.Rect list = slot.list();
            BoardTileGroup group = slot.group();
            int color = BoardTileView.groupColor(group);
            graphics.fill(header.x(), header.y(), header.right(), header.bottom(), HEADER_BG);
            graphics.fill(header.x(), header.bottom() - 1, header.right(), header.bottom(), HEADER_BORDER);
            graphics.fill(header.x() + 6, header.y() + 6, header.x() + 12, header.y() + 12, color);
            Component title =
                    Component.translatable(KEY + "group." + group.name().toLowerCase(Locale.ROOT));
            int count = context.totals().getOrDefault(group, 0);
            String badge = String.valueOf(count);
            int badgeWidth = font.width(badge) + 8;
            graphics.drawString(
                    font,
                    TextClip.text(font, title, header.width() - 20 - badgeWidth),
                    header.x() + 17,
                    header.y() + 5,
                    BoardTileView.groupColor(group) == 0 ? 0xFFFFFFFF : 0xFFE8ECF1,
                    false);
            graphics.fill(
                    header.right() - badgeWidth - 5, header.y() + 4, header.right() - 5, header.y() + 15, COUNT_BG);
            graphics.drawString(font, badge, header.right() - badgeWidth - 1, header.y() + 4, color, false);

            List<BoardTile> tiles = context.slotTiles().get(i);
            int scrolled = (int) (double) scroll.getOrDefault(group, 0.0);
            double content = tiles.size() * (BoardLayout.TILE_HEIGHT + BoardLayout.TILE_GAP) - BoardLayout.TILE_GAP;
            if (tiles.isEmpty()) {
                graphics.drawString(
                        font,
                        Component.translatable(KEY + "column.empty"),
                        list.x() + 6,
                        list.y() + 6,
                        0xFF6B7480,
                        false);
            }
            graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
            for (int j = 0; j < tiles.size(); j++) {
                BoardLayout.Rect base = layout.tile(i, j);
                int y = base.y() - scrolled;
                if (y + base.height() < list.y() || y > list.bottom()) {
                    continue;
                }
                boolean over = mouseX >= base.x()
                        && mouseX < base.right()
                        && mouseY >= Math.max(y, list.y())
                        && mouseY < Math.min(y + base.height(), list.bottom());
                BoardTile tile = tiles.get(j);
                BoardLayout.Rect row = new BoardLayout.Rect(base.x(), y, base.width(), base.height());
                hovered = BoardTileView.render(
                        graphics,
                        font,
                        tile,
                        row,
                        over,
                        tile.id().equals(selectedId),
                        new BoardTileView.Cursor(mouseX, mouseY),
                        new BoardTileView.Context(context.waitReasons(), context.tilesById()));
            }
            graphics.disableScissor();
            if (content > list.height()) {
                BoardLayout.Rect bar = slot.scrollbar();
                graphics.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), TRACK);
                int thumb = Math.max(18, (int) (bar.height() * list.height() / content));
                int travel = Math.max(1, bar.height() - thumb);
                int top = bar.y() + (int) (travel * (scrolled / Math.max(1.0, content - list.height())));
                graphics.fill(bar.x(), top, bar.right(), top + thumb, THUMB);
            }
        }
        return hovered;
    }
}
