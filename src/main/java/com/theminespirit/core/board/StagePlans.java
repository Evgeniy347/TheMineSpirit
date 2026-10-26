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
    public static final int HOUSE_PARTS = 2;
    public static final String CHEST_LEFT = "minecraft:chest[facing=north,type=left]";
    public static final String CHEST_RIGHT = "minecraft:chest[facing=north,type=right]";
    public static final String TABLE = "minecraft:crafting_table";
    public static final String FURNACE = "minecraft:furnace[facing=north]";
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
            case STOCK_CHESTS -> Optional.of(chests(target, site, work.blocks()));
            case CRAFT_FURNACE -> Optional.of(station(target, site.workYard(), work.blocks()));
            case BUILD_HOUSE -> level == 1 ? Optional.of(hut(target, site, work.builds())) : Optional.empty();
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

    public static List<PlannedBlock> yardBlocks(StageSite site, int slot) {
        List<BlockCoord> pair = site.chestSlots().get(Math.max(1, slot) - 1);
        List<PlannedBlock> blocks = new ArrayList<>();
        blocks.add(new PlannedBlock(pair.get(0), CHEST_LEFT));
        blocks.add(new PlannedBlock(pair.get(1), CHEST_RIGHT));
        if (slot <= 1) {
            blocks.add(new PlannedBlock(site.craftingTable(), TABLE));
        }
        return List.copyOf(blocks);
    }

    private static TaskNode hut(TicketTarget target, StageSite site, List<SchemeBuild> palisade) {
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
        if (target.count() != 1) {
            steps.add(PlanSteps.placeAll(target, List.of(), palisade));
        }
        return new SequenceNode(steps);
    }

    private static TaskNode chests(TicketTarget target, StageSite site, List<PlannedBlock> blocks) {
        List<TaskNode> steps = new ArrayList<>();
        steps.add(PlanSteps.gotoPoint(site.workYard()));
        String wood = target.wood() == null ? WoodSpecies.OAK : target.wood();
        steps.add(PlanSteps.withdraw(MINECRAFT + wood + "_planks", CHEST_PLANKS));
        if (blocks.size() > 2) {
            steps.add(PlanSteps.craft(TABLE, 1));
            steps.add(PlanSteps.place(blocks.get(2), target));
        }
        steps.add(PlanSteps.craft("minecraft:chest", 2));
        steps.add(PlanSteps.place(blocks.get(0), target));
        steps.add(PlanSteps.place(blocks.get(1), target));
        return new SequenceNode(steps);
    }

    private static TaskNode station(TicketTarget target, BlockCoord yard, List<PlannedBlock> blocks) {
        List<TaskNode> steps = new ArrayList<>();
        steps.add(PlanSteps.gotoPoint(yard));
        steps.add(PlanSteps.withdraw("minecraft:cobblestone", FURNACE_STONE));
        for (PlannedBlock block : blocks) {
            steps.add(PlanSteps.craft(PlanSteps.bare(block.block()), 1));
            steps.add(PlanSteps.place(block, target));
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
