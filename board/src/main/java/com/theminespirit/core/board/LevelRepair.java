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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class LevelRepair {
    private LevelRepair() {}

    public static PlotLeveling leveling(List<BlockCoord> holes, List<BlockCoord> extras) {
        Objects.requireNonNull(holes, "Дыры участка не могут быть null: нет дыр — пустой список");
        Objects.requireNonNull(extras, "Лишние блоки не могут быть null: нет лишних — пустой список");
        List<PlotLeveling.Box> cut = extras.stream()
                .sorted(Comparator.comparingInt(BlockCoord::y).reversed())
                .map(LevelRepair::box)
                .toList();
        List<PlotLeveling.Box> fill = holes.stream()
                .sorted(Comparator.comparingInt(BlockCoord::y))
                .map(LevelRepair::box)
                .toList();
        return new PlotLeveling(cut, fill, PlotLeveling.DEFAULT_BLOCK);
    }

    public record Progress(int done, int total) {}

    public static Progress progress(PlotLeveling leveling, Predicate<BlockCoord> empty) {
        Objects.requireNonNull(leveling, "План выравнивания не может быть null: без него нечего проверять");
        Objects.requireNonNull(empty, "Проверка клетки не может быть null: по ней видно, срезано ли лишнее");
        int total = 0;
        int done = 0;
        for (PlotLeveling.Box box : leveling.cut()) {
            total++;
            if (empty.test(new BlockCoord(box.x1(), box.y1(), box.z1()))) {
                done++;
            }
        }
        for (PlotLeveling.Box box : leveling.fill()) {
            total++;
            if (!empty.test(new BlockCoord(box.x1(), box.y1(), box.z1()))) {
                done++;
            }
        }
        return new Progress(done, total);
    }

    public static TaskNode plan(TicketTarget target) {
        PlotLeveling leveling = Objects.requireNonNull(
                target.leveling(), "Восстановление выравнивания без плана: срезать и подсыпать нечего");
        List<TaskNode> steps = new ArrayList<>();
        leveling.cut().forEach(box -> steps.add(retried(new ScriptCall("clear_area", params(box, Map.of())))));
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

    private static PlotLeveling.Box box(BlockCoord cell) {
        return new PlotLeveling.Box(cell.x(), cell.y(), cell.z(), cell.x(), cell.y(), cell.z());
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
