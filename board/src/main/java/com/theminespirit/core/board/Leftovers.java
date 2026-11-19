package com.theminespirit.core.board;

import com.theminespirit.core.base.NamespacedId;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;

final class Leftovers {
    private static final String TORCH = "minecraft:torch";
    static final List<String> ITEMS = List.of(
            "minecraft:stick",
            "minecraft:*_planks",
            "minecraft:*_log",
            "minecraft:cobblestone",
            "minecraft:charcoal",
            "minecraft:coal",
            TORCH);

    private Leftovers() {}

    static TaskNode with(TaskNode step, TicketTarget target) {
        List<String> items = new ArrayList<>(ITEMS);
        if (target.leveling() != null) {
            String raw = target.leveling().block();
            String block = raw.contains(":") ? raw : NamespacedId.MINECRAFT + raw;
            if (!(NamespacedId.MINECRAFT + PlotLeveling.DEFAULT_BLOCK).equals(block) && !items.contains(block)) {
                items.add(block);
            }
        }
        return new SequenceNode(List.of(step, TicketWorkPlans.store(target, items.toArray(String[]::new))));
    }
}
