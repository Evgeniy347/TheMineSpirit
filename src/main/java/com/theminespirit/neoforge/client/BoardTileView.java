package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.BoardClock;
import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.BoardMaterials;
import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardTileGroup;
import com.theminespirit.core.ui.BoardTimeView;
import com.theminespirit.core.ui.BoardWaitReasons;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class BoardTileView {
    static final int TEXT_PAD = 4;
    static final int STRIPE = 2;
    static final int ICON = 16;
    static final int ICON_GAP = 2;
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
        int maxIcons = Math.max(1, (width - 2 * TEXT_PAD) / (ICON + ICON_GAP));
        List<BoardMaterials.Material> icons = allIcons.size() > maxIcons ? allIcons.subList(0, maxIcons) : allIcons;
        int iconsWidth = icons.isEmpty() ? 0 : icons.size() * (ICON + ICON_GAP) - ICON_GAP;
        int textWidth = Math.max(0, width - 2 * TEXT_PAD - (iconsWidth > 0 ? iconsWidth + 2 : 0));
        int lineStep = BoardLayout.TILE_LINE_STEP;
        int lineY = y + BoardLayout.TILE_TEXT_TOP;
        graphics.drawString(
                font, font.plainSubstrByWidth(taskText(tile).getString(), textWidth), x + TEXT_PAD, lineY, TEXT_COLOR);
        lineY += lineStep;
        graphics.drawString(
                font,
                font.plainSubstrByWidth(detailsText(tile).getString(), width - 2 * TEXT_PAD),
                x + TEXT_PAD,
                lineY,
                MUTED_COLOR);
        lineY += lineStep;
        Component time = timeText(tile);
        if (time != null) {
            lineY = drawLine(graphics, font, time, x, lineY, width, MUTED_COLOR);
        }
        Component spent = spentText(tile);
        if (spent != null) {
            lineY = drawLine(graphics, font, spent, x, lineY, width, MUTED_COLOR);
        }
        String third = null;
        int thirdColor = UNRESOLVED_COLOR;
        List<BoardWaitReasons.Wait> waits = context.waitReasons().get(tile.id());
        if (tile.unfinished()) {
            third = Component.translatable(KEY + "unfinished").getString();
            thirdColor = FAILED_COLOR;
        } else if (waits != null && !waits.isEmpty()) {
            third = waitText(waits.get(0), context.byId()).getString();
        }
        if (third != null) {
            lineY = drawLine(graphics, font, Component.literal(third), x, lineY, width, thirdColor);
        }
        if (allIcons.size() > maxIcons) {
            texts.addAll(allIcons.subList(maxIcons, allIcons.size()));
        }
        for (BoardMaterials.Material material : texts) {
            if (lineY + BoardLayout.TILE_LINE_STEP > y + BoardLayout.TILE_HEIGHT) {
                break;
            }
            lineY = drawLine(graphics, font, materialText(material), x, lineY, width, UNRESOLVED_COLOR);
        }
        return renderIcons(graphics, font, icons, rect, cursor, tile);
    }

    private static @Nullable Hover renderIcons(
            GuiGraphics graphics,
            Font font,
            List<BoardMaterials.Material> icons,
            BoardLayout.Rect rect,
            Cursor cursor,
            BoardTile tile) {
        int startX = rect.x() + rect.width() - TEXT_PAD + ICON_GAP - icons.size() * (ICON + ICON_GAP);
        int iconY = rect.y() + 2;
        BoardMaterials.Material hovered = null;
        for (int i = 0; i < icons.size(); i++) {
            BoardMaterials.Material material = icons.get(i);
            ItemStack stack = stack(material.item());
            if (stack == null) {
                continue;
            }
            int iconX = startX + i * (ICON + ICON_GAP);
            graphics.renderItem(stack, iconX, iconY);
            graphics.renderItemDecorations(font, stack, iconX, iconY, countText(material.count()));
            if (cursor.x() >= iconX && cursor.x() < iconX + ICON && cursor.y() >= iconY && cursor.y() < iconY + ICON) {
                hovered = material;
            }
        }
        return hovered == null ? null : new Hover(tile, hovered);
    }

    static List<Component> tooltip(
            BoardTile tile, Map<String, List<BoardWaitReasons.Wait>> waitReasons, Map<String, BoardTile> byId) {
        List<Component> lines = new ArrayList<>();
        lines.add(taskText(tile));
        lines.add(Component.translatable(KEY + "group." + tile.group().name().toLowerCase(Locale.ROOT)));
        if (tile.unfinished()) {
            lines.add(Component.translatable(KEY + "unfinished"));
        }
        lines.add(Component.translatableWithFallback(
                KEY + "status." + tile.status().toLowerCase(Locale.ROOT), tile.status()));
        lines.add(detailsText(tile));
        Component time = timeText(tile);
        if (time != null) {
            lines.add(time);
        }
        Component spent = spentText(tile);
        if (spent != null) {
            lines.add(spent);
        }
        for (BoardMaterials.Material material : BoardMaterials.of(tile)) {
            lines.add(materialText(material));
        }
        for (BoardWaitReasons.Wait wait : waitReasons.getOrDefault(tile.id(), List.of())) {
            lines.add(waitText(wait, byId));
        }
        return lines;
    }

    static List<Component> materialTooltip(BoardMaterials.Material material) {
        List<Component> lines = new ArrayList<>();
        lines.add(itemName(material.item()));
        lines.add(materialText(material));
        return lines;
    }

    private static int drawLine(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), width - 2 * TEXT_PAD), x + TEXT_PAD, y, color);
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

    static Component materialText(BoardMaterials.Material material) {
        String key = switch (material.kind()) {
            case GATHER -> KEY + "material.gather";
            case SUPPLY -> KEY + "material.supply";
            case MISSING -> KEY + "material.missing";
        };
        return Component.translatable(key, itemName(material.item()), Component.literal(countText(material.count())));
    }

    private static String countText(int count) {
        return Integer.toString(count);
    }

    private static @Nullable Component timeText(BoardTile tile) {
        BoardTimeView time = BoardTimeView.of(tile);
        if (!time.showsMoment()) {
            return null;
        }
        MutableComponent started = Component.translatable(KEY + "time.started", momentText(time.startedAt()));
        if (time.showsFinish()) {
            started.append(" · ").append(Component.translatable(KEY + "time.finished", momentText(time.finishedAt())));
        }
        return started;
    }

    private static @Nullable Component spentText(BoardTile tile) {
        BoardTimeView time = BoardTimeView.of(tile);
        if (!time.showsSpent()) {
            return null;
        }
        return Component.translatable(KEY + (time.running() ? "time.running" : "time.spent"), time.minutes());
    }

    private static Component momentText(long tick) {
        BoardClock.Moment moment = BoardClock.moment(tick);
        return Component.translatable(
                KEY + "time.moment",
                moment.day(),
                BoardClock.twoDigits(moment.hour()),
                BoardClock.twoDigits(moment.minute()));
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
        MutableComponent text = Component.translatable(KEY + "details", tile.priority(), spirit);
        for (String detail : tile.details()) {
            text.append(" · ").append(Component.literal(detail));
        }
        return text;
    }

    private static Component waitText(BoardWaitReasons.Wait wait, Map<String, BoardTile> byId) {
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
            case DEPENDENCY -> Component.translatable(KEY + "wait.dependency", dependencyText(wait.subject(), byId));
            case BLOCKED -> Component.translatable(KEY + "wait.blocked");
            case FAILED -> Component.translatable(KEY + "wait.failed");
            case CANCELLED -> Component.translatable(KEY + "wait.cancelled");
        };
    }

    private static Component dependencyText(String id, Map<String, BoardTile> byId) {
        BoardTile dependency = byId.get(id);
        return dependency == null ? Component.literal(id) : taskText(dependency);
    }

    static Component itemName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return Component.literal(id);
        }
        return BuiltInRegistries.ITEM.get(key).getDescription();
    }
}
