package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphCauseGuardTests {
    private static final Path GRAPH = Path.of("board/src/main/java/com/theminespirit/core/board/graph");
    private static final List<String> FORBIDDEN = List.of(
            "Cause.of(",
            "reason.toLowerCase(",
            "reason.contains(",
            "text.contains(\"маршрут",
            "text.contains(\"нет материал",
            "text.contains(\"чанк",
            "StuckCause");

    @Test
    void graphSources_ReasonText_IsNotParsed() {
        List<String> found = new ArrayList<>();
        Map<Path, String> sources = SourceFiles.javaUnder(GRAPH);
        for (Map.Entry<Path, String> source : sources.entrySet()) {
            String text = SourceFiles.withoutTextBlocks(source.getValue());
            for (String pattern : FORBIDDEN) {
                if (text.contains(pattern)) {
                    found.add(source.getKey().getFileName() + ": " + pattern);
                }
            }
        }

        assertEquals(
                List.of(),
                found,
                "Граф снова угадывает причину по тексту сообщения: причина создаётся там, где сбой обнаружен, "
                        + "и едет GraphBlock.Need. Цена: русская фраза меняется — блокировка ломается молча. "
                        + "Как надо: типизированная нужда в GraphState.blocks. Нарушители");
    }

    @Test
    void failureMemory_Loaded_StoresTypedNeed() {
        String source = SourceFiles.javaUnder(GRAPH).get(GRAPH.resolve("GraphFailureMemory.java"));

        assertTrue(
                source.contains("@Nullable Need"),
                "Память неудач обязана хранить типизированную нужду, а не строку причины");
        assertFalse(
                source.contains("String reason"),
                "Строка причины не должна попадать в память неудач: иначе граф снова начнёт её разбирать");
    }
}
