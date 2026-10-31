package com.theminespirit.core.board;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.SchematicBlocks;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StagePlans {
    public static final String AIR = "minecraft:air";
    private static final String TABLE = "minecraft:crafting_table";
    private static final String FURNACE = "minecraft:furnace";
    private static final String TABLE_BLOCK = "crafting_table";
    private static final String MINECRAFT = "minecraft:";
    private static final int CHEST_PLANKS = 20;
    private static final int FURNACE_STONE = 8;

    private StagePlans() {}

    public static Optional<TaskNode> plan(BoardCase boardCase, int level, TicketTarget target) {
        if (boardCase == BoardCase.BUILD_PLACED) {
            return Optional.of(placed(target));
        }
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
            case BUILD_FENCE, LIGHTING -> Optional.of(PlanSteps.placeAll(target, work.blocks(), work.builds()));
            case STOCK_CHESTS -> Optional.of(chests(target, site, work.builds()));
            case CRAFT_FURNACE -> Optional.of(station(target, site.workYard(), work.builds()));
            case CRAFT_BREWING_STAND -> Optional.of(brewStand(site.workYard()));
            case BUILD_HOUSE -> level == 1 ? Optional.of(hut(target, site)) : Optional.empty();
            case BUILD_STREETS -> Optional.of(PlanSteps.buildsOnly(target, work.builds()));
            case DISMANTLE_SHELTER -> Optional.of(demolish(work.blocks()));
            default -> Optional.empty();
        };
    }

    private static TaskNode placed(TicketTarget target) {
        String scheme = target.schemeFile();
        if (scheme == null) {
            throw new IllegalArgumentException("Постройка по месту без схемы: BUILD_PLACED берёт файл схемы из "
                    + "цели задания, иначе дух не знает, что ставить в свой слот");
        }
        BlockCoord origin = target.target();
        List<TaskNode> steps = new ArrayList<>();
        steps.add(PlanSteps.gotoPoint(origin));
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
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("file", scheme);
        params.put("x", origin.x());
        params.put("y", origin.y());
        params.put("z", origin.z());
        if (target.wood() != null) {
            params.put(BuildSchematicParams.WOOD, target.wood());
        }
        steps.add(new ScriptCall("build_schematic", Map.copyOf(params)));
        return new SequenceNode(steps);
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

    private static TaskNode brewStand(BlockCoord yard) {
        List<TaskNode> steps = new ArrayList<>();
        steps.add(PlanSteps.gotoPoint(yard));
        steps.add(PlanSteps.withdraw("minecraft:blaze_rod", 1));
        steps.add(PlanSteps.withdraw("minecraft:cobblestone", 3));
        steps.add(new ScriptCall(
                "craft",
                Map.of(
                        "item",
                        "minecraft:brewing_stand",
                        "count",
                        1,
                        "radius",
                        CatalogLimits.STATION_RADIUS_DEFAULT,
                        "place",
                        true)));
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
        blocks.stream()
                .sorted(Comparator.comparingInt((PlannedBlock block) -> SchematicBlocks.isBed(block.block()) ? 0 : 1)
                        .thenComparingInt((PlannedBlock block) -> block.at().y())
                        .thenComparing(PlannedBlock::at))
                .forEach(block -> steps.add(PlanSteps.clearStrict(block.at())));
        return new SequenceNode(steps);
    }
}
