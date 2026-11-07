package com.theminespirit.core.board.graph;

import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StrayGroups;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

public final class HighStrays {
    public record Stalk(String id, List<BlockCoord> cells) {
        public Stalk {
            Objects.requireNonNull(id, "id столба не может быть null: по нему карточка находит свои блоки");
            cells = List.copyOf(Objects.requireNonNull(cells, "клетки столба не могут быть null"));
            if (cells.isEmpty()) {
                throw new IllegalArgumentException("Столб без клеток: убирать нечего");
            }
        }
    }

    public record Split(List<StrayGroups.Cell> reachable, List<Stalk> high) {
        public Split {
            reachable = List.copyOf(Objects.requireNonNull(reachable, "Досягаемые клетки не могут быть null"));
            high = List.copyOf(Objects.requireNonNull(high, "Высокие столбы не могут быть null"));
        }
    }

    private HighStrays() {}

    public static Split split(List<StrayGroups.Cell> cells, SpiritReach reach, IntBinaryOperator groundFeet) {
        Objects.requireNonNull(cells, "Клетки лишнего не могут быть null: пусто — пустой список");
        Objects.requireNonNull(reach, "Досягаемость не может быть null: без неё высота не делится");
        Objects.requireNonNull(groundFeet, "Высоты стоянок не могут быть null: без них не отличить верх от низа");
        List<StrayGroups.Cell> reachable = new ArrayList<>();
        Map<String, List<BlockCoord>> byColumn = new LinkedHashMap<>();
        for (StrayGroups.Cell cell : cells) {
            if (fromGround(reach, groundFeet, cell.at())) {
                reachable.add(cell);
                continue;
            }
            byColumn.computeIfAbsent(columnKey(cell.at()), key -> new ArrayList<>())
                    .add(cell.at());
        }
        List<Stalk> high = new ArrayList<>();
        for (Map.Entry<String, List<BlockCoord>> column : byColumn.entrySet()) {
            List<BlockCoord> sorted = column.getValue().stream()
                    .sorted(Comparator.comparingInt(BlockCoord::y).reversed())
                    .toList();
            high.add(new Stalk("high:" + column.getKey(), sorted));
        }
        high.sort(Comparator.comparing(Stalk::id));
        return new Split(reachable, high);
    }

    public static boolean fromGround(SpiritReach reach, IntBinaryOperator groundFeet, BlockCoord cell) {
        Objects.requireNonNull(reach, "Досягаемость не может быть null");
        Objects.requireNonNull(groundFeet, "Высоты стоянок не могут быть null");
        Objects.requireNonNull(cell, "Клетка не может быть null");
        for (int dx = -reach.horizontal(); dx <= reach.horizontal(); dx++) {
            for (int dz = -reach.horizontal(); dz <= reach.horizontal(); dz++) {
                int x = cell.x() + dx;
                int z = cell.z() + dz;
                BlockCoord feet = new BlockCoord(x, groundFeet.applyAsInt(x, z), z);
                if (reach.reaches(feet, cell)
                        && !SpiritReach.forbiddenStand(feet, cell)
                        && !ReachPlanner.inBody(feet, cell)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String columnKey(BlockCoord at) {
        return at.x() + "," + at.z();
    }
}
