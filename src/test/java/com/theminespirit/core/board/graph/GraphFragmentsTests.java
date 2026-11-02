package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphFragmentsTests {
    private static final String MANUAL = """
            {
              "items": {"stick": {"xmin": 8, "base": 40}},
              "buildings": {},
              "ways": [
                {"id": "manual_stick", "case": "GATHER_MATERIALS", "makes": "stick", "yield": 4, "weight": 1.0}
              ]
            }
            """;
    private static final String GENERATED = """
            {
              "items": {"coal": {"xmin": 0, "base": 20}},
              "buildings": {},
              "ways": [
                {"id": "auto_coal", "case": "GATHER_MATERIALS", "makes": "coal", "yield": 1, "weight": 1.0}
              ]
            }
            """;
    private static final String FRAGMENT = """
            {
              "items": {"rope": {"xmin": 0, "base": 5}},
              "groups": {"#ropes": ["rope"]},
              "ways": [
                {"id": "frag_rope", "case": "GATHER_MATERIALS", "makes": "rope", "yield": 1, "weight": 1.0}
              ],
              "triggers": [{"have": "rope", "build": "stick"}]
            }
            """;

    @Test
    void merge_Fragment_AddsOnlyNewNodes() {
        SupplyGraph graph = SupplyGraph.merge(
                MANUAL,
                "manual.json",
                GENERATED,
                "generated.json",
                List.of(new GraphFragments.Fragment(FRAGMENT, "board/graph.d/rope.json")));

        assertTrue(graph.items().containsKey("rope"), "Фрагмент добавляет новый предмет");
        assertEquals(List.of("rope"), graph.groups().get("#ropes"), "Фрагмент добавляет новую группу");
        assertTrue(graph.way("frag_rope").isPresent(), "Фрагмент добавляет новый путь");
        assertEquals(
                List.of(new SupplyGraph.Trigger("rope", "stick")),
                graph.triggers(),
                "Фрагмент добавляет новый триггер");
        assertTrue(graph.way("auto_coal").isPresent(), "Слияние с рецептами не теряется");
    }

    @Test
    void merge_FragmentRepeatsExistingId_NamesFile() {
        String duplicate = """
                {
                  "items": {"stick": {"xmin": 0, "base": 1}}
                }
                """;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> SupplyGraph.merge(
                        MANUAL,
                        "manual.json",
                        GENERATED,
                        "generated.json",
                        List.of(new GraphFragments.Fragment(duplicate, "board/graph.d/broken.json"))),
                "Повтор id во фрагменте обязан отклоняться: иначе два файла спорят за один узел");

        assertTrue(
                error.getMessage().contains("board/graph.d/broken.json"),
                "Ошибка обязана назвать файл фрагмента: " + error.getMessage());
        assertTrue(
                error.getMessage().contains("stick"), "Ошибка обязана назвать повторённый id: " + error.getMessage());
    }

    @Test
    void merge_FragmentsRepeatIdAcrossFiles_NamesSecondFile() {
        GraphFragments.Fragment first = new GraphFragments.Fragment(FRAGMENT, "board/graph.d/first.json");
        GraphFragments.Fragment second = new GraphFragments.Fragment(FRAGMENT, "board/graph.d/second.json");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> SupplyGraph.merge(MANUAL, "manual.json", GENERATED, "generated.json", List.of(first, second)),
                "Два фрагмента не имеют права добавить один и тот же id");

        assertTrue(
                error.getMessage().contains("board/graph.d/second.json"),
                "Ошибка обязана назвать второй файл: " + error.getMessage());
    }

    @Test
    void fromDirectory_JsonFiles_AreSortedAndRead(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("b.json"), "{}");
        Files.writeString(directory.resolve("a.json"), "{}");
        Files.writeString(directory.resolve("note.txt"), "не фрагмент");

        List<GraphFragments.Fragment> fragments = GraphFragments.fromDirectory(directory);

        assertEquals(
                List.of("a.json", "b.json"),
                fragments.stream()
                        .map(fragment ->
                                Path.of(fragment.origin()).getFileName().toString())
                        .toList(),
                "Фрагменты читаются по имени: порядок слияния не зависит от файловой системы");
    }

    @Test
    void loadDefault_ProductionFragments_LoadWithoutConflict() {
        SupplyGraph graph = SupplyGraph.loadDefault();

        assertTrue(graph.items().containsKey("stick"), "Слияние рецептов работает вместе с каталогом фрагментов");
        assertTrue(!graph.ways().isEmpty(), "Граф по умолчанию не пуст: каталог фрагментов прочитан");
    }
}
