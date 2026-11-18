package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FenceRepairs {
    public static final String TICKET_ID = "repair_fence:all";
    public static final String CAUSE = "fence:all";

    private FenceRepairs() {}

    public record Batch(List<BlockCoord> cells, List<String> blocks) {
        public Batch {
            cells = List.copyOf(
                    Objects.requireNonNull(cells, "Клетки ограды не могут быть null: без них задание в пустоту"));
            blocks = List.copyOf(
                    Objects.requireNonNull(blocks, "Блоки ограды не могут быть null: каждый пролом чем-то чинят"));
            if (cells.isEmpty()) {
                throw new IllegalArgumentException("Клетки ограды пусты: задание починки без проломов не ставят");
            }
            if (cells.size() != blocks.size()) {
                throw new IllegalArgumentException("Клеток ограды " + cells.size() + ", блоков " + blocks.size()
                        + ": каждому пролому нужен свой блок, проверьте реестр проломов");
            }
        }

        public BlockCoord first() {
            return cells.get(0);
        }
    }

    public static Batch of(Map<BlockCoord, String> breaks) {
        Objects.requireNonNull(breaks, "Проломы ограды не могут быть null: без них задание нечего чинить");
        if (breaks.isEmpty()) {
            throw new IllegalArgumentException("Проломов ограды нет: одну задачу починки ставят только по реестру");
        }
        List<Map.Entry<BlockCoord, String>> sorted = new ArrayList<>(breaks.entrySet());
        sorted.sort(Comparator.comparingInt(
                        (Map.Entry<BlockCoord, String> entry) -> entry.getKey().x())
                .thenComparingInt(entry -> entry.getKey().y())
                .thenComparingInt(entry -> entry.getKey().z()));
        List<BlockCoord> cells = new ArrayList<>(sorted.size());
        List<String> blocks = new ArrayList<>(sorted.size());
        for (Map.Entry<BlockCoord, String> entry : sorted) {
            cells.add(entry.getKey());
            blocks.add(entry.getValue());
        }
        return new Batch(cells, blocks);
    }

    public static Map<String, Integer> scale(Map<String, Integer> perCell, int cells) {
        Objects.requireNonNull(perCell, "Расход на клетку не может быть null: без него не посчитать ресурсы");
        if (cells < 1) {
            throw new IllegalArgumentException("Клеток ограды " + cells + ": расход считают хотя бы на одну");
        }
        Map<String, Integer> scaled = new LinkedHashMap<>();
        perCell.forEach((item, count) -> scaled.put(item, count * cells));
        return Map.copyOf(scaled);
    }

    public static boolean coveredBy(TicketTarget target, Map<BlockCoord, String> breaks) {
        Objects.requireNonNull(target, "Цель задания не может быть null: покрытие считают по её клеткам");
        Objects.requireNonNull(breaks, "Проломы ограды не могут быть null: без них покрытие не проверить");
        if (target.fenceCells().isEmpty()) {
            return false;
        }
        Map<BlockCoord, String> known = new LinkedHashMap<>();
        for (int index = 0; index < target.fenceCells().size(); index++) {
            known.put(target.fenceCells().get(index), target.fenceCellBlocks().get(index));
        }
        for (Map.Entry<BlockCoord, String> entry : breaks.entrySet()) {
            if (!Objects.equals(known.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
