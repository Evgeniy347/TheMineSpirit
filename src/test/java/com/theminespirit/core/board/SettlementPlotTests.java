package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettlementPlotTests {
    // Дома духов живого сервера INC-56: у каждого свой SpiritHome.
    private static final List<BlockCoord> HOMES = List.of(
            new BlockCoord(-171, 71, -489),
            new BlockCoord(-171, 71, -487),
            new BlockCoord(-167, 71, -486),
            new BlockCoord(-170, 71, -487));

    @TempDir
    Path directory;

    @Test
    void center_AnyOrderOfSpirits_SamePlot() {
        BlockCoord expected = SettlementPlot.center(HOMES);
        for (int shift = 1; shift < HOMES.size(); shift++) {
            List<BlockCoord> order = new ArrayList<>(HOMES);
            Collections.rotate(order, shift);

            assertEquals(
                    expected,
                    SettlementPlot.center(order),
                    "Центр участка не зависит от порядка духов: «дом первого в списке» двигал ограду сквозь дом "
                            + "(INC-56)");
        }
        assertEquals(
                new BlockCoord(-171, 71, -487),
                expected,
                "Центр — нижняя медиана домов по каждой оси: при смене правила перепроверить раскладку участка");
    }

    @Test
    void survey_SlopedPlot_MedianWithMinMax() {
        SettlementPlot plot = SettlementPlot.survey(new BlockCoord(0, 64, 0), 1, (x, z) -> 64 + x, "oak");

        assertEquals(64, plot.anchor().y(), "Высота участка — медиана ног колонок, а не высота дома (INC-55)");
        assertEquals(63, plot.min(), "Минимум колонок пишется в лог и журнал для разбора");
        assertEquals(65, plot.max(), "Максимум колонок пишется в лог и журнал для разбора");
        assertEquals(9, plot.columns(), "Участок 3×3 — девять колонок");
    }

    @Test
    void covers_CenterShift_SameOnlyWhenNear() {
        SettlementPlot plot = new SettlementPlot(new BlockCoord(0, 64, 0), 60, 70, 529, "oak");

        assertTrue(plot.covers(new BlockCoord(3, 64, -2)), "Сдвиг центра домов на пару блоков — тот же участок");
        assertFalse(
                plot.covers(new BlockCoord(40, 64, 0)),
                "Центр домов ушёл за четверть участка — другое поселение, участок обмеряется заново");
    }

    @Test
    void journalEntry_WrittenAndRead_KeepsHeight() {
        SettlementPlot plot = new SettlementPlot(new BlockCoord(-171, 68, -487), 63, 74, 529, "jungle");
        BoardJournal journal = new BoardJournal(directory.resolve("board-log.jsonl"));
        journal.enqueue(plot.journalEntry(100, "hash-1"));
        journal.flushAll();

        BoardLogEntry read = journal.last(1).get(0);

        assertEquals(BoardLog.Event.NOTE, read.event(), "Фиксация участка — заметка журнала доски (INC-55)");
        assertEquals(
                List.of("x=-171", "y=68", "z=-487", "min=63", "max=74", "columns=529", "wood=jungle"),
                read.modifiers(),
                "В журнале — центр, высота, мин/макс и число колонок: по ним видно, кто и когда посчитал высоту и выбрал породу (INC-58)");
        assertFalse(
                journal.load(new BoardLedger()).failed(),
                "Заметка не ломает чтение журнала при перезапуске: иначе отметки построек не восстановятся");
    }
}
