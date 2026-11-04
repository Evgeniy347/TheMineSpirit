package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class StrayGroups {
    public record Cell(BlockCoord at, String blockId) {
        public Cell {
            Objects.requireNonNull(at, "Клетка лишнего блока не может быть null: по ней её сносят");
            Objects.requireNonNull(blockId, "Блок клетки не может быть null: по нему выбирают инструмент");
        }

        String path() {
            return FloatingBlocks.pathOf(blockId);
        }
    }

    public record Group(
            List<Cell> cells,
            String type,
            BlockCoord position,
            @Nullable String builder,
            Map<String, Integer> blocks) {
        public Group {
            cells = List.copyOf(Objects.requireNonNull(cells, "Клетки куска не могут быть null"));
            Objects.requireNonNull(type, "Тип куска не может быть null: без него не выбрать снос");
            Objects.requireNonNull(position, "Начало куска не может быть null: к нему идут духи");
            blocks = Map.copyOf(Objects.requireNonNull(blocks, "Состав куска не может быть null"));
            if (cells.isEmpty()) {
                throw new IllegalArgumentException("Кусок лишнего без клеток: сносить нечего");
            }
        }

        public int size() {
            return cells.size();
        }
    }

    private static final Comparator<Cell> ORDER = Comparator.comparingInt(
                    (Cell cell) -> cell.at().y())
            .thenComparingInt(cell -> cell.at().x())
            .thenComparingInt(cell -> cell.at().z());

    private StrayGroups() {}

    public static List<Group> of(List<Cell> cells, Map<BlockCoord, String> builders) {
        Objects.requireNonNull(cells, "Клетки лишнего не могут быть null: пусто — пустой список");
        Objects.requireNonNull(builders, "Строители клеток не могут быть null: неизвестный — пустая карта");
        Set<BlockCoord> left = new LinkedHashSet<>();
        Map<BlockCoord, Cell> byAt = new LinkedHashMap<>();
        for (Cell cell : cells) {
            left.add(cell.at());
            byAt.putIfAbsent(cell.at(), cell);
        }
        List<Group> groups = new ArrayList<>();
        for (Cell seed : cells.stream().sorted(ORDER).toList()) {
            if (!left.remove(seed.at())) {
                continue;
            }
            List<Cell> found = connected(seed, left, byAt);
            found.sort(ORDER);
            groups.add(group(found, builders));
        }
        groups.sort(Comparator.comparing(Group::position, StrayGroups::compare));
        return List.copyOf(groups);
    }

    private static List<Cell> connected(Cell seed, Set<BlockCoord> left, Map<BlockCoord, Cell> byAt) {
        List<Cell> found = new ArrayList<>();
        Deque<Cell> queue = new ArrayDeque<>();
        found.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            BlockCoord at = queue.poll().at();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
                            continue;
                        }
                        BlockCoord next = new BlockCoord(at.x() + dx, at.y() + dy, at.z() + dz);
                        if (!left.remove(next)) {
                            continue;
                        }
                        Cell cell = byAt.getOrDefault(next, new Cell(next, "minecraft:air"));
                        found.add(cell);
                        queue.add(cell);
                    }
                }
            }
        }
        return found;
    }

    private static Group group(List<Cell> cells, Map<BlockCoord, String> builders) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Cell cell : cells) {
            counts.merge(cell.path(), 1, Integer::sum);
        }
        String type = counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .orElseThrow()
                .getKey();
        String builder = null;
        for (Cell cell : cells) {
            String known = builders.get(cell.at());
            if (known != null && !known.isBlank()) {
                builder = known;
                break;
            }
        }
        return new Group(cells, type, cells.get(0).at(), builder, counts);
    }

    private static int compare(BlockCoord first, BlockCoord second) {
        return Comparator.comparingInt(BlockCoord::y)
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z)
                .compare(first, second);
    }
}
