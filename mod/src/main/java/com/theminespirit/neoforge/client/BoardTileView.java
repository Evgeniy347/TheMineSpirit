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
    static final int TEXT_PAD = 9;
    private static final int MATERIAL_Y = 24;
    private static final String KEY = ModIdentity.MOD_ID + ".board.";
    private static final int TEXT_COLOR = 0xFFE8ECF1;
    private static final int MUTED_COLOR = 0xFF98A0AB;
    private static final int DIM_COLOR = 0xFF6B7480;
    private static final int GOLD = 0xFFE0B050;
    private static final int TILE_COLOR = 0xF014171C;
    private static final int TILE_HOVER_COLOR = 0xF01E2228;
    private static final int TILE_SELECTED_COLOR = 0xF0262116;
    private static final int FAILED_COLOR = 0xFFFF5555;
    private static final int GOOD_COLOR = 0xFF6FA548;
    private static final int ORANGE_COLOR = 0xFFFFAA00;
    private static final int CHIP_BG = 0x2AFFFFFF;
    private static final int CHIP_BAD = 0x33FF5555;
    private static final Map<BoardTileGroup, Integer> GROUP_COLORS = Map.of(
            BoardTileGroup.DONE, 0xFF6FA548,
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
            boolean selected,
            Cursor cursor,
            Context context) {
        int x = rect.x();
        int y = rect.y();
        int width = rect.width();
        int height = BoardLayout.TILE_HEIGHT;
        graphics.fill(
                x, y, x + width, y + height, selected ? TILE_SELECTED_COLOR : over ? TILE_HOVER_COLOR : TILE_COLOR);
        if (selected) {
            graphics.fill(x, y, x + width, y + 1, GOLD);
            graphics.fill(x, y + height - 1, x + width, y + height, GOLD);
            graphics.fill(x, y, x + 1, y + height, GOLD);
            graphics.fill(x + width - 1, y, x + width, y + height, GOLD);
        }
        int stripe = tile.unfinished() ? FAILED_COLOR : GROUP_COLORS.get(tile.group());
        graphics.fill(x + 4, y + 5, x + 7, y + height - 5, stripe);

        String priority = "P" + tile.priority();
        int chipWidth = font.width(priority) + 6;
        int textWidth = Math.max(1, width - TEXT_PAD - 3 - chipWidth - 10);
        TextClip.Line name = TextClip.fit(font, BoardTileTexts.taskText(tile), textWidth);
        graphics.drawString(font, name.text(), x + TEXT_PAD + 3, y + 5, TEXT_COLOR, false);
        graphics.fill(x + width - 6 - chipWidth, y + 4, x + width - 6, y + 14, 0x2AE0B050);
        graphics.drawString(font, priority, x + width - 3 - chipWidth, y + 4, GOLD, false);

        String worker;
        int workerColor;
        if (tile.spirit() != null) {
            worker = tile.spirit();
            workerColor = GOOD_COLOR;
        } else {
            List<BoardWaitReasons.Wait> waits = context.waitReasons().getOrDefault(tile.id(), List.of());
            worker = waits.isEmpty()
                    ? Component.translatable(KEY + "tile.free").getString()
                    : Component.translatable(KEY + "tile.waiting").getString();
            workerColor = ORANGE_COLOR;
        }
        graphics.fill(x + TEXT_PAD + 1, y + 19, x + TEXT_PAD + 5, y + 23, workerColor);
        graphics.drawString(
                font,
                TextClip.text(font, Component.literal(worker), width - TEXT_PAD - 12),
                x + TEXT_PAD + 8,
                y + 16,
                MUTED_COLOR,
                false);

        int materialX = x + TEXT_PAD + 3;
        List<BoardMaterials.Material> materials = BoardMaterials.of(tile);
        int shown = Math.min(2, materials.size());
        for (int i = 0; i < shown; i++) {
            BoardMaterials.Material material = materials.get(i);
            ItemStack stack = stack(material.item());
            boolean bad = BoardMaterials.shortage(material) || material.kind() == BoardMaterials.Kind.MISSING;
            String label = label(material);
            int chipWidthNow = BoardLayout.materialChipWidth(font.width(label));
            if (materialX + chipWidthNow > x + width - 6) {
                break;
            }
            graphics.fill(
                    materialX, y + MATERIAL_Y, materialX + chipWidthNow, y + MATERIAL_Y + 16, bad ? CHIP_BAD : CHIP_BG);
            if (stack != null) {
                graphics.renderItem(stack, materialX + 1, y + MATERIAL_Y);
            }
            graphics.drawString(
                    font,
                    label,
                    materialX + BoardLayout.MATERIAL_ICON + 3,
                    y + MATERIAL_Y + 4,
                    bad ? 0xFFFF9C9C : MUTED_COLOR,
                    false);
            materialX += chipWidthNow + 4;
        }
        if (materials.size() > shown && materialX + 12 < x + width - 6) {
            graphics.drawString(
                    font, "+" + (materials.size() - shown), materialX, y + MATERIAL_Y + 4, DIM_COLOR, false);
        }

        String status = statusText(tile, context);
        int statusColor = tile.unfinished()
                ? FAILED_COLOR
                : !context.waitReasons().getOrDefault(tile.id(), List.of()).isEmpty()
                        ? ORANGE_COLOR
                        : GROUP_COLORS.get(tile.group());
        graphics.fill(x + TEXT_PAD + 1, y + 45, x + TEXT_PAD + 5, y + 49, statusColor);
        Component time = BoardTileTexts.timeText(tile);
        int timeWidth = time == null ? 0 : Math.min(84, font.width(time));
        TextClip.Line statusLine = TextClip.fit(
                font, Component.literal(status), width - TEXT_PAD - 14 - (time == null ? 0 : timeWidth + 4));
        graphics.drawString(font, statusLine.text(), x + TEXT_PAD + 8, y + 42, statusColor, false);
        if (time != null) {
            String shortTime = TextClip.text(font, time, 84);
            graphics.drawString(font, shortTime, x + width - 6 - font.width(shortTime), y + 42, DIM_COLOR, false);
        }

        BoardMaterials.Material hovered = over ? hoveredMaterial(font, cursor, x, y, width, materials) : null;
        return over ? new Hover(tile, hovered) : null;
    }

    private static BoardMaterials.@Nullable Material hoveredMaterial(
            Font font, Cursor cursor, int x, int y, int width, List<BoardMaterials.Material> materials) {
        if (cursor.y() < y + MATERIAL_Y || cursor.y() >= y + MATERIAL_Y + 16) {
            return null;
        }
        int materialX = x + TEXT_PAD + 3;
        for (int i = 0; i < Math.min(2, materials.size()); i++) {
            String label = label(materials.get(i));
            int chipWidth = BoardLayout.materialChipWidth(font.width(label));
            if (materialX + chipWidth > x + width - 6) {
                return null;
            }
            if (cursor.x() >= materialX && cursor.x() < materialX + chipWidth) {
                return materials.get(i);
            }
            materialX += chipWidth + 4;
        }
        return null;
    }

    private static String label(BoardMaterials.Material material) {
        if (material.have() != null) {
            return material.have() + "/" + material.count();
        }
        return "×" + material.count();
    }

    private static String statusText(BoardTile tile, Context context) {
        if (tile.unfinished()) {
            return BoardTileTexts.unfinished().getString();
        }
        List<BoardWaitReasons.Wait> waits = context.waitReasons().getOrDefault(tile.id(), List.of());
        if (!waits.isEmpty()) {
            return BoardTileTexts.waitText(waits.get(0), context.byId()).getString();
        }
        return Component.translatableWithFallback(
                        KEY + "status." + tile.status().toLowerCase(Locale.ROOT), tile.status())
                .getString();
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

    static @Nullable ItemStack stack(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        return new ItemStack(item);
    }
}
