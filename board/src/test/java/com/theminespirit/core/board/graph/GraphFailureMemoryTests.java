package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.GraphBlock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphFailureMemoryTests {
    @Test
    void note_SameNodeAndTarget_CountsAndKeepsLastNeed() {
        GraphFailureMemory memory = new GraphFailureMemory();

        memory.note("make:dirt", "10,64,20", new GraphBlock.Path("10,64,20"), 100);
        memory.note("make:dirt", "10,64,20", new GraphBlock.Path("10,64,20"), 120);

        List<GraphFeedback.Failure> facts = memory.facts(120);
        assertEquals(1, facts.size(), "Провалы одной цели складываются в одну запись: " + facts);
        assertEquals(2, facts.get(0).count(), "Счётчик провалов цели растёт");
        assertEquals(new GraphBlock.Path("10,64,20"), facts.get(0).need(), "Последняя нужда важнее первой");
        assertEquals(120, facts.get(0).lastTick(), "В памяти остаётся время последнего провала");
    }

    @Test
    void note_DifferentTargets_KeepSeparateEntries() {
        GraphFailureMemory memory = new GraphFailureMemory();

        memory.note("make:dirt", "10,64,20", new GraphBlock.Path("10,64,20"), 100);
        memory.note("make:dirt", "-82,67,-87", new GraphBlock.Path("-82,67,-87"), 100);

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

        memory.note("make:dirt", "10,64,20", new GraphBlock.Path("10,64,20"), 100);

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

        memory.note("chop_jungle_log", "10,64,20", new GraphBlock.Path("10,64,20"), 100);
        memory.note("chop_jungle_log", "10,64,20", new GraphBlock.Path("10,64,20"), 120);
        memory.succeed("chop_jungle_log", "10,64,20", 130);

        assertTrue(
                memory.recent("chop_jungle_log", "10,64,20", 130).isEmpty(),
                "Успех по цели сбрасывает её счётчик: удачная рубка не тащит старый провал дальше");
        memory.note("chop_jungle_log", "10,64,20", new GraphBlock.Path("10,64,20"), 140);
        assertEquals(
                1,
                memory.recent("chop_jungle_log", "10,64,20", 140).orElseThrow().count(),
                "После сброса новый провал считается первым");
    }

    @Test
    void unreachablePoints_PathNeed_IsCollected() {
        GraphFailureMemory memory = new GraphFailureMemory();

        memory.note("chop_jungle_log", "1,64,1", new GraphBlock.Path("1,64,1"), 100);
        memory.note("chop_jungle_log", "2,64,2", new GraphBlock.Path("2,64,2"), 100);
        memory.note("chop_jungle_log", "3,64,3", null, 100);
        memory.note("chop_jungle_log", "4,64,4", new GraphBlock.Item("dirt", 12), 100);
        memory.note("chop_jungle_log", "5,64,5", null, 100);

        assertEquals(
                Set.of("1,64,1", "2,64,2"),
                memory.unreachablePoints(100, 1000),
                "Недостижима только нужда пути: чанк, материал и прочее не исключают цель");
        memory.succeed("chop_jungle_log", "1,64,1", 110);
        assertEquals(Set.of("2,64,2"), memory.unreachablePoints(110, 1000), "Успех цели убирает её из недостижимых");
    }

    @Test
    void unreachablePoints_DifferentNodes_CollectPointsForAllTasks() {
        GraphFailureMemory memory = new GraphFailureMemory();

        memory.note(
                "event:more2_stage_prep:more2_prepare_stage", "-171,71,-489", new GraphBlock.Path("-171,71,-489"), 100);
        memory.note(
                "event:more6_carry_materials:more6_deposit_materials",
                "-171,71,-489",
                new GraphBlock.Path("-171,71,-489"),
                110);
        memory.note("chop_jungle_log", "1,64,1", new GraphBlock.Item("dirt", 1), 120);
        memory.note("mine_coal", "", new GraphBlock.Path(""), 130);

        assertEquals(
                Set.of("-171,71,-489"),
                memory.unreachablePoints(130, 1000),
                "Недостижимая точка помнится сама по себе, а не только под задачей: иначе смена задания "
                        + "забывает провал и карусель повторяется (observe25 B5)");
    }

    @Test
    void recent_KnownFailure_ReturnsCountAndNeed() {
        GraphFailureMemory memory = new GraphFailureMemory();
        memory.note("building:streets", "-180,69,-520", null, 500);

        GraphFeedback.Failure failure =
                memory.recent("building:streets", "-180,69,-520", 500).orElseThrow();

        assertEquals(1, failure.count(), "Недавний провал отдаётся с числом");
        assertNull(failure.need(), "Провал без нужды (чанк) не выдумывает причину");
    }

    @Test
    void note_TypedNeed_IsNotParsedFromText() {
        GraphFailureMemory memory = new GraphFailureMemory();
        GraphBlock.Need need = new GraphBlock.Tool("pickaxe", 1, "spirit-1");

        memory.note("mine_coal", "", need, 10);

        assertEquals(need, memory.recent("mine_coal", "", 10).orElseThrow().need(), "Нужда едет типом как есть");
    }

    @Test
    void pathBlocks_FewTargets_NoBlockUntilDifferentTargets() {
        GraphFailureMemory memory = new GraphFailureMemory();
        memory.note("chop_oak_log", "1,64,0", new GraphBlock.Path("1,64,0"), 10);
        memory.note("chop_oak_log", "1,64,0", new GraphBlock.Path("1,64,0"), 11);

        assertTrue(memory.pathBlocks(11, 3000, 2).isEmpty(), "Одна цель не блокирует узел целиком");

        memory.note("chop_oak_log", "9,64,0", new GraphBlock.Path("9,64,0"), 12);

        List<GraphBlock> blocks = memory.pathBlocks(12, 3000, 2);
        assertEquals(1, blocks.size(), "Две разные недостижимые цели блокируют узел: " + blocks);
        assertEquals("chop_oak_log", blocks.get(0).node(), "Блокировка адресована узлу");
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
                "mine_coal",
                GraphFailureMemory.node("graph:mine_coal@spirit-1", null),
                "Дух-исполнитель не часть узла");
    }
}
