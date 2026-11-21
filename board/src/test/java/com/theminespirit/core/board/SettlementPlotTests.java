package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.SettlementPlot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettlementPlotTests {
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
                            + "(INC-56)"
                            + " Иначе тест center_AnyOrderOfSpirits_SamePlot перестанет ловить эту поломку. При смене center_AnyOrderOfSpirits_SamePlot проверь это утверждение.");
        }
        assertEquals(
                new BlockCoord(-171, 71, -487),
                expected,
                "Центр — нижняя медиана домов по каждой оси: при смене правила перепроверить раскладку участка"
                        + " Иначе тест center_AnyOrderOfSpirits_SamePlot перестанет ловить эту поломку.");
    }

    @Test
    void survey_SlopedPlot_MedianWithMinMax() {
        SettlementPlot plot = SettlementPlot.survey(
                new BlockCoord(0, 64, 0), ColumnMask.rect(-1, -1, 1, 1), (x, z) -> 64 + x, "oak", OptionalInt.empty());

        assertEquals(
                64,
                plot.anchor().y(),
                "Высота участка — медиана ног колонок, а не высота дома (INC-55)"
                        + " Иначе тест survey_SlopedPlot_MedianWithMinMax перестанет ловить эту поломку. При смене survey_SlopedPlot_MedianWithMinMax проверь это утверждение.");
        assertEquals(
                63,
                plot.min(),
                "Минимум колонок пишется в лог и журнал для разбора"
                        + " Иначе тест survey_SlopedPlot_MedianWithMinMax перестанет ловить эту поломку. При смене survey_SlopedPlot_MedianWithMinMax проверь это утверждение.");
        assertEquals(
                65,
                plot.max(),
                "Максимум колонок пишется в лог и журнал для разбора"
                        + " Иначе тест survey_SlopedPlot_MedianWithMinMax перестанет ловить эту поломку. При смене survey_SlopedPlot_MedianWithMinMax проверь это утверждение.");
        assertEquals(
                9,
                plot.columns(),
                "Участок 3×3 — девять колонок"
                        + " Иначе тест survey_SlopedPlot_MedianWithMinMax перестанет ловить эту поломку. При смене survey_SlopedPlot_MedianWithMinMax проверь это утверждение.");
    }

    @Test
    void covers_CenterShift_SameOnlyWhenNear() {
        SettlementPlot plot = new SettlementPlot(new BlockCoord(0, 64, 0), 60, 70, 529, "oak");

        assertTrue(
                plot.covers(new BlockCoord(3, 64, -2)),
                "Сдвиг центра домов на пару блоков — тот же участок"
                        + " Иначе тест covers_CenterShift_SameOnlyWhenNear перестанет ловить эту поломку. При смене covers_CenterShift_SameOnlyWhenNear проверь это утверждение.");
        assertFalse(
                plot.covers(new BlockCoord(40, 64, 0)),
                "Центр домов ушёл за четверть участка — другое поселение, участок обмеряется заново"
                        + " Иначе тест covers_CenterShift_SameOnlyWhenNear перестанет ловить эту поломку. При смене covers_CenterShift_SameOnlyWhenNear проверь это утверждение.");
    }

    @Test
    void journalEntry_WrittenAndRead_KeepsHeight() {
        SettlementPlot plot = new SettlementPlot(new BlockCoord(-171, 68, -487), 63, 74, 529, "jungle");
        BoardJournal journal = new BoardJournal(directory.resolve("board-log.jsonl"));
        journal.enqueue(SettlementLevel.journalEntry(plot, 100, "hash-1"));
        journal.flushAll();

        BoardLogEntry read = journal.last(1).get(0);

        assertEquals(
                BoardLog.Event.NOTE,
                read.event(),
                "Фиксация участка — заметка журнала доски (INC-55)"
                        + " Иначе тест journalEntry_WrittenAndRead_KeepsHeight перестанет ловить эту поломку. При смене journalEntry_WrittenAndRead_KeepsHeight проверь это утверждение.");
        assertEquals(
                List.of("x=-171", "y=68", "z=-487", "min=63", "max=74", "columns=529", "wood=jungle"),
                read.modifiers(),
                "В журнале — центр, высота, мин/макс и число колонок: по ним видно, кто и когда посчитал высоту и выбрал породу (INC-58)"
                        + " Иначе тест journalEntry_WrittenAndRead_KeepsHeight перестанет ловить эту поломку. При смене journalEntry_WrittenAndRead_KeepsHeight проверь это утверждение.");
        assertFalse(
                journal.load(new BoardLedger()).failed(),
                "Заметка не ломает чтение журнала при перезапуске: иначе отметки построек не восстановятся"
                        + " При смене journalEntry_WrittenAndRead_KeepsHeight проверь это утверждение.");
    }
}
