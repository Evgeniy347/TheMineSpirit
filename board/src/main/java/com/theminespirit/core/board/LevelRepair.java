package com.theminespirit.core.board;

import com.theminespirit.core.building.FillArea;
import com.theminespirit.core.building.FillOrder;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class LevelRepair {
    private LevelRepair() {}

    public static List<BlockCoord> withinShift(List<BlockCoord> raised) {
        Map<String, Integer> bottoms = new HashMap<>();
        raised.forEach(at -> bottoms.merge(at.x() + "," + at.z(), at.y(), Math::min));
        return raised.stream()
                .filter(at -> at.y() < bottoms.getOrDefault(at.x() + "," + at.z(), at.y()) + PlotLeveling.MAX_SHIFT)
                .toList();
    }

    public static PlotLeveling leveling(List<BlockCoord> holes, List<BlockCoord> extras) {
        Objects.requireNonNull(holes, "Дыры участка не могут быть null: нет дыр — пустой список");
        Objects.requireNonNull(extras, "Лишние блоки не могут быть null: нет лишних — пустой список");
        return new PlotLeveling(columns(extras, true), columns(holes, false), PlotLeveling.DEFAULT_BLOCK);
    }

    private static List<PlotLeveling.Box> splitColumns(List<PlotLeveling.Box> boxes) {
        Map<String, int[]> grouped = new LinkedHashMap<>();
        for (PlotLeveling.Box box : boxes) {
            for (int x = box.x1(); x <= box.x2(); x++) {
                int columnX = x;
                for (int z = box.z1(); z <= box.z2(); z++) {
                    int columnZ = z;
                    String key = columnX + "," + columnZ;
                    int[] range =
                            grouped.computeIfAbsent(key, ignored -> new int[] {columnX, columnZ, box.y1(), box.y2()});
                    range[2] = Math.min(range[2], box.y1());
                    range[3] = Math.max(range[3], box.y2());
                }
            }
        }
        return columns(grouped, true);
    }

    private static List<PlotLeveling.Box> columns(List<BlockCoord> cells, boolean topDown) {
        Map<String, int[]> grouped = new LinkedHashMap<>();
        for (BlockCoord cell : cells) {
            String key = cell.x() + "," + cell.z();
            int[] range = grouped.computeIfAbsent(key, ignored -> new int[] {cell.x(), cell.z(), cell.y(), cell.y()});
            range[2] = Math.min(range[2], cell.y());
            range[3] = Math.max(range[3], cell.y());
        }
        return columns(grouped, topDown);
    }

    private static List<PlotLeveling.Box> columns(Map<String, int[]> grouped, boolean topDown) {
        List<PlotLeveling.Box> boxes = new ArrayList<>(grouped.size());
        grouped.values()
                .forEach(range ->
                        boxes.add(new PlotLeveling.Box(range[0], range[2], range[1], range[0], range[3], range[1])));
        Comparator<PlotLeveling.Box> order = topDown
                ? Comparator.comparingInt(PlotLeveling.Box::y2)
                        .reversed()
                        .thenComparingInt(PlotLeveling.Box::x1)
                        .thenComparingInt(PlotLeveling.Box::z1)
                : Comparator.comparingInt(PlotLeveling.Box::y1)
                        .thenComparingInt(PlotLeveling.Box::x1)
                        .thenComparingInt(PlotLeveling.Box::z1);
        boxes.sort(order);
        return List.copyOf(boxes);
    }

    public record Progress(int done, int total) {}

    public static Progress progress(PlotLeveling leveling, Predicate<BlockCoord> empty) {
        Objects.requireNonNull(leveling, "План выравнивания не может быть null: без него нечего проверять");
        Objects.requireNonNull(empty, "Проверка клетки не может быть null: по ней видно, срезано ли лишнее");
        int total = 0;
        int done = 0;
        for (PlotLeveling.Box box : leveling.cut()) {
            total++;
            if (corners(box).stream().allMatch(empty)) {
                done++;
            }
        }
        for (PlotLeveling.Box box : leveling.fill()) {
            total++;
            if (corners(box).stream().noneMatch(empty)) {
                done++;
            }
        }
        return new Progress(done, total);
    }

    private static List<BlockCoord> corners(PlotLeveling.Box box) {
        Set<BlockCoord> points = new LinkedHashSet<>();
        for (int x : new int[] {box.x1(), box.x2()}) {
            for (int y : new int[] {box.y1(), box.y2()}) {
                for (int z : new int[] {box.z1(), box.z2()}) {
                    points.add(new BlockCoord(x, y, z));
                }
            }
        }
        return List.copyOf(points);
    }

    public static TaskNode plan(TicketTarget target) {
        PlotLeveling leveling = Objects.requireNonNull(
                target.leveling(), "Восстановление выравнивания без плана: срезать и подсыпать нечего");
        List<TaskNode> steps = new ArrayList<>();
        splitColumns(leveling.cut())
                .forEach(box -> steps.add(retried(new ScriptCall("clear_area", params(box, Map.of())))));
        if (!leveling.cut().isEmpty()) {
            steps.add(retried(new ScriptCall(
                    "collect_drops", Map.of("radius", CatalogLimits.DROP_RADIUS_MAX, "filter", dropFilter(leveling)))));
        }
        List<FillOrder.Box> layers = FillOrder.layers(leveling.fill());
        int batch = Math.min(LevelingRules.current().batch(), CatalogLimits.COUNT_MAX);
        for (List<FillOrder.Box> group : FillOrder.batches(layers, batch)) {
            int volume = FillOrder.volume(group);
            BlockCoord source = GroundsPlan.dirtSource(target, 1);
            steps.add(retried(new ScriptCall(
                    "acquire_at",
                    Map.of(
                            "item",
                            "minecraft:" + PlotLeveling.DEFAULT_BLOCK,
                            "count",
                            Math.min(volume, CatalogLimits.COUNT_MAX),
                            "x",
                            source.x(),
                            "y",
                            source.y(),
                            "z",
                            source.z()))));
            group.forEach(box -> steps.add(retried(
                    new ScriptCall("fill", params(box, Map.of("block", PlotLeveling.DEFAULT_BLOCK))))));
        }
        return new SequenceNode(steps);
    }

    private static List<String> dropFilter(PlotLeveling leveling) {
        String dirt = "minecraft:" + PlotLeveling.DEFAULT_BLOCK;
        String raw = leveling.block();
        String block = raw.contains(":") ? raw : "minecraft:" + raw;
        return block.equals(dirt) ? List.of(dirt) : List.of(dirt, block);
    }

    private static TaskNode retried(ScriptCall call) {
        return new FallbackNode(List.of(call, new ScriptCall("wait", Map.of("ticks", CatalogLimits.WAIT_TICKS_MIN))));
    }

    private static Map<String, Object> params(FillArea box, Map<String, Object> extra) {
        Map<String, Object> params = new LinkedHashMap<>(extra);
        params.put("x1", box.x1());
        params.put("y1", box.y1());
        params.put("z1", box.z1());
        params.put("x2", box.x2());
        params.put("y2", box.y2());
        params.put("z2", box.z2());
        return Map.copyOf(params);
    }
}
