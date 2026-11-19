package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.BoardMaterials;
import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardWaitReasons;
import com.theminespirit.core.ui.api.BoardTileGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class BoardTileView {
    static final int TEXT_PAD = 4;
    static final int STRIPE = 2;
    private static final String KEY = ModIdentity.MOD_ID + ".board.";
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFA0A0A0;
    private static final int TILE_COLOR = 0xC0101010;
    private static final int TILE_HOVER_COLOR = 0xE0303030;
    private static final int UNRESOLVED_COLOR = 0xFFFFAAAA;
    private static final int FAILED_COLOR = 0xFFFF5555;
    private static final Map<BoardTileGroup, Integer> GROUP_COLORS = Map.of(
            BoardTileGroup.DONE, 0xFF55AA55,
            BoardTileGroup.ACTIVE, 0xFFFFAA00,
            BoardTileGroup.WAITING, 0xFFFF5555,
            BoardTileGroup.AVAILABLE, 0xFF55AAFF);

    private BoardTileView() {}

    record Hover(BoardTile tile, BoardMaterials.@Nullable Material material) {}

    record Cursor(int x, int y) {}

    record Context(Map<String, List<BoardWaitReasons.Wait>> waitReasons, Map<String, BoardTile> byId) {}

    static int groupColor(BoardTileGroup group) {
        return GROUP_COLORS.get(group);
    }

    static @Nullable Hover render(
            GuiGraphics graphics,
            Font font,
            BoardTile tile,
            BoardLayout.Rect rect,
            boolean over,
            Cursor cursor,
            Context context) {
        int x = rect.x();
        int y = rect.y();
        int width = rect.width();
        graphics.fill(x, y, x + width, y + BoardLayout.TILE_HEIGHT, over ? TILE_HOVER_COLOR : TILE_COLOR);
        graphics.fill(
                x,
                y,
                x + STRIPE,
                y + BoardLayout.TILE_HEIGHT,
                tile.unfinished() ? FAILED_COLOR : GROUP_COLORS.get(tile.group()));
        List<BoardMaterials.Material> allIcons = new ArrayList<>();
        List<BoardMaterials.Material> texts = new ArrayList<>();
        for (BoardMaterials.Material material : BoardMaterials.of(tile)) {
            if (stack(material.item()) == null) {
                texts.add(material);
            } else {
                allIcons.add(material);
            }
        }
        BoardLayout.IconGrid grid = BoardLayout.iconGrid(width, allIcons.size());
        int shown = Math.min(allIcons.size(), grid.capacity());
        int hidden = allIcons.size() - shown;
        if (hidden > 0 && grid.capacity() > 1) {
            shown = grid.capacity() - 1;
            hidden = allIcons.size() - shown;
        }
        List<BoardMaterials.Material> icons = allIcons.subList(0, shown);
        int textWidth =
                Math.max(0, width - 2 * TEXT_PAD - (grid.width() > 0 ? grid.width() + BoardLayout.ICON_GAP : 0));
        int lineStep = BoardLayout.TILE_LINE_STEP;
        int lineY = y + BoardLayout.TILE_TEXT_TOP;
        graphics.drawString(
                font,
                font.plainSubstrByWidth(BoardTileTexts.taskText(tile).getString(), textWidth),
                x + TEXT_PAD,
                lineY,
                TEXT_COLOR);
        lineY += lineStep;
        graphics.drawString(
                font,
                font.plainSubstrByWidth(BoardTileTexts.detailsText(tile).getString(), textWidth),
                x + TEXT_PAD,
                lineY,
                MUTED_COLOR);
        lineY += lineStep;
        Component time = BoardTileTexts.timeText(tile);
        if (time != null) {
            lineY = drawLine(graphics, font, time, x, lineY, textWidth, MUTED_COLOR);
        }
        Component spent = BoardTileTexts.spentText(tile);
        if (spent != null) {
            lineY = drawLine(graphics, font, spent, x, lineY, textWidth, MUTED_COLOR);
        }
        String third = null;
        int thirdColor = UNRESOLVED_COLOR;
        List<BoardWaitReasons.Wait> waits = context.waitReasons().get(tile.id());
        if (tile.unfinished()) {
            third = BoardTileTexts.unfinished().getString();
            thirdColor = FAILED_COLOR;
        } else if (waits != null && !waits.isEmpty()) {
            third = BoardTileTexts.waitText(waits.get(0), context.byId()).getString();
        }
        if (third != null) {
            lineY = drawLine(graphics, font, Component.literal(third), x, lineY, textWidth, thirdColor);
        }
        for (BoardMaterials.Material material : texts) {
            if (lineY + BoardLayout.TILE_LINE_STEP > y + BoardLayout.TILE_HEIGHT) {
                break;
            }
            lineY = drawLine(
                    graphics, font, BoardTileTexts.materialText(material), x, lineY, textWidth, UNRESOLVED_COLOR);
        }
        return renderIcons(graphics, font, icons, grid, hidden, rect, cursor, tile);
    }

    private static @Nullable Hover renderIcons(
            GuiGraphics graphics,
            Font font,
            List<BoardMaterials.Material> icons,
            BoardLayout.IconGrid grid,
            int hidden,
            BoardLayout.Rect rect,
            Cursor cursor,
            BoardTile tile) {
        if (grid.columns() == 0) {
            return null;
        }
        int startX = rect.x() + rect.width() - TEXT_PAD - grid.width();
        int startY = rect.y() + BoardLayout.ICON_TOP;
        BoardMaterials.Material hovered = null;
        for (int i = 0; i < icons.size(); i++) {
            BoardMaterials.Material material = icons.get(i);
            ItemStack stack = stack(material.item());
            if (stack == null) {
                continue;
            }
            int iconX = iconX(startX, grid, i);
            int iconY = iconY(startY, grid, i);
            graphics.renderItem(stack, iconX, iconY);
            drawCount(graphics, font, material, iconX, iconY);
            if (isInside(cursor, iconX, iconY)) {
                hovered = material;
            }
        }
        if (hidden > 0) {
            int index = icons.size();
            graphics.drawString(
                    font, "+" + hidden, iconX(startX, grid, index), iconY(startY, grid, index) + 4, FAILED_COLOR, true);
        }
        return hovered == null ? null : new Hover(tile, hovered);
    }

    private static int iconX(int startX, BoardLayout.IconGrid grid, int index) {
        return startX + (index % grid.columns()) * (BoardLayout.ICON_SIZE + BoardLayout.ICON_GAP);
    }

    private static int iconY(int startY, BoardLayout.IconGrid grid, int index) {
        return startY + (index / grid.columns()) * (BoardLayout.ICON_SIZE + BoardLayout.ICON_GAP);
    }

    private static boolean isInside(Cursor cursor, int iconX, int iconY) {
        return cursor.x() >= iconX
                && cursor.x() < iconX + BoardLayout.ICON_SIZE
                && cursor.y() >= iconY
                && cursor.y() < iconY + BoardLayout.ICON_SIZE;
    }

    private static void drawCount(
            GuiGraphics graphics, Font font, BoardMaterials.Material material, int iconX, int iconY) {
        String count = BoardTileTexts.countText(material.count());
        int starWidth = BoardMaterials.shortage(material) ? font.width("*") : 0;
        int numberX = iconX + BoardLayout.ICON_SIZE - font.width(count) - starWidth;
        int numberY = iconY + BoardLayout.ICON_SIZE - font.lineHeight + 1;
        graphics.drawString(font, count, numberX, numberY, TEXT_COLOR, true);
        if (starWidth > 0) {
            graphics.drawString(font, "*", numberX + font.width(count), numberY, FAILED_COLOR, true);
        }
    }

    static List<Component> tooltip(
            BoardTile tile, Map<String, List<BoardWaitReasons.Wait>> waitReasons, Map<String, BoardTile> byId) {
        List<Component> lines = new ArrayList<>();
        lines.add(BoardTileTexts.taskText(tile));
        lines.add(Component.translatable(KEY + "group." + tile.group().name().toLowerCase(Locale.ROOT)));
        if (tile.unfinished()) {
            lines.add(BoardTileTexts.unfinished());
        }
        lines.add(Component.translatableWithFallback(
                KEY + "status." + tile.status().toLowerCase(Locale.ROOT), tile.status()));
        lines.add(BoardTileTexts.detailsText(tile));
        Component time = BoardTileTexts.timeText(tile);
        if (time != null) {
            lines.add(time);
        }
        Component spent = BoardTileTexts.spentText(tile);
        if (spent != null) {
            lines.add(spent);
        }
        for (BoardMaterials.Material material : BoardMaterials.of(tile)) {
            lines.add(BoardTileTexts.materialText(material));
        }
        for (BoardWaitReasons.Wait wait : waitReasons.getOrDefault(tile.id(), List.of())) {
            lines.add(BoardTileTexts.waitText(wait, byId));
        }
        return lines;
    }

    static List<Component> materialTooltip(BoardMaterials.Material material) {
        List<Component> lines = new ArrayList<>();
        lines.add(BoardTileTexts.itemName(material.item()));
        lines.add(BoardTileTexts.materialText(material));
        return lines;
    }

    private static int drawLine(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), maxWidth), x + TEXT_PAD, y, color);
        return y + BoardLayout.TILE_LINE_STEP;
    }

    private static @Nullable ItemStack stack(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        return new ItemStack(item);
    }
}
