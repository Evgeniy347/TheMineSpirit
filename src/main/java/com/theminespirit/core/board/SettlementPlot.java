package com.theminespirit.core.board;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

public record SettlementPlot(BlockCoord anchor, int min, int max, int columns, String wood) {
    public static final String JOURNAL_ID = "plot";
    public static final int DRIFT = SettlementLayout.HALF / 2;

    public SettlementPlot {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него раскладка и выравнивание");
        WoodSpecies.check(wood);
        if (min > anchor.y() || max < anchor.y() || columns < 1) {
            throw new IllegalArgumentException("Высота участка " + anchor.y() + " вне [" + min + ", " + max
                    + "] или колонок " + columns + ": медиана лежит между минимумом и максимумом");
        }
    }

    public static BlockCoord center(Collection<BlockCoord> homes) {
        Objects.requireNonNull(homes, "Дома духов не могут быть null: без них центра поселения нет");
        if (homes.isEmpty()) {
            throw new IllegalArgumentException("Нет ни одного дома духа: центр поселения задаёт дом");
        }
        return new BlockCoord(
                median(homes.stream().mapToInt(BlockCoord::x).toArray()),
                median(homes.stream().mapToInt(BlockCoord::y).toArray()),
                median(homes.stream().mapToInt(BlockCoord::z).toArray()));
    }

    public static SettlementPlot survey(BlockCoord center, int half, IntBinaryOperator feet, String wood) {
        Objects.requireNonNull(center, "Центр участка не может быть null: обмер идёт вокруг него");
        Objects.requireNonNull(feet, "Высоты колонок не могут быть null: без них высоты участка нет");
        int side = 2 * half + 1;
        int[] heights = new int[side * side];
        int index = 0;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                heights[index++] = feet.applyAsInt(center.x() + dx, center.z() + dz);
            }
        }
        Arrays.sort(heights);
        int level = heights[heights.length / 2];
        return new SettlementPlot(
                new BlockCoord(center.x(), level, center.z()),
                heights[0],
                heights[heights.length - 1],
                heights.length,
                wood);
    }

    public boolean contains(BlockCoord place) {
        Objects.requireNonNull(place, "Место не может быть null: проверяется, лежит ли оно на участке");
        return Math.abs(place.x() - anchor.x()) <= SettlementLayout.PLOT
                && Math.abs(place.z() - anchor.z()) <= SettlementLayout.PLOT;
    }

    public boolean covers(BlockCoord center) {
        Objects.requireNonNull(center, "Центр домов не может быть null: проверяется, тот ли это участок");
        return Math.abs(center.x() - anchor.x()) <= DRIFT && Math.abs(center.z() - anchor.z()) <= DRIFT;
    }

    public SettlementPlot withWood(String species) {
        return new SettlementPlot(anchor, min, max, columns, species);
    }

    public BoardLogEntry journalEntry(long tick, String configHash) {
        return BoardLog.note(
                JOURNAL_ID,
                BoardCase.CLEAR_GROUNDS,
                tick,
                List.of(
                        "x=" + anchor.x(),
                        "y=" + anchor.y(),
                        "z=" + anchor.z(),
                        "min=" + min,
                        "max=" + max,
                        "columns=" + columns,
                        "wood=" + wood),
                null,
                configHash);
    }

    private static int median(int[] values) {
        Arrays.sort(values);
        return values[(values.length - 1) / 2];
    }
}
