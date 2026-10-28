package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

final class CraftOrdersView {
    static final String WAITING = "waiting";
    private static final String CANCELLED = "cancelled";
    private static final String INVENTORY = "inventory";
    private static final String KEY = ModIdentity.MOD_ID + ".order.";

    private CraftOrdersView() {}

    static List<Component> tooltip(CraftTerminalChannel.Order order) {
        List<Component> lines = new ArrayList<>();
        lines.add(
                Component.translatable(KEY + "title", order.id(), order.target().getHoverName(), order.count()));
        lines.add(status(order.status()));
        for (CraftTerminalChannel.Line line : order.missing()) {
            lines.add(Component.translatable(
                    KEY + "lack", line.count(), line.sample().getHoverName()));
        }
        if (WAITING.equals(order.status())) {
            lines.add(Component.translatable(KEY + "cancel_hint"));
        }
        return lines;
    }

    private static Component status(String status) {
        return switch (status) {
            case WAITING -> Component.translatable(KEY + "status.waiting");
            case CANCELLED -> Component.translatable(KEY + "status.cancelled");
            case INVENTORY ->
                Component.translatable(KEY + "status.done", Component.translatable(KEY + "where.inventory"));
            default -> Component.translatable(KEY + "status.done", Component.translatable(KEY + "where.chest", status));
        };
    }
}
