package com.theminespirit.core.town;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntBinaryOperator;

public record SettlementPlot(BlockCoord anchor, int min, int max, int columns, String wood) {
    public static final String JOURNAL_ID = "plot";
    public static final int DRIFT = 5;

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

    public static SettlementPlot survey(
            BlockCoord center, ColumnMask area, IntBinaryOperator feet, String wood, OptionalInt chosen) {
        Objects.requireNonNull(center, "Центр участка не может быть null: от него якорь плана");
        Objects.requireNonNull(area, "Площадки не могут быть null: обмер идёт по ним");
        Objects.requireNonNull(feet, "Высоты колонок не могут быть null: без них высоты участка нет");
        int[] heights = area.cells().stream()
                .mapToInt(cell -> feet.applyAsInt(cell.x(), cell.z()))
                .sorted()
                .toArray();
        if (heights.length == 0) {
            throw new IllegalArgumentException("Площадок этапа нет: обмерять нечего, план без построек этапа");
        }
        int level = chosen.orElse(heights[heights.length / 2]);
        return new SettlementPlot(
                new BlockCoord(center.x(), level, center.z()),
                Math.min(heights[0], level),
                Math.max(heights[heights.length - 1], level),
                heights.length,
                wood);
    }

    public boolean covers(BlockCoord center) {
        Objects.requireNonNull(center, "Центр домов не может быть null: проверяется, тот ли это участок");
        return Math.abs(center.x() - anchor.x()) <= DRIFT && Math.abs(center.z() - anchor.z()) <= DRIFT;
    }

    public SettlementPlot withWood(String species) {
        return new SettlementPlot(anchor, min, max, columns, species);
    }

    private static int median(int[] values) {
        Arrays.sort(values);
        return values[(values.length - 1) / 2];
    }
}
