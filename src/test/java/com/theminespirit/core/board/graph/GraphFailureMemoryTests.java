package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphFailureMemoryTests {
    @Test
    void note_SameNodeAndTarget_CountsAndKeepsLastCause() {
        GraphFailureMemory memory = new GraphFailureMemory();

        memory.note("make:dirt", "10,64,20", "маршрут не найден", 100);
        memory.note("make:dirt", "10,64,20", "не дошёл до цели", 120);

        List<GraphFeedback.Failure> facts = memory.facts(120);
        assertEquals(1, facts.size(), "Провалы одной цели складываются в одну запись: " + facts);
        assertEquals(2, facts.get(0).count(), "Счётчик провалов цели растёт");
        assertEquals(GraphFeedback.Cause.NOT_REACHED, facts.get(0).cause(), "Последняя причина важнее первой");
        assertEquals(120, facts.get(0).lastTick(), "В памяти остаётся время последнего провала");
    }

    @Test
    void note_DifferentTargets_KeepSeparateEntries() {
        GraphFailureMemory memory = new GraphFailureMemory();

        memory.note("make:dirt", "10,64,20", "маршрут не найден", 100);
        memory.note("make:dirt", "-82,67,-87", "маршрут не найден", 100);

        assertEquals(2, memory.facts(100).size(), "Цель — часть ключа памяти: разные цели не смешиваются");
        assertEquals(
                2.0,
                memory.facts(100).stream()
                        .filter(failure -> "make:dirt".equals(failure.node()))
                        .mapToDouble(GraphFeedback.Failure::weight)
                        .sum(),
                1e-9,
                "Штраф узла видит все его цели: затухающие веса складываются, а не обрываются счётчиком");
    }

    @Test
    void facts_OldFailures_DecayInsteadOfHardWindow() {
        GraphFailureMemory memory = new GraphFailureMemory();
        double halfLife = 1000.0;

        memory.note("make:dirt", "10,64,20", "маршрут не найден", 100);

        GraphFeedback.Failure fresh =
                memory.recent("make:dirt", "10,64,20", 100, halfLife).orElseThrow();
        GraphFeedback.Failure half =
                memory.recent("make:dirt", "10,64,20", 1100, halfLife).orElseThrow();
        assertEquals(1.0, fresh.weight(), 1e-9, "Свежий провал весит целый счётчик");
        assertEquals(0.5, half.weight(), 1e-9, "Через полураспад штраф цели вдвое слабее, а не обрывается");
        assertTrue(
                memory.facts(100 + 40_000, halfLife).isEmpty(),
                "Спустя много полураспадов провал затухает до нуля и не держит память вечно");
        assertTrue(
                memory.recent("make:dirt", "10,64,20", 100 + 40_000, halfLife).isEmpty(),
                "Затухшая цель не считается недавней");
    }

    @Test
    void succeed_SameTarget_ResetsItsCounter() {
        GraphFailureMemory memory = new GraphFailureMemory();

        memory.note("chop_jungle_log", "10,64,20", "маршрут не найден", 100);
        memory.note("chop_jungle_log", "10,64,20", "маршрут не найден", 120);
        memory.succeed("chop_jungle_log", "10,64,20", 130);

        assertTrue(
                memory.recent("chop_jungle_log", "10,64,20", 130).isEmpty(),
                "Успех по цели сбрасывает её счётчик: удачная рубка не тащит старый провал дальше");
        memory.note("chop_jungle_log", "10,64,20", "маршрут не найден", 140);
        assertEquals(
                1,
                memory.recent("chop_jungle_log", "10,64,20", 140).orElseThrow().count(),
                "После сброса новый провал считается первым");
    }

    @Test
    void unreachableTargets_NoPathAndNotReached_OnlyThoseAreMarked() {
        GraphFailureMemory memory = new GraphFailureMemory();

        memory.note("chop_jungle_log", "1,64,1", "маршрут не найден", 100);
        memory.note("chop_jungle_log", "2,64,2", "застрял, не дошёл", 100);
        memory.note("chop_jungle_log", "3,64,3", "чанк не загружен", 100);
        memory.note("chop_jungle_log", "4,64,4", "не хватает dirt=12", 100);
        memory.note("chop_jungle_log", "5,64,5", "причина не записана", 100);

        assertEquals(
                Set.of("1,64,1", "2,64,2"),
                memory.unreachableTargets("chop_jungle_log", 100, 1000),
                "Недостижимы только «нет пути» и «не дошёл»: чанк, материал и прочее не исключают цель");
        memory.succeed("chop_jungle_log", "1,64,1", 110);
        assertEquals(
                Set.of("2,64,2"),
                memory.unreachableTargets("chop_jungle_log", 110, 1000),
                "Успех цели убирает её из недостижимых");
    }

    @Test
    void recent_KnownFailure_ReturnsCountAndCause() {
        GraphFailureMemory memory = new GraphFailureMemory();
        memory.note("building:streets", "-180,69,-520", "чанк не загружен", 500);

        GraphFeedback.Failure failure =
                memory.recent("building:streets", "-180,69,-520", 500).orElseThrow();

        assertEquals(1, failure.count(), "Недавний провал отдаётся с числом");
        assertEquals(GraphFeedback.Cause.CHUNK, failure.cause(), "Причина «чанк» распознана");
    }

    @Test
    void cause_KnownPhrases_AreClassified() {
        assertEquals(
                GraphFeedback.Cause.NO_PATH,
                GraphFeedback.Cause.of("маршрут не найден: цель (-82,67,-87)"),
                "Нет маршрута — NO_PATH");
        assertEquals(
                GraphFeedback.Cause.NO_PATH,
                GraphFeedback.Cause.of("путь упёрся в тупик в пределах 4"),
                "Тупик — NO_PATH");
        assertEquals(
                GraphFeedback.Cause.NOT_REACHED,
                GraphFeedback.Cause.of("стоянка (-172,70,-491) за преградой"),
                "Стоянка за преградой — NOT_REACHED");
        assertEquals(
                GraphFeedback.Cause.NO_MATERIAL,
                GraphFeedback.Cause.of("не хватает dirt=12"),
                "Нехватка — NO_MATERIAL");
        assertEquals(GraphFeedback.Cause.CHUNK, GraphFeedback.Cause.of("чанк не загружен"), "Чанк — CHUNK");
        assertEquals(GraphFeedback.Cause.OTHER, GraphFeedback.Cause.of("причина не записана"), "Неизвестная — OTHER");
        assertEquals(GraphFeedback.Cause.OTHER, GraphFeedback.Cause.of(null), "Пустая причина — OTHER");
    }

    @Test
    void node_GraphTicket_LosesPrefixPartAndSpirit() {
        assertEquals("make:dirt", GraphFailureMemory.node("graph:make:dirt:2", null), "Дубль части — тот же узел");
        assertEquals("make:dirt", GraphFailureMemory.node("graph:make:dirt", null), "Префикс графа снят");
        assertEquals(
                "building:streets",
                GraphFailureMemory.node("graph:building:streets", "graph:building:streets"),
                "Постройка остаётся собой");
        assertEquals(
                "tool:pickaxe",
                GraphFailureMemory.node("graph:tool:pickaxe@spirit-1", null),
                "Личный путь на инструмент сводится к узлу");
        assertEquals(
                "event:more2_shortage:more2_gather",
                GraphFailureMemory.node("first:build_streets", "graph:event:more2_shortage:more2_gather"),
                "Причина билета мимо графа называет узел графа");
        assertEquals(
                "rescue_spirit",
                GraphFailureMemory.node("rescue:c48663b4-da59-43d9-9804-81a680580300", "graph:rescue_spirit@29454474"),
                "Тик эпизода в причине спасения не дробит память неудач: узел остаётся rescue_spirit");
        assertNull(
                GraphFailureMemory.node("first:build_streets", "first:build_streets"),
                "Провал мимо графа не попадает в память узлов");
    }
}
