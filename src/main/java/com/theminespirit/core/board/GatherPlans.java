package com.theminespirit.core.board;

import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GatherPlans {
    private static final int LOGS_PER_TREE = 4;
    private static final int PLANKS_PER_LOG = 4;
    private static final String PLANKS_SUFFIX = "_planks";
    private static final int LEAVES_PER_SAPLING = 20;
    private static final String COUNT = "count";
    private static final String ITEM = "item";
    private static final String ACQUIRE = "acquire";
    private static final String DIRT_PATH = "dirt_path";
    private static final String WOODEN_SHOVEL = "minecraft:wooden_shovel";
    private static final String SPECIES = "species";
    private static final String RADIUS = "radius";
    private static final String MINECRAFT = "minecraft:";
    private static final String AXE = "*_axe";

    private GatherPlans() {}

    static Optional<TaskNode> plan(TicketTarget target, String item, int batch) {
        Optional<TaskNode> portal = PortalPlans.special(target, item, batch);
        if (portal.isPresent()) {
            return portal;
        }
        Optional<TaskNode> saplings = saplingPlan(item, batch);
        if (saplings.isPresent()) {
            return saplings;
        }
        Optional<TaskNode> stripped = stripPlan(target, item, batch);
        if (stripped.isPresent()) {
            return stripped;
        }
        Optional<TaskNode> planks = planksPlan(target, item, batch);
        if (planks.isPresent()) {
            return planks;
        }
        Optional<TaskNode> path = pathPlan(target, item, batch);
        if (path.isPresent()) {
            return path;
        }
        Optional<TreeSpecies> tree = treeOf(item);
        if (tree.isEmpty()) {
            return Optional.empty();
        }
        int trees = Math.min(CatalogLimits.COUNT_MAX, (batch + LOGS_PER_TREE - 1) / LOGS_PER_TREE);
        ScriptCall acquire = new ScriptCall(ACQUIRE, Map.of(ITEM, item, COUNT, batch, "fresh", true));
        return Optional.of(chopping(target, tree.get(), trees, acquire));
    }

    private static Optional<TaskNode> saplingPlan(String item, int batch) {
        Optional<TreeSpecies> sapling = saplingSpeciesOf(item);
        if (sapling.isEmpty()) {
            return Optional.empty();
        }
        TreeSpecies species = sapling.get();
        int trees = Math.min(CatalogLimits.COUNT_MAX, Math.max(1, (batch + 1) / 2));
        int leaves = Math.max(16, batch * LEAVES_PER_SAPLING);
        List<TaskNode> steps = new ArrayList<>();
        steps.add(axe());
        steps.add(new ScriptCall(
                "chop_tree",
                Map.of(SPECIES, species.id(), COUNT, trees, RADIUS, BoardTuning.chopRadius(), "replant", false)));
        int left = leaves;
        while (left > 0) {
            int part = Math.min(left, CatalogLimits.COUNT_MAX);
            steps.add(new ScriptCall(
                    "gather",
                    Map.of(
                            "block",
                            pathOf(species.leavesId()),
                            COUNT,
                            part,
                            RADIUS,
                            CatalogLimits.HARVEST_RADIUS_MAX)));
            left -= part;
        }
        return Optional.of(new SequenceNode(steps));
    }

    private static Optional<TaskNode> planksPlan(TicketTarget target, String item, int batch) {
        String name = pathOf(item);
        if (!name.endsWith(PLANKS_SUFFIX)) {
            return Optional.empty();
        }
        String species = name.substring(0, name.length() - PLANKS_SUFFIX.length());
        Optional<TreeSpecies> tree = TreeSpecies.ALL.stream()
                .filter(candidate -> candidate.id().equals(species))
                .findFirst();
        if (tree.isEmpty()) {
            return Optional.empty();
        }
        int logs = Math.max(1, (batch + PLANKS_PER_LOG - 1) / PLANKS_PER_LOG);
        int trees = Math.min(CatalogLimits.COUNT_MAX, Math.max(1, (logs + LOGS_PER_TREE - 1) / LOGS_PER_TREE));
        ScriptCall acquire = new ScriptCall(ACQUIRE, Map.of(ITEM, item, COUNT, batch, "fresh", true));
        return Optional.of(chopping(target, tree.get(), trees, acquire));
    }

    private static TaskNode chopping(TicketTarget target, TreeSpecies tree, int trees, TaskNode withoutTrees) {
        TaskNode chop = new ScriptCall(
                "chop_tree",
                Map.of(SPECIES, tree.id(), COUNT, trees, RADIUS, BoardTuning.chopRadius(), "replant", true));
        return sequence(TicketPlans.gotoTarget(target), new FallbackNode(List.of(sequence(axe(), chop), withoutTrees)));
    }

    private static Optional<TaskNode> pathPlan(TicketTarget target, String item, int batch) {
        if (!DIRT_PATH.equals(pathOf(item))) {
            return Optional.empty();
        }
        List<TaskNode> steps = new ArrayList<>();
        steps.add(TicketPlans.gotoTarget(target));
        steps.add(new ScriptCall(ACQUIRE, Map.of(ITEM, WOODEN_SHOVEL, COUNT, 1)));
        steps.add(new ScriptCall(
                "gather", Map.of("block", DIRT_PATH, COUNT, batch, RADIUS, CatalogLimits.HARVEST_RADIUS_MAX)));
        return Optional.of(new SequenceNode(steps));
    }

    private static Optional<TaskNode> stripPlan(TicketTarget target, String item, int batch) {
        Optional<TreeSpecies> stripped = strippedSpeciesOf(item);
        if (stripped.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sequence(
                TicketPlans.gotoTarget(target),
                axe(),
                new ScriptCall(
                        "strip_log",
                        Map.of(SPECIES, stripped.get().id(), COUNT, batch, RADIUS, BoardTuning.chopRadius())),
                new ScriptCall(
                        "collect_drops",
                        Map.of(RADIUS, CatalogLimits.DROP_RADIUS_MAX, "filter", List.of(pathOf(item))))));
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
        String full = item.startsWith(MINECRAFT) ? item : MINECRAFT + item;
        return TreeSpecies.ALL.stream()
                .filter(tree -> tree.logId().equals(full))
                .findFirst();
    }

    private static String pathOf(String item) {
        return item.startsWith(MINECRAFT) ? item.substring(MINECRAFT.length()) : item;
    }

    private static TaskNode axe() {
        return new FallbackNode(List.of(
                new ScriptCall(ACQUIRE, Map.of(ITEM, AXE, COUNT, 1)),
                new ScriptCall(ACQUIRE, Map.of(ITEM, "wooden_axe", COUNT, 1))));
    }

    private static TaskNode sequence(TaskNode... steps) {
        return new SequenceNode(List.of(steps));
    }
}
