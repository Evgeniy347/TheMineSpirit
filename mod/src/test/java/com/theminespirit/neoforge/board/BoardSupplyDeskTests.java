package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BoardSupplyDeskTests {
    @Test
    void stock_LoadedContainers_UsesRegistryWithoutBlockWalk() throws IOException {
        String desk =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/BoardSupplyDesk.java"));
        String reader =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/StockReader.java"));

        assertTrue(
                desk.contains("reader.collect(level, spirits)") && reader.contains("ChestSnapshots.positions(level)"),
                "Запасы берутся из реестра и загруженных чанков: иначе холодный stock снова обходит мир "
                        + "по блокам. При смене stock_LoadedContainers_UsesRegistryWithoutBlockWalk проверь "
                        + "BoardSupplyDesk.refresh и StockReader.collect.");
        assertFalse(
                reader.contains("StockScan"),
                "Блочный StockScan удалён: иначе лишний обход снова займёт секунды в стартовом тике. "
                        + "При смене stock_LoadedContainers_UsesRegistryWithoutBlockWalk проверь ChestSnapshots.positions.");
    }

    @Test
    void perfReport_FirstWindow_IsNotDiscarded() throws IOException {
        String report = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/PerfReport.java"));

        assertTrue(
                report.contains("if (lines.isEmpty())") && !report.contains("boolean first"),
                "Первое окно производительности не выбрасывается: иначе холодный stock снова скроется "
                        + "из отчёта. При смене perfReport_FirstWindow_IsNotDiscarded проверь PerfReport.tick.");
    }
}
