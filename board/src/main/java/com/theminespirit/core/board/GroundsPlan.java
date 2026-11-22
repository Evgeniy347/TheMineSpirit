package com.theminespirit.core.board;

import com.theminespirit.core.base.NamespacedId;
import com.theminespirit.core.building.EarthCover;
import com.theminespirit.core.building.FillOrder;
import com.theminespirit.core.building.api.FillArea;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.core.town.StageWork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GroundsPlan {
    public static final int CLEAR_PARTS = 1;
    private static final int DIRT_SOURCE_GAP = 4;
    private static final String CLEAR_AREA = "clear_area";

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
            return 1
                    + band(x - x1, x2 - x1 + 1)
                    + BoardConfig.current().plans().clearGrid() * band(z - z1, z2 - z1 + 1);
        }

        private static int band(int offset, int size) {
            int width = Math.max(
                    1,
                    (size + BoardConfig.current().plans().clearGrid() - 1)
                            / BoardConfig.current().plans().clearGrid());
            return Math.max(0, Math.min(BoardConfig.current().plans().clearGrid() - 1, offset / width));
        }
    }

    public static Frame frame(TicketTarget target) {
        StageWork work = target.work();
        return work == null
                ? Frame.around(target.target(), BoardConfig.current().plans().freeRadius())
                : Frame.of(work.site().level(), work.site().anchor().y());
    }

    public static BlockCoord dirtSource(TicketTarget target, int part) {
        StageWork work = target.work();
        Frame outer = work == null
                ? Frame.around(target.target(), BoardConfig.current().plans().freeDirtReach())
                : Frame.of(work.site().enclosure(), work.site().anchor().y());
        int col = (part - 1) % BoardConfig.current().plans().clearGrid() - 1;
        int row = (part - 1) / BoardConfig.current().plans().clearGrid() - 1;
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
        List<TaskNode> steps = new ArrayList<>();
        PlotLeveling leveling = target.leveling() == null ? PlotLeveling.FLAT : target.leveling();
        List<PlotLeveling.Box> cuts = new ArrayList<>(leveling.cutTopDown());
        cuts.addAll(chunks(target, frame));
        if (!cuts.isEmpty()) {
            steps.add(GatherPlans.shovel());
            steps.add(retried(new ScriptCall(CLEAR_AREA, boxesParams(cuts, Map.of()))));
        }
        if (!cuts.isEmpty()) {
            steps.add(retried(new ScriptCall(
                    "collect_drops", Map.of("radius", CatalogLimits.DROP_RADIUS_MAX, "filter", dropFilter(leveling)))));
        }
        List<PlotLeveling.Box> fills = leveling.fill();
        for (PlotLeveling.Box box : fills) {
            steps.add(retried(new ScriptCall(CLEAR_AREA, boxParams(box, Map.of("keep_ground", true)))));
        }
        BlockCoord source = dirtSource(target, 1);
        List<PlotLeveling.Box> voids = leveling.voids();
        List<FillOrder.Box> pits = new ArrayList<>(FillOrder.layers(fills));
        pits.addAll(FillOrder.layers(voids));
        steps.addAll(fillSteps(leveling, pits, source, frame.y()));
        return new SequenceNode(steps);
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
                            NamespacedId.MINECRAFT + leveling.block(),
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
        int y2 = frame.y() + BoardConfig.current().plans().clearHeight();
        if (work != null) {
            return List.of();
        }
        List<PlotLeveling.Box> chunks = new ArrayList<>();
        int band = (frame.x2() - frame.x1() + 1) / BoardConfig.current().plans().clearGrid();
        for (int row = 0; row < BoardConfig.current().plans().clearGrid(); row++) {
            for (int col = 0; col < BoardConfig.current().plans().clearGrid(); col++) {
                int x = frame.x1() + col * band;
                int z = frame.z1() + row * band;
                int lastX = col == BoardConfig.current().plans().clearGrid() - 1 ? frame.x2() : x + band - 1;
                int lastZ = row == BoardConfig.current().plans().clearGrid() - 1 ? frame.z2() : z + band - 1;
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
                        NamespacedId.MINECRAFT + PlotLeveling.DEFAULT_BLOCK,
                        "count",
                        Math.min(volume, CatalogLimits.COUNT_MAX),
                        "x",
                        source.x(),
                        "y",
                        source.y(),
                        "z",
                        source.z())));
    }

    private static List<String> dropFilter(PlotLeveling leveling) {
        String dirt = NamespacedId.MINECRAFT + PlotLeveling.DEFAULT_BLOCK;
        String block = leveling.block().contains(":") ? leveling.block() : NamespacedId.MINECRAFT + leveling.block();
        if (EarthCover.earth(block)) {
            return EarthCover.ids(dirt);
        }
        return List.of(dirt, block);
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

    private static Map<String, Object> boxesParams(List<PlotLeveling.Box> boxes, Map<String, Object> extra) {
        Map<String, Object> params = new LinkedHashMap<>(extra);
        List<String> list = new ArrayList<>(boxes.size());
        for (PlotLeveling.Box box : boxes) {
            list.add(box.x1() + "," + box.y1() + "," + box.z1() + "," + box.x2() + "," + box.y2() + "," + box.z2());
        }
        params.put("boxes", List.copyOf(list));
        return Map.copyOf(params);
    }

    private static TaskNode retried(ScriptCall call) {
        return new FallbackNode(List.of(call, new ScriptCall("wait", Map.of("ticks", CatalogLimits.WAIT_TICKS_MIN))));
    }

    private static int mid(int a, int b) {
        return a + (b - a) / 2;
    }
}
