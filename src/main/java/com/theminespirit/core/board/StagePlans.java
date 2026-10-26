package com.theminespirit.core.board;

import com.theminespirit.core.building.PresetLibrary;
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
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StagePlans {
    public static final String FENCE = "minecraft:oak_fence";
    public static final String GATE_NORTH = "minecraft:oak_fence_gate[facing=north]";
    public static final String GATE_EAST = "minecraft:oak_fence_gate[facing=east]";
    public static final String TORCH = "minecraft:torch";
    public static final String AIR = "minecraft:air";
    public static final int HOUSE_PARTS = 2;
    public static final String CHEST_LEFT = "minecraft:chest[facing=north,type=left]";
    public static final String CHEST_RIGHT = "minecraft:chest[facing=north,type=right]";
    public static final String TABLE = "minecraft:crafting_table";
    public static final String FURNACE = "minecraft:furnace[facing=north]";
    private static final String MINECRAFT = "minecraft:";
    private static final String WAIT = "wait";
    private static final String TICKS = "ticks";
    private static final int PALISADE_WIDTH = 7;
    private static final int PALISADE_DEPTH = 3;
    private static final int CHEST_PLANKS = 20;
    private static final int FURNACE_STONE = 8;
    private static final int PLANKS_PER_LOG = 4;
    private static final int PLANKS_PER_GATE = 4;
    private static final int FENCES_PER_CRAFT = 3;
    private static final int PLANKS_PER_FENCE_CRAFT = 5;
    private static final int YARD_REACH = 8;

    private StagePlans() {}

    public static Optional<TaskNode> plan(BoardCase boardCase, int level, TicketTarget target) {
        StageWork work = target.work();
        if (work == null) {
            return Optional.empty();
        }
        StageSite site = work.site();
        if (boardCase == BoardCase.REPAIR_BUILDING && !work.repairs().isEmpty()) {
            BoardCase repaired = BoardCase.valueOf(work.repairs());
            return Optional.of(TicketPlans.plan(repaired, 1, target.withWork(new StageWork(site, work.blocks()))));
        }
        return switch (boardCase) {
            case BUILD_FENCE, LIGHTING -> Optional.of(placeAll(target, work.blocks()));
            case STOCK_CHESTS -> Optional.of(chests(target, site, work.blocks()));
            case CRAFT_FURNACE -> Optional.of(station(target, site.workYard(), work.blocks()));
            case BUILD_HOUSE -> level == 1 ? Optional.of(hut(target, site, work.blocks())) : Optional.empty();
            case DISMANTLE_SHELTER -> Optional.of(demolish(work.blocks()));
            default -> Optional.empty();
        };
    }

    public static List<PlannedBlock> palisade(StageSite site) {
        BlockCoord origin = site.palisadeOrigin();
        List<PlannedBlock> blocks = new ArrayList<>();
        for (int dx = 0; dx < PALISADE_WIDTH; dx++) {
            String block = dx == StageSite.PALISADE_GATE ? GATE_NORTH : FENCE;
            blocks.add(new PlannedBlock(new BlockCoord(origin.x() + dx, origin.y(), origin.z()), block));
        }
        for (int dz = 1; dz < PALISADE_DEPTH; dz++) {
            blocks.add(new PlannedBlock(new BlockCoord(origin.x(), origin.y(), origin.z() + dz), FENCE));
            blocks.add(new PlannedBlock(
                    new BlockCoord(origin.x() + PALISADE_WIDTH - 1, origin.y(), origin.z() + dz), FENCE));
        }
        return List.copyOf(blocks);
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

    private static TaskNode hut(TicketTarget target, StageSite site, List<PlannedBlock> palisade) {
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
        steps.add(gotoPoint(site.workYard()));
        DataTables.INSTANCE
                .presets()
                .current()
                .byId(scheme)
                .ifPresent(preset -> PresetLibrary.countMaterials(preset.schematic())
                        .forEach((item, count) -> {
                            String id = target.wood() == null ? item : WoodSpecies.swap(item, target.wood());
                            steps.add(acquire(
                                    id.contains(":") ? id : MINECRAFT + id, Math.min(count, CatalogLimits.COUNT_MAX)));
                        }));
        steps.add(new ScriptCall("build_schematic", Map.copyOf(params)));
        if (target.count() != 1) {
            steps.add(placeAll(target, palisade));
        }
        return new SequenceNode(steps);
    }

    private static TaskNode chests(TicketTarget target, StageSite site, List<PlannedBlock> blocks) {
        List<TaskNode> steps = new ArrayList<>();
        steps.add(gotoPoint(site.workYard()));
        String wood = target.wood() == null ? WoodSpecies.OAK : target.wood();
        steps.add(withdraw(MINECRAFT + wood + "_planks", CHEST_PLANKS));
        if (blocks.size() > 2) {
            steps.add(craft(TABLE, 1));
            steps.add(place(blocks.get(2), target));
        }
        steps.add(craft("minecraft:chest", 2));
        steps.add(place(blocks.get(0), target));
        steps.add(place(blocks.get(1), target));
        return new SequenceNode(steps);
    }

    private static TaskNode station(TicketTarget target, BlockCoord yard, List<PlannedBlock> blocks) {
        List<TaskNode> steps = new ArrayList<>();
        steps.add(gotoPoint(yard));
        steps.add(withdraw("minecraft:cobblestone", FURNACE_STONE));
        for (PlannedBlock block : blocks) {
            steps.add(craft(bare(block.block()), 1));
            steps.add(place(block, target));
        }
        return new SequenceNode(steps);
    }

    private static TaskNode placeAll(TicketTarget target, List<PlannedBlock> blocks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlannedBlock block : blocks) {
            if (!AIR.equals(block.block())) {
                counts.merge(item(block, target), 1, Integer::sum);
            }
        }
        List<TaskNode> steps = new ArrayList<>();
        StageWork work = target.work();
        if (work != null) {
            steps.add(gotoPoint(work.site().workYard()));
            counts.forEach((item, count) -> steps.add(withdraw(item, Math.min(count, CatalogLimits.COUNT_MAX))));
            int logs = (woodPlanks(counts) + PLANKS_PER_LOG - 1) / PLANKS_PER_LOG;
            String wood = target.wood() == null ? WoodSpecies.OAK : target.wood();
            if (logs > 0) {
                steps.add(withdraw(MINECRAFT + wood + "_log", Math.min(logs, CatalogLimits.COUNT_MAX)));
            }
        }
        if (!blocks.isEmpty()) {
            steps.add(gotoPoint(blocks.get(0).at()));
        }
        counts.forEach((item, count) -> steps.add(acquire(item, Math.min(count, CatalogLimits.COUNT_MAX))));
        for (PlannedBlock block : blocks) {
            steps.add(place(block, target));
        }
        return new SequenceNode(steps);
    }

    private static TaskNode demolish(List<PlannedBlock> blocks) {
        List<TaskNode> steps = new ArrayList<>();
        if (!blocks.isEmpty()) {
            steps.add(gotoPoint(blocks.get(0).at()));
        }
        blocks.forEach(block -> steps.add(clear(block.at(), true)));
        return new SequenceNode(steps);
    }

    private static TaskNode clear(BlockCoord at, boolean demolish) {
        return new FallbackNode(List.of(
                new ScriptCall(
                        "clear_area",
                        Map.of(
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
                                demolish)),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    private static TaskNode place(PlannedBlock block, TicketTarget target) {
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

    private static String item(PlannedBlock block, TicketTarget target) {
        String id = bare(block.block());
        return target.wood() == null ? id : WoodSpecies.swap(id, target.wood());
    }

    private static String bare(String block) {
        int state = block.indexOf('[');
        return state < 0 ? block : block.substring(0, state);
    }

    private static int woodPlanks(Map<String, Integer> counts) {
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

    private static TaskNode withdraw(String item, int count) {
        return new FallbackNode(List.of(
                new ScriptCall(
                        "withdraw", Map.of("container", "nearest", "item", item, "count", count, "radius", YARD_REACH)),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    private static ScriptCall craft(String item, int count) {
        return new ScriptCall(
                "craft", Map.of("item", item, "count", count, "radius", CatalogLimits.STATION_RADIUS_DEFAULT));
    }

    private static TaskNode acquire(String item, int count) {
        return new FallbackNode(List.of(
                new ScriptCall("acquire", Map.of("item", item, "count", count)),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall("goto", Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }
}
