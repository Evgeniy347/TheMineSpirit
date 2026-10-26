package com.theminespirit.core.board;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.BuildSchematicParams;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.SchemeBuild;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StagePlans {
    public static final String FENCE = "minecraft:oak_fence";
    public static final String TORCH = "minecraft:torch";
    public static final String AIR = "minecraft:air";
    public static final String TABLE = "minecraft:crafting_table";
    public static final String FURNACE = "minecraft:furnace";
    private static final String TABLE_BLOCK = "crafting_table";
    private static final String MINECRAFT = "minecraft:";
    private static final int GROVE_STEP = 2;
    private static final int CHEST_PLANKS = 20;
    private static final int FURNACE_STONE = 8;

    private StagePlans() {}

    public static Optional<TaskNode> plan(BoardCase boardCase, int level, TicketTarget target) {
        StageWork work = target.work();
        if (work == null) {
            return Optional.empty();
        }
        StageSite site = work.site();
        if (boardCase == BoardCase.REPAIR_BUILDING && !work.repairs().isEmpty()) {
            BoardCase repaired = BoardCase.valueOf(work.repairs());
            return Optional.of(
                    TicketPlans.plan(repaired, 1, target.withWork(new StageWork(site, work.blocks(), work.builds()))));
        }
        return switch (boardCase) {
            case BUILD_FENCE, LIGHTING, PLANT_GROVE ->
                Optional.of(PlanSteps.placeAll(target, work.blocks(), work.builds()));
            case STOCK_CHESTS -> Optional.of(chests(target, site, work.builds()));
            case CRAFT_FURNACE -> Optional.of(station(target, site.workYard(), work.builds()));
            case BUILD_HOUSE -> level == 1 ? Optional.of(hut(target, site)) : Optional.empty();
            case DISMANTLE_SHELTER -> Optional.of(demolish(work.blocks()));
            default -> Optional.empty();
        };
    }

    public static TaskNode groveAround(TicketTarget target) {
        String sapling = sapling(target.wood() == null ? WoodSpecies.OAK : target.wood());
        BlockCoord center = target.target();
        List<PlannedBlock> blocks = new ArrayList<>();
        for (int dx : new int[] {-GROVE_STEP, GROVE_STEP}) {
            for (int dz : new int[] {-GROVE_STEP, GROVE_STEP}) {
                blocks.add(new PlannedBlock(new BlockCoord(center.x() + dx, center.y(), center.z() + dz), sapling));
            }
        }
        return PlanSteps.placeAll(target, blocks, List.of());
    }

    public static String sapling(String wood) {
        return MINECRAFT + WoodSpecies.swap("oak_sapling", wood);
    }

    private static TaskNode hut(TicketTarget target, StageSite site) {
        String scheme = DataTables.INSTANCE
                .stages()
                .current()
                .scheme(BoardCase.BUILD_HOUSE, 1)
                .orElseThrow(() -> new IllegalStateException(
                        "stages.json: нет схемы дома уровня 1 — хижине этапа 1 не по чему строиться"));
        BlockCoord origin = site.houseOrigin();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("file", scheme);
        params.put("x", origin.x());
        params.put("y", origin.y());
        params.put("z", origin.z());
        if (target.wood() != null) {
            params.put(BuildSchematicParams.WOOD, target.wood());
        }
        List<TaskNode> steps = new ArrayList<>();
        steps.add(PlanSteps.gotoPoint(site.workYard()));
        DataTables.INSTANCE
                .presets()
                .current()
                .byId(scheme)
                .ifPresent(preset -> PresetLibrary.countMaterials(preset.schematic())
                        .forEach((item, count) -> {
                            String id = target.wood() == null ? item : WoodSpecies.swap(item, target.wood());
                            steps.add(PlanSteps.acquire(
                                    id.contains(":") ? id : MINECRAFT + id, Math.min(count, CatalogLimits.COUNT_MAX)));
                        }));
        steps.add(new ScriptCall("build_schematic", Map.copyOf(params)));
        return new SequenceNode(steps);
    }

    private static TaskNode chests(TicketTarget target, StageSite site, List<SchemeBuild> builds) {
        List<TaskNode> steps = new ArrayList<>();
        steps.add(PlanSteps.gotoPoint(site.workYard()));
        String wood = target.wood() == null ? WoodSpecies.OAK : target.wood();
        steps.add(PlanSteps.withdraw(MINECRAFT + wood + "_planks", CHEST_PLANKS));
        for (SchemeBuild build : builds) {
            steps.add(
                    build.only().contains(TABLE_BLOCK)
                            ? PlanSteps.craft(TABLE, 1)
                            : PlanSteps.craft("minecraft:chest", 2));
            steps.add(PlanSteps.build(build, target.wood()));
        }
        return new SequenceNode(steps);
    }

    private static TaskNode station(TicketTarget target, BlockCoord yard, List<SchemeBuild> builds) {
        List<TaskNode> steps = new ArrayList<>();
        steps.add(PlanSteps.gotoPoint(yard));
        steps.add(PlanSteps.withdraw("minecraft:cobblestone", FURNACE_STONE));
        for (SchemeBuild build : builds) {
            steps.add(PlanSteps.craft(FURNACE, 1));
            steps.add(PlanSteps.build(build, target.wood()));
        }
        return new SequenceNode(steps);
    }

    private static TaskNode demolish(List<PlannedBlock> blocks) {
        List<TaskNode> steps = new ArrayList<>();
        if (!blocks.isEmpty()) {
            steps.add(PlanSteps.gotoPoint(blocks.get(0).at()));
        }
        blocks.forEach(block -> steps.add(PlanSteps.clear(block.at(), true)));
        return new SequenceNode(steps);
    }
}
