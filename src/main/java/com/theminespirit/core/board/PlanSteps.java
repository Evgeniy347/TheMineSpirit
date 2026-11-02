package com.theminespirit.core.board;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchemeFilter;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.BuildSchematicParams;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.SchemeBuild;
import com.theminespirit.core.town.StageWork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class PlanSteps {
    static final String AIR = "minecraft:air";
    static final String MINECRAFT = "minecraft:";
    private static final String WAIT = "wait";
    private static final String TICKS = "ticks";
    private static final int PLANKS_PER_LOG = 4;
    private static final int PLANKS_PER_GATE = 4;
    private static final int FENCES_PER_CRAFT = 3;
    private static final int PLANKS_PER_FENCE_CRAFT = 5;
    private static final int YARD_REACH = 8;
    private static final int BUILD_HOP = 8;

    private PlanSteps() {}

    static TaskNode clear(BlockCoord at, boolean demolish) {
        return new FallbackNode(List.of(
                new ScriptCall("clear_area", clearParams(at, demolish)),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    static TaskNode clearStrict(BlockCoord at) {
        return new ScriptCall("clear_area", clearParams(at, true));
    }

    private static Map<String, Object> clearParams(BlockCoord at, boolean demolish) {
        return Map.of(
                "x1",
                at.x(),
                "y1",
                at.y(),
                "z1",
                at.z(),
                "x2",
                at.x(),
                "y2",
                at.y(),
                "z2",
                at.z(),
                "demolish",
                demolish);
    }

    static TaskNode place(PlannedBlock block, TicketTarget target) {
        BlockCoord at = block.at();
        if (AIR.equals(block.block())) {
            return clear(at, true);
        }
        String id = target.wood() == null ? block.block() : WoodSpecies.swap(block.block(), target.wood());
        ScriptCall put = new ScriptCall("place_block", Map.of("block", id, "x", at.x(), "y", at.y(), "z", at.z()));
        return new FallbackNode(List.of(
                put,
                new SequenceNode(List.of(clear(at, true), put)),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    static String item(PlannedBlock block, TicketTarget target) {
        String id = bare(block.block());
        return target.wood() == null ? id : WoodSpecies.swap(id, target.wood());
    }

    static String bare(String block) {
        int state = block.indexOf('[');
        return state < 0 ? block : block.substring(0, state);
    }

    static boolean ownCraft(String item) {
        return item.endsWith("_fence") || item.endsWith("_fence_gate");
    }

    static int woodPlanks(Map<String, Integer> counts) {
        int planks = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            int count = entry.getValue();
            if (entry.getKey().endsWith("_fence_gate")) {
                planks += count * PLANKS_PER_GATE;
            } else if (entry.getKey().endsWith("_fence")) {
                planks += (count + FENCES_PER_CRAFT - 1) / FENCES_PER_CRAFT * PLANKS_PER_FENCE_CRAFT;
            }
        }
        return planks;
    }

    static TaskNode withdraw(String item, int count) {
        return new FallbackNode(List.of(
                new ScriptCall(
                        "withdraw", Map.of("container", "nearest", "item", item, "count", count, "radius", YARD_REACH)),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    static ScriptCall craft(String item, int count) {
        return new ScriptCall(
                "craft", Map.of("item", item, "count", count, "radius", CatalogLimits.STATION_RADIUS_DEFAULT));
    }

    static TaskNode acquire(String item, int count) {
        return new FallbackNode(List.of(
                new ScriptCall("acquire", Map.of("item", item, "count", count)),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall("goto", Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    static TaskNode placeAll(TicketTarget target, List<PlannedBlock> blocks, List<SchemeBuild> builds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlannedBlock block : blocks) {
            if (!AIR.equals(block.block())) {
                counts.merge(item(block, target), 1, Integer::sum);
            }
        }
        String wood = target.wood() == null ? WoodSpecies.OAK : target.wood();
        builds.forEach(build -> materials(build)
                .forEach((item, count) -> counts.merge(
                        WoodSpecies.swap(item.contains(":") ? item : MINECRAFT + item, wood), count, Integer::sum)));
        List<TaskNode> steps = new ArrayList<>();
        StageWork work = target.work();
        if (work != null) {
            steps.add(gotoPoint(work.site().workYard()));
            counts.forEach((item, count) -> {
                if (!ownCraft(item)) {
                    steps.add(withdraw(item, Math.min(count, CatalogLimits.COUNT_MAX)));
                }
            });
            int logs = (woodPlanks(counts) + PLANKS_PER_LOG - 1) / PLANKS_PER_LOG;
            if (logs > 0) {
                steps.add(withdraw(MINECRAFT + wood + "_log", Math.min(logs, CatalogLimits.COUNT_MAX)));
            }
        }
        if (!blocks.isEmpty()) {
            steps.add(gotoPoint(blocks.get(0).at()));
        } else if (!builds.isEmpty()) {
            steps.add(gotoPoint(builds.get(0).origin()));
        }
        counts.forEach((item, count) -> steps.add(
                ownCraft(item)
                        ? craft(item, Math.min(count, CatalogLimits.COUNT_MAX))
                        : acquire(item, Math.min(count, CatalogLimits.COUNT_MAX))));
        for (PlannedBlock block : blocks) {
            steps.add(place(block, target));
        }
        BlockCoord last =
                blocks.isEmpty() ? null : blocks.get(blocks.size() - 1).at();
        addBuilds(steps, builds, last, target.wood(), 0);
        return new SequenceNode(steps);
    }

    static TaskNode placeCells(TicketTarget target, List<PlannedBlock> blocks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlannedBlock block : blocks) {
            if (!AIR.equals(block.block())) {
                counts.merge(item(block, target), 1, Integer::sum);
            }
        }
        List<TaskNode> steps = new ArrayList<>();
        counts.forEach((item, count) -> steps.add(
                ownCraft(item)
                        ? craft(item, Math.min(count, CatalogLimits.COUNT_MAX))
                        : acquire(item, Math.min(count, CatalogLimits.COUNT_MAX))));
        for (PlannedBlock block : blocks) {
            steps.add(place(block, target));
        }
        return new SequenceNode(steps);
    }

    static TaskNode buildsOnly(TicketTarget target, List<SchemeBuild> builds) {
        List<TaskNode> steps = new ArrayList<>();
        addBuilds(steps, builds, null, target.wood(), 1);
        return new SequenceNode(steps);
    }

    private static void addBuilds(
            List<TaskNode> steps,
            List<SchemeBuild> builds,
            @Nullable BlockCoord from,
            @Nullable String wood,
            int lift) {
        BlockCoord last = from;
        for (SchemeBuild build : builds) {
            BlockCoord origin = build.origin();
            if ((last == null && lift > 0) || (last != null && far(last, origin))) {
                steps.add(gotoPoint(new BlockCoord(origin.x(), origin.y() + lift, origin.z())));
            }
            steps.add(build(build, wood));
            last = build.origin();
        }
    }

    private static boolean far(BlockCoord from, BlockCoord to) {
        return Math.abs(from.x() - to.x()) + Math.abs(from.z() - to.z()) > BUILD_HOP;
    }

    static ScriptCall build(SchemeBuild build, @Nullable String wood) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("file", build.scheme());
        params.put("x", build.origin().x());
        params.put("y", build.origin().y());
        params.put("z", build.origin().z());
        if (build.rotation() != BuildRotation.NONE) {
            params.put("rotation", build.rotation().id());
        }
        if (!build.only().isEmpty()) {
            params.put(BuildSchematicParams.ONLY, String.join(",", build.only()));
        }
        if (wood != null) {
            params.put(BuildSchematicParams.WOOD, wood);
        }
        return new ScriptCall("build_schematic", Map.copyOf(params));
    }

    static Map<String, Integer> materials(SchemeBuild build) {
        Schematic schematic = DataTables.INSTANCE
                .presets()
                .current()
                .byId(build.scheme())
                .orElseThrow(() -> new IllegalStateException(
                        "presets.json: нет схемы '" + build.scheme() + "' — постройку не по чему строить"))
                .schematic();
        return PresetLibrary.countMaterials(SchemeFilter.only(schematic, build.only()));
    }
}
