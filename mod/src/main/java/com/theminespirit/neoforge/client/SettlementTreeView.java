package com.theminespirit.neoforge.client;

import com.mojang.math.Axis;
import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.SettlementTree;
import com.theminespirit.core.ui.SettlementTreeStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class SettlementTreeView {
    private static final String KEY = "theminespirit.tree.";
    private static final String CHECK = "\u2714";
    private static final int LABEL_WIDTH = 110;
    private static final int MINIMAP_WIDTH = 84;
    private static final int MINIMAP_HEIGHT = 58;

    record Viewpoint(int x, int y) {}

    private SettlementTreeView() {}

    static SettlementTree.@Nullable Node draw(
            GuiGraphics graphics,
            Font font,
            SettlementTree model,
            BoardLayout.Rect area,
            double scale,
            int panX,
            int panY,
            @Nullable String selectedId,
            Viewpoint mouse) {
        int mouseX = mouse.x();
        int mouseY = mouse.y();
        int contentWidth = (int) Math.ceil(model.width() * scale);
        int contentHeight = (int) Math.ceil(model.height() * scale);
        int left = area.x() + Math.max(4, (area.width() - contentWidth) / 2) + panX;
        int top = area.y() + Math.max(4, (area.height() - contentHeight) / 2) + panY;
        drawLinks(graphics, model, left, top, scale);
        SettlementTree.Node hovered = null;
        for (SettlementTree.Node node : model.nodes()) {
            int x = left + (int) Math.round(node.x() * scale);
            int y = top + (int) Math.round(node.y() * scale);
            int size = Math.max(8, (int) Math.round(SettlementTreeStyle.NODE * scale));
            drawNode(graphics, font, node, x, y, size, scale, node.id().equals(selectedId));
            if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
                hovered = node;
            }
        }
        drawMinimap(graphics, model, area);
        return hovered;
    }

    static List<Component> tooltip(SettlementTree.Node node) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(node.title()));
        if (node.level() > 0) {
            lines.add(Component.translatable(KEY + "level", node.level()));
        }
        lines.add(stateText(node));
        if (!node.detail().isEmpty()) {
            lines.add(Component.literal(node.detail()));
        }
        if (!node.requirements().isEmpty()) {
            lines.add(Component.translatable(KEY + "requires", String.join(", ", node.requirements())));
        }
        if (!node.missing().isEmpty()) {
            lines.add(Component.translatable(KEY + "missing", materials(node.missing())));
        }
        SettlementTree.Position at = node.at();
        if (at != null) {
            lines.add(Component.translatable(KEY + "where", at.x(), at.y(), at.z()));
        }
        if (node.optional()) {
            lines.add(Component.translatable(KEY + "optional"));
        } else if (!node.blocking() && node.kind() == SettlementTree.Kind.BUILDING) {
            lines.add(Component.translatable(KEY + "nonblocking"));
        }
        return lines;
    }

    static Component stateText(SettlementTree.Node node) {
        if (node.kind() == SettlementTree.Kind.ROOT) {
            return Component.translatable(KEY + "state.root");
        }
        if (node.kind() == SettlementTree.Kind.GATE) {
            return Component.translatable(
                    KEY + (node.state() == SettlementTree.State.READY ? "gate.open" : "gate.closed"));
        }
        return switch (node.state()) {
            case READY -> Component.translatable(KEY + "state.ready");
            case IN_PROGRESS -> Component.translatable(KEY + "state.in_progress", node.percent());
            case NOT_STARTED -> Component.translatable(KEY + "state.not_started");
            case LOCKED -> Component.translatable(KEY + "state.locked");
            case UNKNOWN -> Component.translatable(KEY + "state.unknown");
        };
    }

    static int stateColor(SettlementTree.State state) {
        return switch (state) {
            case READY -> 0xFFE0B050;
            case IN_PROGRESS -> 0xFF6FA548;
            case NOT_STARTED -> 0xFF8A857C;
            case LOCKED -> 0xFF4A4742;
            case UNKNOWN -> 0xFF8B8B8B;
        };
    }

    private static void drawLinks(GuiGraphics graphics, SettlementTree model, int left, int top, double scale) {
        Map<String, SettlementTree.Node> byId = new HashMap<>();
        model.nodes().forEach(node -> byId.put(node.id(), node));
        int thickness = Math.max(1, (int) Math.round(scale * 1.5));
        for (SettlementTree.Edge edge : model.edges()) {
            SettlementTree.Node from = byId.get(edge.from());
            SettlementTree.Node to = byId.get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            int color = SettlementTreeStyle.link(from.state(), to.state());
            List<SettlementTree.Point> points = edge.points();
            for (int index = 1; index < points.size(); index++) {
                SettlementTree.Point first = points.get(index - 1);
                SettlementTree.Point second = points.get(index);
                int x1 = left + (int) Math.round(first.x() * scale);
                int y1 = top + (int) Math.round(first.y() * scale);
                int x2 = left + (int) Math.round(second.x() * scale);
                int y2 = top + (int) Math.round(second.y() * scale);
                if (y1 == y2) {
                    graphics.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + thickness, color);
                } else {
                    graphics.fill(x1, Math.min(y1, y2), x1 + thickness, Math.max(y1, y2) + 1, color);
                }
            }
        }
    }

    private static void drawNode(
            GuiGraphics graphics,
            Font font,
            SettlementTree.Node node,
            int x,
            int y,
            int size,
            double scale,
            boolean selected) {
        int border = selected ? 0xFFFFFFFF : SettlementTreeStyle.border(node.state());
        int fill = SettlementTreeStyle.fill(node.state());
        if (node.kind() == SettlementTree.Kind.GATE) {
            float centerX = x + size / 2f;
            float centerY = y + size / 2f;
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, centerY, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(45));
            graphics.fill(
                    (int) (-size * 0.62f), (int) (-size * 0.62f), (int) (size * 0.62f), (int) (size * 0.62f), border);
            graphics.fill(
                    (int) (-size * 0.52f), (int) (-size * 0.52f), (int) (size * 0.52f), (int) (size * 0.52f), fill);
            graphics.pose().popPose();
        } else {
            graphics.fill(x, y, x + size, y + size, border);
            graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, fill);
        }
        ItemStack stack = stack(node.icon());
        int iconSize = Math.min(16, size - 4);
        int iconX = x + (size - iconSize) / 2;
        int iconY = y + (size - iconSize) / 2;
        if (stack == null) {
            graphics.fill(x + size / 4, y + size / 4, x + size - size / 4, y + size - size / 4, 0xFF000000);
        } else {
            graphics.pose().pushPose();
            float factor = iconSize / 16f;
            graphics.pose().translate(iconX, iconY, 0);
            graphics.pose().scale(factor, factor, 1);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
        }
        int barWidth = (size - 4) * node.percent() / 100;
        if (barWidth > 0) {
            graphics.fill(x + 2, y + size - 4, x + 2 + barWidth, y + size - 2, border);
        }
        if (node.state() == SettlementTree.State.READY) {
            graphics.fill(x + size - 8, y + 1, x + size - 1, y + 8, 0xFFE0B050);
            graphics.drawString(font, CHECK, x + size - 8, y, 0xFF3A3010, false);
        } else if (node.state() == SettlementTree.State.UNKNOWN) {
            graphics.fill(x + size - 8, y + 1, x + size - 1, y + 8, 0xFF5A5A5A);
            graphics.drawString(font, "?", x + size - 7, y, 0xFFE0E0E0, false);
        }
        if (node.level() > 1 && scale >= 0.85) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(KEY + "level.short", node.level()),
                    x + size / 2,
                    y - 9,
                    SettlementTreeStyle.MUTED);
        }
        if (scale >= 0.85) {
            int color =
                    node.state() == SettlementTree.State.LOCKED ? SettlementTreeStyle.MUTED : SettlementTreeStyle.TEXT;
            String title = TextClip.text(font, Component.literal(node.title()), LABEL_WIDTH);
            graphics.drawCenteredString(font, title, x + size / 2, y + size + 3, color);
        }
    }

    private static void drawMinimap(GuiGraphics graphics, SettlementTree model, BoardLayout.Rect area) {
        int width = Math.min(MINIMAP_WIDTH, area.width() - 16);
        int height = Math.min(MINIMAP_HEIGHT, area.height() - 16);
        if (width < 20 || height < 16) {
            return;
        }
        int x = area.right() - width - 8;
        int y = area.bottom() - height - 8;
        graphics.fill(x, y, x + width, y + height, 0xD00A0D10);
        graphics.fill(x, y, x + width, y + 1, 0xFF20262D);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF20262D);
        double scale =
                Math.min((width - 6.0) / Math.max(1, model.width()), (height - 6.0) / Math.max(1, model.height()));
        for (SettlementTree.Node node : model.nodes()) {
            int nx = x + 3 + (int) Math.round(node.x() * scale);
            int ny = y + 3 + (int) Math.round(node.y() * scale);
            int size = Math.max(2, (int) Math.round(SettlementTreeStyle.NODE * scale));
            graphics.fill(nx, ny, nx + size, ny + size, stateColor(node.state()));
        }
    }

    private static String materials(Map<String, Integer> missing) {
        List<String> lines = new ArrayList<>();
        missing.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add(itemName(entry.getKey()) + " ×" + entry.getValue()));
        return String.join(", ", lines);
    }

    private static String itemName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return id;
        }
        return BuiltInRegistries.ITEM.get(key).getDescription().getString();
    }

    private static @Nullable ItemStack stack(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return null;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }
}
