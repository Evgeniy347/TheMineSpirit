package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreSourceGuardTests {
    private static final Path BOARD = SourceFiles.path("neoforge/board");
    private static final Map<Path, String> SOURCES = SourceFiles.javaUnder(BOARD);

    @Test
    void boardSources_StorageWatchers_DoNotTickOwnWork() {
        String sources = SOURCES.get(BOARD.resolve("BoardSources.java"));

        assertEquals(
                List.of(),
                found(sources, "WarehouseWatch.tick("),
                "Склад снова строит свой сторож: задание на склад выбирает узел графа building:warehouse, "
                        + "иначе граф и сторож ставят одну постройку дважды. Уберите вызов из BoardSources. "
                        + "Нарушители: WarehouseWatch.tick");
    }

    @Test
    void stageSurroundings_StorageWatchers_AreNotTicked() {
        String sources = SOURCES.getOrDefault(BOARD.resolve("StageSurroundings.java"), "");

        List<String> found = new ArrayList<>();
        found.addAll(found(sources, "OldChests."));
        found.addAll(found(sources, "ChestSpaceWatch."));
        assertEquals(
                List.of(),
                found,
                "Сторожа склада снова ставят задания мимо графа: перенос старых сундуков и новую пару выбирают "
                        + "узлы store:transfer и store:space, а сторож — только источник фактов. "
                        + "Нарушители: OldChests, ChestSpaceWatch");
    }

    @Test
    void chestSpaceWatch_OnlyFactAndGraphExecutor_NoOwnTick() {
        String sources = SOURCES.get(BOARD.resolve("ChestSpaceWatch.java"));

        List<String> found = new ArrayList<>();
        found.addAll(found(sources, "static void tick("));
        found.addAll(found(sources, "static void request("));
        assertEquals(
                List.of(),
                found,
                "ChestSpaceWatch сам решает, когда ставить сундук: порог и место выбирает узел store:space, "
                        + "а сторож оставляет только факт свободных слотов и исполнение задания графа. "
                        + "Нарушители: ChestSpaceWatch.tick, ChestSpaceWatch.request");
    }

    @Test
    void yardFloorWatch_OwnTick_IsRemoved() {
        String sources = SOURCES.get(BOARD.resolve("YardFloorWatch.java"));

        List<String> found = new ArrayList<>();
        found.addAll(found(sources, "static void tick("));
        SOURCES.values().forEach(source -> found.addAll(found(source, "YardFloorWatch.tick(")));
        assertEquals(
                List.of(),
                found,
                "Дыры под станками двора снова чинятся сторожами мимо графа: ремонт выбирает узел "
                        + "repair:stock_chests, сторож остаётся аудитом пола. "
                        + "Нарушители: YardFloorWatch.tick, любой вызов YardFloorWatch.tick из аудита");
    }

    private static List<String> found(String source, String pattern) {
        return source.contains(pattern) ? List.of(pattern) : List.of();
    }
}
