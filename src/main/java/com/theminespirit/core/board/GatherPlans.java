package com.theminespirit.core.board;

import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GatherPlans {
    private static final int LOGS_PER_TREE = 4;

    private GatherPlans() {}

    static Optional<TaskNode> plan(TicketTarget target, String item, int batch) {
        Optional<TaskNode> saplings = saplingPlan(target, item, batch);
        if (saplings.isPresent()) {
            return saplings;
        }
        Optional<TaskNode> stripped = stripPlan(target, item, batch);
        if (stripped.isPresent()) {
            return stripped;
        }
        Optional<TreeSpecies> tree = treeOf(item);
        if (tree.isEmpty()) {
            return Optional.empty();
        }
        int trees = Math.min(CatalogLimits.COUNT_MAX, (batch + LOGS_PER_TREE - 1) / LOGS_PER_TREE);
        ScriptCall acquire = new ScriptCall("acquire", Map.of("item", item, "count", batch, "fresh", true));
        return Optional.of(sequence(
                TicketPlans.gotoTarget(target),
                new FallbackNode(List.of(
                        new ScriptCall(
                                "chop_tree",
                                Map.of(
                                        "species",
                                        tree.get().id(),
                                        "count",
                                        trees,
                                        "radius",
                                        BoardTuning.chopRadius(),
                                        "replant",
                                        true)),
                        acquire))));
    }

    private static Optional<TaskNode> saplingPlan(TicketTarget target, String item, int batch) {
        Optional<TreeSpecies> sapling = saplingSpeciesOf(item);
        if (sapling.isEmpty()) {
            return Optional.empty();
        }
        TreeSpecies species = sapling.get();
        int trees = Math.min(CatalogLimits.COUNT_MAX, Math.max(1, (batch + 1) / 2));
        int leaves = Math.min(CatalogLimits.COUNT_MAX, Math.max(16, batch * 16));
        return Optional.of(sequence(
                TicketPlans.gotoTarget(target),
                new ScriptCall(
                        "chop_tree",
                        Map.of(
                                "species",
                                species.id(),
                                "count",
                                trees,
                                "radius",
                                BoardTuning.chopRadius(),
                                "replant",
                                false)),
                new ScriptCall(
                        "gather",
                        Map.of(
                                "block",
                                pathOf(species.leavesId()),
                                "count",
                                leaves,
                                "radius",
                                CatalogLimits.HARVEST_RADIUS_MAX))));
    }

    private static Optional<TaskNode> stripPlan(TicketTarget target, String item, int batch) {
        Optional<TreeSpecies> stripped = strippedSpeciesOf(item);
        if (stripped.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sequence(
                TicketPlans.gotoTarget(target),
                new ScriptCall(
                        "strip_log",
                        Map.of(
                                "species",
                                stripped.get().id(),
                                "count",
                                batch,
                                "radius",
                                CatalogLimits.HARVEST_RADIUS_MAX)),
                new ScriptCall(
                        "collect_drops",
                        Map.of("radius", CatalogLimits.DROP_RADIUS_MAX, "filter", List.of(pathOf(item))))));
    }

    private static Optional<TreeSpecies> saplingSpeciesOf(String item) {
        String name = pathOf(item);
        return TreeSpecies.ALL.stream()
                .filter(tree -> (tree.id() + "_sapling").equals(name)
                        || pathOf(tree.saplingId()).equals(name))
                .findFirst();
    }

    private static Optional<TreeSpecies> strippedSpeciesOf(String item) {
        String name = pathOf(item);
        return TreeSpecies.ALL.stream()
                .filter(tree -> ("stripped_" + tree.id() + "_log").equals(name))
                .findFirst();
    }

    private static Optional<TreeSpecies> treeOf(String item) {
        String full = item.startsWith("minecraft:") ? item : "minecraft:" + item;
        return TreeSpecies.ALL.stream()
                .filter(tree -> tree.logId().equals(full))
                .findFirst();
    }

    private static String pathOf(String item) {
        return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
    }

    private static TaskNode sequence(TaskNode... steps) {
        return new SequenceNode(List.of(steps));
    }
}
