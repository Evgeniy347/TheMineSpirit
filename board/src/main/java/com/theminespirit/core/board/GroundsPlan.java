package com.theminespirit.core.board;

import com.theminespirit.core.building.FillArea;
import com.theminespirit.core.building.FillOrder;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.core.town.StageWork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GroundsPlan {
    public static final int CLEAR_GRID = 3;
    public static final int CLEAR_PARTS = CLEAR_GRID * CLEAR_GRID;
    public static final int CLEAR_HEIGHT = 3;
    private static final int FREE_RADIUS = 16;
    private static final int FREE_DIRT_REACH = 13;
    private static final int DIRT_SOURCE_GAP = 4;
    private static final int CUT_SLACK = 12;
    private static final int FILL_SLACK = 40;
    private static final String CLEAR_AREA = "clear_area";
    private static final String MINECRAFT = "minecraft:";

    private GroundsPlan() {}

    public record Frame(int x1, int z1, int x2, int z2, int y) {
        static Frame around(BlockCoord center, int radius) {
            return new Frame(
                    center.x() - radius, center.z() - radius, center.x() + radius, center.z() + radius, center.y());
        }

        static Frame of(ColumnMask mask, int y) {
            return new Frame(
                    mask.minX(), mask.minZ(), mask.minX() + mask.width() - 1, mask.minZ() + mask.depth() - 1, y);
        }

        int part(int x, int z) {
            return 1 + band(x - x1, x2 - x1 + 1) + CLEAR_GRID * band(z - z1, z2 - z1 + 1);
        }

        private static int band(int offset, int size) {
            int width = Math.max(1, (size + CLEAR_GRID - 1) / CLEAR_GRID);
            return Math.max(0, Math.min(CLEAR_GRID - 1, offset / width));
        }
    }

    public static Frame frame(TicketTarget target) {
        StageWork work = target.work();
        return work == null
                ? Frame.around(target.target(), FREE_RADIUS)
                : Frame.of(work.site().level(), work.site().anchor().y());
    }

    public static BlockCoord dirtSource(TicketTarget target, int part) {
        StageWork work = target.work();
        Frame outer = work == null
                ? Frame.around(target.target(), FREE_DIRT_REACH)
                : Frame.of(work.site().enclosure(), work.site().anchor().y());
        int col = (part - 1) % CLEAR_GRID - 1;
        int row = (part - 1) / CLEAR_GRID - 1;
        int x = col < 0
                ? outer.x1() - DIRT_SOURCE_GAP
                : col > 0 ? outer.x2() + DIRT_SOURCE_GAP : mid(outer.x1(), outer.x2());
        int z = row < 0 || (col == 0 && row == 0)
                ? outer.z1() - DIRT_SOURCE_GAP
                : row > 0 ? outer.z2() + DIRT_SOURCE_GAP : mid(outer.z1(), outer.z2());
        return new BlockCoord(x, outer.y(), z);
    }

    public static TaskNode plan(TicketTarget target) {
        Frame frame = frame(target);
        int quarter = target.count();
        boolean single = quarter >= 1 && quarter <= CLEAR_PARTS;
        List<TaskNode> steps = new ArrayList<>();
        PlotLeveling leveling = target.leveling() == null ? PlotLeveling.FLAT : target.leveling();
        List<PlotLeveling.Box> cuts = leveling.cutTopDown();
        List<Integer> cutPart = balancedParts(cuts, frame, CUT_SLACK);
        if ((!single || quarter == 1) && !cuts.isEmpty() && target.work() != null) {
            steps.addAll(chestSteps(target));
        }
        if (!cuts.isEmpty()) {
            steps.add(GatherPlans.shovel());
        }
        for (int index = 0; index < cuts.size(); index++) {
            if (!single || cutPart.get(index) == quarter) {
                steps.add(retried(new ScriptCall(CLEAR_AREA, boxParams(cuts.get(index), Map.of()))));
            }
        }
        if (!cuts.isEmpty()) {
            steps.add(retried(new ScriptCall(
                    "collect_drops",
                    Map.of(
                            "radius",
                            CatalogLimits.DROP_RADIUS_MAX,
                            "filter",
                            List.of(MINECRAFT + PlotLeveling.DEFAULT_BLOCK)))));
        }
        List<PlotLeveling.Box> fills = single ? ofPart(leveling.fill(), frame, quarter) : leveling.fill();
        for (PlotLeveling.Box box : fills) {
            steps.add(retried(new ScriptCall(CLEAR_AREA, boxParams(box, Map.of("keep_ground", true)))));
        }
        BlockCoord source = dirtSource(target, single ? quarter : 1);
        List<PlotLeveling.Box> voids = leveling.voids().stream()
                .filter(box -> !single || frame.part(box.x1(), box.z1()) == quarter)
                .toList();
        List<FillOrder.Box> pits = new ArrayList<>(FillOrder.layers(fills));
        pits.addAll(FillOrder.layers(voids));
        steps.addAll(fillSteps(leveling, pits, source, frame.y()));
        for (PlotLeveling.Box chunk : chunks(target, frame)) {
            if (!single || frame.part(chunk.x1(), chunk.z1()) == quarter) {
                steps.add(new ScriptCall(CLEAR_AREA, boxParams(chunk, Map.of())));
            }
        }
        return new SequenceNode(steps);
    }

    private static List<TaskNode> chestSteps(TicketTarget target) {
        StageWork work = Objects.requireNonNull(
                target.work(), "Временный сундук расчистки ставится по месту этапа: работа не может быть null");
        BlockCoord spot = SiteLevels.chestSpot(work.site());
        return List.of(
                retried(new ScriptCall("acquire", Map.of("item", MINECRAFT + "chest", "count", 1))),
                retried(new ScriptCall(
                        "place_block",
                        Map.of(
                                "block", MINECRAFT + "chest",
                                "x", spot.x(),
                                "y", spot.y(),
                                "z", spot.z()))));
    }

    private static List<TaskNode> fillSteps(PlotLeveling leveling, List<FillOrder.Box> pits, BlockCoord source, int y) {
        List<TaskNode> steps = new ArrayList<>();
        int batch = Math.min(LevelingRules.current().batch(), CatalogLimits.COUNT_MAX);
        if (PlotLeveling.DEFAULT_BLOCK.equals(leveling.block())) {
            for (List<FillOrder.Box> group : FillOrder.batches(pits, batch)) {
                steps.add(dirtFor(FillOrder.volume(group), source));
                group.forEach(layer -> steps.add(fillLayer(layer, PlotLeveling.DEFAULT_BLOCK)));
            }
            return steps;
        }
        List<FillOrder.Box> top = new ArrayList<>();
        List<FillOrder.Box> deep = new ArrayList<>();
        for (FillOrder.Box layer : pits) {
            (PlotLeveling.topLayer(layer.y1(), y) ? top : deep).add(layer);
        }
        int deepVolume = FillOrder.volume(deep);
        if (deepVolume > 0) {
            steps.add(new ScriptCall(
                    "acquire",
                    Map.of(
                            "item",
                            MINECRAFT + leveling.block(),
                            "count",
                            Math.min(deepVolume, CatalogLimits.COUNT_MAX))));
        }
        deep.forEach(layer -> steps.add(fillLayer(layer, leveling.block())));
        for (List<FillOrder.Box> group : FillOrder.batches(top, batch)) {
            steps.add(dirtFor(FillOrder.volume(group), source));
            group.forEach(
                    layer -> steps.add(fillLayer(layer, PlotLeveling.fillBlock(layer.y1(), y, leveling.block()))));
        }
        return steps;
    }

    private static List<PlotLeveling.Box> chunks(TicketTarget target, Frame frame) {
        StageWork work = target.work();
        int y1 = frame.y();
        int y2 = frame.y() + CLEAR_HEIGHT;
        if (work != null) {
            return List.of();
        }
        List<PlotLeveling.Box> chunks = new ArrayList<>();
        int band = (frame.x2() - frame.x1() + 1) / CLEAR_GRID;
        for (int row = 0; row < CLEAR_GRID; row++) {
            for (int col = 0; col < CLEAR_GRID; col++) {
                int x = frame.x1() + col * band;
                int z = frame.z1() + row * band;
                int lastX = col == CLEAR_GRID - 1 ? frame.x2() : x + band - 1;
                int lastZ = row == CLEAR_GRID - 1 ? frame.z2() : z + band - 1;
                chunks.add(new PlotLeveling.Box(x, y1, z, lastX, y2, lastZ));
            }
        }
        return chunks;
    }

    private static TaskNode fillLayer(FillOrder.Box layer, String block) {
        return retried(new ScriptCall("fill", boxParams(layer, Map.of("block", block))));
    }

    private static TaskNode dirtFor(int volume, BlockCoord source) {
        return retried(new ScriptCall(
                "acquire_at",
                Map.of(
                        "item",
                        MINECRAFT + PlotLeveling.DEFAULT_BLOCK,
                        "count",
                        Math.min(volume, CatalogLimits.COUNT_MAX),
                        "x",
                        source.x(),
                        "y",
                        source.y(),
                        "z",
                        source.z())));
    }

    private static List<PlotLeveling.Box> ofPart(List<PlotLeveling.Box> boxes, Frame frame, int part) {
        List<Integer> parts = balancedParts(boxes, frame, FILL_SLACK);
        List<PlotLeveling.Box> mine = new ArrayList<>();
        for (int index = 0; index < boxes.size(); index++) {
            if (parts.get(index) == part) {
                mine.add(boxes.get(index));
            }
        }
        return mine;
    }

    static List<Integer> balancedParts(List<PlotLeveling.Box> cuts, Frame frame, int slack) {
        int[] load = new int[CLEAR_PARTS + 1];
        List<Integer> parts = new ArrayList<>(cuts.size());
        for (PlotLeveling.Box box : cuts) {
            int home = frame.part(box.x1(), box.z1());
            int least = Integer.MAX_VALUE;
            for (int part = 1; part <= CLEAR_PARTS; part++) {
                least = Math.min(least, load[part]);
            }
            int chosen = home;
            int bestDistance = Integer.MAX_VALUE;
            for (int part = 1; part <= CLEAR_PARTS; part++) {
                int distance = Math.abs((part - 1) % CLEAR_GRID - (home - 1) % CLEAR_GRID)
                        + Math.abs((part - 1) / CLEAR_GRID - (home - 1) / CLEAR_GRID);
                if (load[part] <= least + slack && distance < bestDistance) {
                    chosen = part;
                    bestDistance = distance;
                }
            }
            load[chosen] += box.volume();
            parts.add(chosen);
        }
        return parts;
    }

    static Map<String, Object> boxParams(FillArea box, Map<String, Object> extra) {
        Map<String, Object> params = new LinkedHashMap<>(extra);
        params.put("x1", box.x1());
        params.put("y1", box.y1());
        params.put("z1", box.z1());
        params.put("x2", box.x2());
        params.put("y2", box.y2());
        params.put("z2", box.z2());
        return Map.copyOf(params);
    }

    private static TaskNode retried(ScriptCall call) {
        return new FallbackNode(List.of(call, new ScriptCall("wait", Map.of("ticks", CatalogLimits.WAIT_TICKS_MIN))));
    }

    private static int mid(int a, int b) {
        return a + (b - a) / 2;
    }
}
