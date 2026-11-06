package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.BoardColumns;
import com.theminespirit.core.ui.BoardFeed;
import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardWaitReasons;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

final class BoardColumnView {
    private static final String KEY = ModIdentity.MOD_ID + ".board.";
    private static final int HEADER_BG_COLOR = 0xE0101010;

    private BoardColumnView() {}

    record Context(
            BoardLayout layout,
            BoardFeed feed,
            List<BoardColumns.BoardColumn> columns,
            Map<String, List<BoardWaitReasons.Wait>> waitReasons,
            Map<String, BoardTile> tilesById) {}

    static BoardTileView.@Nullable Hover render(
            GuiGraphics graphics, Font font, Context context, double[] scroll, int mouseX, int mouseY) {
        BoardLayout layout = context.layout();
        BoardFeed feed = context.feed();
        List<BoardColumns.BoardColumn> columns = context.columns();
        Map<String, List<BoardWaitReasons.Wait>> waitReasons = context.waitReasons();
        Map<String, BoardTile> tilesById = context.tilesById();
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
                    .append(" " + feed.total(column.group()));
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
}
