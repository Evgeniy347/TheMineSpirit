package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementScanSliceTests {
    @Test
    void scanSlice_FirstCall_ReturnsPrefixAndMore() {
        SettlementScanTests.Fake world = new SettlementScanTests.Fake();
        List<ColumnMask.Cell> columns = ColumnMask.rect(0, 0, 9, 9).cells();

        SettlementScan.Slice slice =
                SettlementScan.scanSlice(columns, 0, 40, SettlementScanTests.LEVEL, SettlementScanTests.rules(), world);

        assertEquals(
                40,
                slice.nextIndex(),
                "Срез обязан отчитаться, до какой колонки дошёл: иначе следующий тик не знает, где продолжить"
                        + " Иначе тест scanSlice_FirstCall_ReturnsPrefixAndMore перестанет ловить эту поломку. При смене scanSlice_FirstCall_ReturnsPrefixAndMore проверь это утверждение.");
        assertTrue(
                slice.more(),
                "Колонок 100, бюджет 40 — работа не закончена: иначе порционный скан схлопывается в однотиковый"
                        + " Иначе тест scanSlice_FirstCall_ReturnsPrefixAndMore перестанет ловить эту поломку. При смене scanSlice_FirstCall_ReturnsPrefixAndMore проверь это утверждение.");
        assertEquals(
                null,
                slice.done(),
                "Промежуточный срез не отдаёт результат: ранний выход давал бы доске пустые факты участка"
                        + " Иначе тест scanSlice_FirstCall_ReturnsPrefixAndMore перестанет ловить эту поломку. При смене scanSlice_FirstCall_ReturnsPrefixAndMore проверь это утверждение.");
    }

    @Test
    void scanSlice_LastColumn_ReturnsDoneStrays() {
        SettlementScanTests.Fake world = new SettlementScanTests.Fake();
        world.put(3, SettlementScanTests.LEVEL, 3, "minecraft:cobblestone");
        List<ColumnMask.Cell> columns = ColumnMask.rect(0, 0, 4, 4).cells();

        SettlementScan.Slice slice = SettlementScan.scanSlice(
                columns, 0, 100, SettlementScanTests.LEVEL, SettlementScanTests.rules(), world);

        assertTrue(
                !slice.more(),
                "Бюджет больше числа колонок — срез последний: иначе скан никогда не завершается"
                        + " Иначе тест scanSlice_LastColumn_ReturnsDoneStrays перестанет ловить эту поломку. При смене scanSlice_LastColumn_ReturnsDoneStrays проверь это утверждение.");
        assertEquals(
                List.of(new BlockCoord(3, SettlementScanTests.LEVEL, 3)),
                slice.done().strays().stream().map(StrayGroups.Cell::at).toList(),
                "Последний срез отдаёт накопленный результат целиком: булыжник обязан дойти до доски"
                        + " Иначе тест scanSlice_LastColumn_ReturnsDoneStrays перестанет ловить эту поломку. При смене scanSlice_LastColumn_ReturnsDoneStrays проверь это утверждение.");
    }

    @Test
    void scanSlice_ResumeFromIndex_ContinuesColumns() {
        SettlementScanTests.Fake world = new SettlementScanTests.Fake();
        world.put(7, SettlementScanTests.LEVEL, 7, "minecraft:cobblestone");
        List<ColumnMask.Cell> columns = ColumnMask.rect(0, 0, 9, 9).cells();

        SettlementScan.Slice first =
                SettlementScan.scanSlice(columns, 0, 50, SettlementScanTests.LEVEL, SettlementScanTests.rules(), world);
        SettlementScan.Slice second = SettlementScan.scanSlice(
                columns, first.nextIndex(), 50, SettlementScanTests.LEVEL, SettlementScanTests.rules(), world);

        assertTrue(
                !second.more(),
                "Два среза по 50 покрывают 100 колонок: иначе возобновление с nextIndex ломается"
                        + " Иначе тест scanSlice_ResumeFromIndex_ContinuesColumns перестанет ловить эту поломку. При смене scanSlice_ResumeFromIndex_ContinuesColumns проверь это утверждение.");
        assertEquals(
                List.of(new BlockCoord(7, SettlementScanTests.LEVEL, 7)),
                second.done().strays().stream().map(StrayGroups.Cell::at).toList(),
                "Булыжник во второй половине находится только при продолжении с nextIndex: пропуск колонок"
                        + " прячет лишнее от доски"
                        + " Иначе тест scanSlice_ResumeFromIndex_ContinuesColumns перестанет ловить эту поломку. При смене scanSlice_ResumeFromIndex_ContinuesColumns проверь это утверждение.");
    }
}
