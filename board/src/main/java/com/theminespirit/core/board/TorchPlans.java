package com.theminespirit.core.board;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class TorchPlans {
    private static final String TORCH = "minecraft:torch";
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String TICKS = "ticks";
    private static final String ACQUIRE = "acquire";
    private static final String CRAFT = "craft";
    private static final String RADIUS = "radius";
    private static final int TORCHES_PER_CRAFT = 4;
    private static final String WAIT = "wait";
    private static final String BLOCK = "block";
    private static final List<BlockCoord> LIGHT_OFFSETS = List.of(
            new BlockCoord(-3, 0, -3), new BlockCoord(3, 0, -3), new BlockCoord(-3, 0, 3), new BlockCoord(3, 0, 3));
    private static final List<String> LEFTOVERS = List.of(
            "minecraft:stick",
            "minecraft:*_planks",
            "minecraft:*_log",
            "minecraft:cobblestone",
            "minecraft:charcoal",
            "minecraft:coal",
            TORCH);

    private TorchPlans() {}

    static void addMaterials(List<TaskNode> steps, Map<String, Integer> materials, String wood, TicketTarget target) {
        int torches = materials.getOrDefault(TORCH, 0);
        materials.forEach((item, count) -> {
            if (!TORCH.equals(item)) {
                steps.add(material(target, WoodSpecies.swap(item, wood), Math.min(count, CatalogLimits.COUNT_MAX)));
            }
        });
        if (torches > 0) {
            steps.add(new FallbackNode(List.of(
                    new SequenceNode(supply(torches)),
                    new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))));
        }
    }

    private static TaskNode material(TicketTarget target, String item, int count) {
        return GatherPlans.plan(target, item, count)
                .orElseGet(() -> new ScriptCall(ACQUIRE, Map.of(ITEM, item, COUNT, count)));
    }

    static List<TaskNode> supply(int torches) {
        int crafts = (torches + TORCHES_PER_CRAFT - 1) / TORCHES_PER_CRAFT;
        return List.of(
                new FallbackNode(List.of(
                        new ScriptCall(ACQUIRE, Map.of(ITEM, "minecraft:charcoal", COUNT, crafts)),
                        new ScriptCall(ACQUIRE, Map.of(ITEM, "minecraft:coal", COUNT, crafts)))),
                new ScriptCall(ACQUIRE, Map.of(ITEM, "minecraft:stick", COUNT, crafts)),
                new ScriptCall(
                        CRAFT, Map.of(ITEM, TORCH, COUNT, torches, RADIUS, CatalogLimits.STATION_RADIUS_DEFAULT)));
    }

    static TaskNode lighting(TicketTarget target) {
        List<BlockCoord> torches = LIGHT_OFFSETS.stream()
                .map(offset -> new BlockCoord(
                        target.target().x() + offset.x(),
                        target.target().y(),
                        target.target().z() + offset.z()))
                .toList();
        List<TaskNode> steps = new ArrayList<>(torches.size() + 4);
        steps.add(TicketPlans.gotoTarget(target));
        steps.addAll(supply(torches.size()));
        for (BlockCoord torch : torches) {
            steps.add(new ScriptCall(
                    "place_block", Map.of(BLOCK, TORCH, "x", torch.x(), "y", torch.y(), "z", torch.z())));
        }
        return new SequenceNode(steps);
    }

    static TaskNode plan(TicketTarget target) {
        int sticks = (TicketPlans.torchCount(target) + 3) / 4;
        return new SequenceNode(List.of(
                TicketPlans.gotoTarget(target),
                TicketPlans.takeFrom("minecraft:coal", sticks),
                TicketPlans.takeFrom("minecraft:charcoal", sticks),
                new FallbackNode(List.of(
                        new ScriptCall(ACQUIRE, Map.of(ITEM, "minecraft:stick", COUNT, sticks)),
                        new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))),
                TicketPlans.craft(TORCH, TicketPlans.torchCount(target), target),
                TicketPlans.store(
                        target,
                        LEFTOVERS.stream().filter(item -> !TORCH.equals(item)).toArray(String[]::new))));
    }
}
