package com.theminespirit.neoforge.client;

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

    private SettlementTreeView() {}

    static SettlementTree.@Nullable Node draw(
            GuiGraphics graphics,
            Font font,
            SettlementTree model,
            BoardLayout.Rect inner,
            double offsetX,
            double offsetY,
            int mouseX,
            int mouseY) {
        int left = inner.x() + SettlementTreeStyle.PAD + (int) Math.round(offsetX);
        int top = inner.y() + SettlementTreeStyle.PAD + (int) Math.round(offsetY);
        drawLinks(graphics, model, left, top);
        SettlementTree.Node hovered = null;
        for (SettlementTree.Node node : model.nodes()) {
            int x = left + node.x();
            int y = top + node.y();
            drawNode(graphics, font, node, x, y);
            if (mouseX >= x
                    && mouseX < x + SettlementTreeStyle.NODE
                    && mouseY >= y
                    && mouseY < y + SettlementTreeStyle.NODE) {
                hovered = node;
            }
        }
        return hovered;
    }

    static List<Component> tooltip(SettlementTree.Node node) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(node.title()));
        if (node.level() > 0) {
            lines.add(Component.translatable(KEY + "level", node.level()));
        }
        lines.add(stateText(node));
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

    private static void drawLinks(GuiGraphics graphics, SettlementTree model, int left, int top) {
        Map<String, SettlementTree.Node> byId = new HashMap<>();
        model.nodes().forEach(node -> byId.put(node.id(), node));
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
                int x1 = left + first.x();
                int y1 = top + first.y();
                int x2 = left + second.x();
                int y2 = top + second.y();
                if (y1 == y2) {
                    graphics.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 1, color);
                } else {
                    graphics.fill(x1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2) + 1, color);
                }
            }
        }
    }

    private static void drawNode(GuiGraphics graphics, Font font, SettlementTree.Node node, int x, int y) {
        int size = SettlementTreeStyle.NODE;
        graphics.fill(x, y, x + size, y + size, SettlementTreeStyle.border(node.state()));
        graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, SettlementTreeStyle.fill(node.state()));
        ItemStack stack = stack(node.icon());
        if (stack == null) {
            graphics.fill(x + 6, y + 6, x + size - 6, y + size - 6, 0xFF000000);
        } else {
            graphics.renderItem(stack, x + 6, y + 6);
        }
        int barWidth = (size - 4) * node.percent() / 100;
        if (barWidth > 0) {
            graphics.fill(
                    x + 2, y + size - 4, x + 2 + barWidth, y + size - 2, SettlementTreeStyle.border(node.state()));
        }
        String label = label(node);
        int color = node.state() == SettlementTree.State.LOCKED ? SettlementTreeStyle.MUTED : SettlementTreeStyle.TEXT;
        graphics.drawCenteredString(font, label, x + size / 2, y + size + 2, color);
    }

    private static String label(SettlementTree.Node node) {
        if (node.state() == SettlementTree.State.READY) {
            return CHECK;
        }
        if (node.state() == SettlementTree.State.UNKNOWN) {
            return "?";
        }
        return node.percent() + "%";
    }

    private static Component stateText(SettlementTree.Node node) {
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
