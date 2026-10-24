package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

/**
 * Якорь участка поселения: центр и высота ног участка, считаются один раз до расчистки и хранятся в мире
 * (INC-55, INC-56). Центр — покомпонентная медиана домов духов: не зависит от порядка духов в списке; высота —
 * медиана уровней ног колонок участка {@code SettlementLayout.PLOT}², с минимумом и максимумом для разбора.
 */
public record SettlementPlot(BlockCoord anchor, int min, int max, int columns) {
    public static final String JOURNAL_ID = "plot";
    // Сдвиг центра домов — для участка: дом духа переехал на пару блоков — участок тот же; центр ушёл дальше
    // четверти участка — это другое поселение, его обмеряют заново.
    public static final int DRIFT = SettlementLayout.HALF / 2;

    public SettlementPlot {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него раскладка и выравнивание");
        if (min > anchor.y() || max < anchor.y() || columns < 1) {
            throw new IllegalArgumentException("Высота участка " + anchor.y() + " вне [" + min + ", " + max
                    + "] или колонок " + columns + ": медиана лежит между минимумом и максимумом");
        }
    }

    // Центр участка — медиана домов по x, y и z (нижняя при чётном числе): один и тот же при любом порядке духов.
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

    // Обмер участка {@code half} вокруг центра: высота — медиана уровней ног колонок, плюс минимум и максимум.
    public static SettlementPlot survey(BlockCoord center, int half, IntBinaryOperator feet) {
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
                new BlockCoord(center.x(), level, center.z()), heights[0], heights[heights.length - 1], heights.length);
    }

    public boolean covers(BlockCoord center) {
        Objects.requireNonNull(center, "Центр домов не может быть null: проверяется, тот ли это участок");
        return Math.abs(center.x() - anchor.x()) <= DRIFT && Math.abs(center.z() - anchor.z()) <= DRIFT;
    }

    // Запись журнала доски: кто и когда зафиксировал высоту участка (INC-55).
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
                        "columns=" + columns),
                null,
                configHash);
    }

    private static int median(int[] values) {
        Arrays.sort(values);
        return values[(values.length - 1) / 2];
    }
}
