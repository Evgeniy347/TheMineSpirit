package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CraftedMaterialSourceGuardTests {
    private static final Path BOARD = Path.of("src/main/java/com/theminespirit/neoforge/board");
    private static final Path GRAPH_POST = BOARD.resolve("GraphPost.java");
    private static final String MAKE_GATHER = "gather(MAKE + item";
    private static final String CRAFTED_CHECK = "CraftedItems.isCrafted(item)";
    private static final String VIOLATION = "GraphPost.java: graph:make добывает крафтовый предмет из мира";

    @Test
    void graphPost_MissingCraftedBuildingMaterial_IsNotGathered() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(GRAPH_POST, SourceFiles.javaUnder(BOARD).get(GRAPH_POST));

        assertEquals(
                List.of(),
                violations(sources),
                "graph:make снова выдаёт добычу крафтового предмета: доски, факелы и сундуки ищутся блоками в мире "
                        + "(нет пути к блоку, пропущено защищённых), задание падает 5 раз и уводит духа от крафта. "
                        + "Как надо: ветка недостающих материалов постройки выдаёт gather только для природного "
                        + "предмета, а крафтовый оставляет путям графа (auto_/craft_ рецепта). Нарушители");
    }

    @Test
    void patternSource_UnfilteredMakeGather_IsReported() {
        String old = """
                missing.forEach((item, count) -> {
                    if (GraphItemIds.known(item)) {
                        gather(MAKE + item, ticket.id(), task.priority(), item, count, craftPoint());
                    }
                });
                """;

        assertEquals(
                List.of(VIOLATION),
                violations(Map.of(GRAPH_POST, old)),
                "Сторож ослеп: старый graph:make, который добывает любой предмет постройки из мира, обязан ловиться");
    }

    @Test
    void patternSource_CraftedFilterBeforeGather_IsAccepted() {
        String fixed = """
                missing.forEach((item, count) -> {
                    if (GraphItemIds.known(item) && !CraftedItems.isCrafted(item)) {
                        gather(MAKE + item, ticket.id(), task.priority(), item, count, craftPoint());
                    }
                });
                """;

        assertEquals(
                List.of(),
                violations(Map.of(GRAPH_POST, fixed)),
                "Сторож ложно срабатывает: проверка крафтового предмета перед gather — правильный код");
    }

    private static List<String> violations(Map<Path, String> sources) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<Path, String> source : sources.entrySet()) {
            String text = source.getValue();
            if (text.contains(MAKE_GATHER) && !text.contains(CRAFTED_CHECK)) {
                found.add(source.getKey().getFileName() + ": graph:make добывает крафтовый предмет из мира");
            }
        }
        return found;
    }
}
