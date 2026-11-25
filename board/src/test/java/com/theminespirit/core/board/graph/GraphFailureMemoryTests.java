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
        assertEquals(
                1,
                facts.size(),
                "Провалы одной цели складываются в одну запись: " + facts
                        + " Иначе тест note_SameNodeAndTarget_CountsAndKeepsLastNeed перестанет ловить эту поломку. При смене note_SameNodeAndTarget_CountsAndKeepsLastNeed проверь это утверждение.");
        assertEquals(
                2,
                facts.get(0).count(),
                "Счётчик провалов цели растёт"
                        + " Иначе тест note_SameNodeAndTarget_CountsAndKeepsLastNeed перестанет ловить эту поломку. При смене note_SameNodeAndTarget_CountsAndKeepsLastNeed проверь это утверждение.");
        assertEquals(
                new GraphBlock.Path("10,64,20"),
                facts.get(0).need(),
                "Последняя нужда важнее первой"
                        + " Иначе тест note_SameNodeAndTarget_CountsAndKeepsLastNeed перестанет ловить эту поломку. При смене note_SameNodeAndTarget_CountsAndKeepsLastNeed проверь это утверждение.");
        assertEquals(
                120,
                facts.get(0).lastTick(),
                "В памяти остаётся время последнего провала"
                        + " Иначе тест note_SameNodeAndTarget_CountsAndKeepsLastNeed перестанет ловить эту поломку. При смене note_SameNodeAndTarget_CountsAndKeepsLastNeed проверь это утверждение.");
    }

    @Test
    void note_DifferentTargets_KeepSeparateEntries() {
        GraphFailureMemory memory = new GraphFailureMemory();

        memory.note("make:dirt", "10,64,20", new GraphBlock.Path("10,64,20"), 100);
        memory.note("make:dirt", "-82,67,-87", new GraphBlock.Path("-82,67,-87"), 100);

        assertEquals(
                2,
                memory.facts(100).size(),
                "Цель — часть ключа памяти: разные цели не смешиваются"
                        + " Иначе тест note_DifferentTargets_KeepSeparateEntries перестанет ловить эту поломку. При смене note_DifferentTargets_KeepSeparateEntries проверь это утверждение.");
        assertEquals(
                2 * memory.facts(100).get(0).weight(),
                memory.facts(100).stream()
                        .filter(failure -> "make:dirt".equals(failure.node()))
                        .mapToDouble(GraphFeedback.Failure::weight)
                        .sum(),
                1e-9,
                "Штраф узла видит все его цели: затухающие веса складываются, а не обрываются счётчиком"
                        + " Иначе тест note_DifferentTargets_KeepSeparateEntries перестанет ловить эту поломку. При смене note_DifferentTargets_KeepSeparateEntries проверь это утверждение.");
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
        assertEquals(
                fresh.weight() / 2,
                half.weight(),
                1e-9,
                "Через полураспад штраф цели вдвое слабее, а не обрывается"
                        + " Иначе тест facts_OldFailures_DecayInsteadOfHardWindow перестанет ловить эту поломку. При смене facts_OldFailures_DecayInsteadOfHardWindow проверь это утверждение.");
        assertTrue(
                memory.facts(100 + 40_000, halfLife).isEmpty(),
                "Спустя много полураспадов провал затухает до нуля и не держит память вечно"
                        + " Иначе тест facts_OldFailures_DecayInsteadOfHardWindow перестанет ловить эту поломку. При смене facts_OldFailures_DecayInsteadOfHardWindow проверь это утверждение.");
        assertTrue(
                memory.recent("make:dirt", "10,64,20", 100 + 40_000, halfLife).isEmpty(),
                "Затухшая цель не считается недавней"
                        + " Иначе тест facts_OldFailures_DecayInsteadOfHardWindow перестанет ловить эту поломку. При смене facts_OldFailures_DecayInsteadOfHardWindow проверь это утверждение.");
    }

    @Test
    void recentPath_ItemFailure_DoesNotBlockTargetAsUnreachable() {
        GraphFailureMemory memory = new GraphFailureMemory();
        memory.note("more_light_dark_spot", "-75,109,-268", new GraphBlock.Item("torch", 1), 100);

        assertTrue(
                memory.recentPath("more_light_dark_spot", "-75,109,-268", 100).isEmpty(),
                "Нехватка факела не доказывает недостижимость точки: recentPath должен пропускать"
                        + " только отказ пути, иначе освещение заблокируется из-за материала Иначе тест"
                        + " recentPath_ItemFailure_DoesNotBlockTargetAsUnreachable перестанет ловить"
                        + " эту поломку. При смене recentPath_ItemFailure_DoesNotBlockTargetAsUnreachable"
                        + " проверь GraphFailureMemory.unreachable.");
        memory.note("more_light_dark_spot", "-75,109,-268", new GraphBlock.Path("-75,109,-268"), 120);
        assertTrue(
                memory.recentPath("more_light_dark_spot", "-75,109,-268", 120).isPresent(),
                "После отказа пути та же точка временно блокируется: граф не должен снова выдавать"
                        + " её другому духу каждые 300 тиков Иначе тест"
                        + " recentPath_ItemFailure_DoesNotBlockTargetAsUnreachable перестанет ловить"
                        + " живой случай LIGHTING от 25.11. При смене"
                        + " recentPath_ItemFailure_DoesNotBlockTargetAsUnreachable проверь MorePost.lightDarkSpot.");
    }

    @Test
    void saved_RestoredMemory_KeepsDecayAndNeed() {
        GraphFailureMemory memory = new GraphFailureMemory();
        memory.note("chop_jungle_log", "10,64,20", new GraphBlock.Path("10,64,20"), 100);
        memory.note("make:dirt", "", new GraphBlock.Item("dirt", 12), 200);
        GraphFailureMemory restored = new GraphFailureMemory();

        restored.restore(memory.saved());

        assertEquals(
                Set.of("10,64,20"),
                restored.unreachablePoints(200, 1000),
                "После перезапуска недостижимая точка помнится: пустая цель в набор не попадает"
                        + " Иначе тест saved_RestoredMemory_KeepsDecayAndNeed перестанет ловить эту поломку. При смене saved_RestoredMemory_KeepsDecayAndNeed проверь это утверждение.");
        GraphFeedback.Failure half =
                restored.recent("chop_jungle_log", "10,64,20", 1100, 1000).orElseThrow();
        assertEquals(
                memory.recent("chop_jungle_log", "10,64,20", 1100, 1000)
                        .orElseThrow()
                        .weight(),
                half.weight(),
                1e-9,
                "Сохранённая память затухает по времени так же, как исходная, а не начинается заново"
                        + " Иначе тест saved_RestoredMemory_KeepsDecayAndNeed перестанет ловить эту поломку. При смене saved_RestoredMemory_KeepsDecayAndNeed проверь это утверждение.");
        assertEquals(
                new GraphBlock.Item("dirt", 12),
                restored.recent("make:dirt", "", 200, 1000).orElseThrow().need(),
                "Нужда провала переживает перезапуск вместе с целью"
                        + " Иначе тест saved_RestoredMemory_KeepsDecayAndNeed перестанет ловить эту поломку. При смене saved_RestoredMemory_KeepsDecayAndNeed проверь это утверждение.");
    }

    @Test
    void succeed_SameTarget_ResetsItsCounter() {
        GraphFailureMemory memory = new GraphFailureMemory();

        memory.note("chop_jungle_log", "10,64,20", new GraphBlock.Path("10,64,20"), 100);
        memory.note("chop_jungle_log", "10,64,20", new GraphBlock.Path("10,64,20"), 120);
        memory.succeed("chop_jungle_log", "10,64,20", 130);

        assertTrue(
                memory.recent("chop_jungle_log", "10,64,20", 130).isEmpty(),
                "Успех по цели сбрасывает её счётчик: удачная рубка не тащит старый провал дальше"
                        + " Иначе тест succeed_SameTarget_ResetsItsCounter перестанет ловить эту поломку. При смене succeed_SameTarget_ResetsItsCounter проверь это утверждение.");
        memory.note("chop_jungle_log", "10,64,20", new GraphBlock.Path("10,64,20"), 140);
        assertEquals(
                1,
                memory.recent("chop_jungle_log", "10,64,20", 140).orElseThrow().count(),
                "После сброса новый провал считается первым"
                        + " Иначе тест succeed_SameTarget_ResetsItsCounter перестанет ловить эту поломку. При смене succeed_SameTarget_ResetsItsCounter проверь это утверждение.");
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
                "Недостижима только нужда пути: чанк, материал и прочее не исключают цель"
                        + " Иначе тест unreachablePoints_PathNeed_IsCollected перестанет ловить эту поломку. При смене unreachablePoints_PathNeed_IsCollected проверь это утверждение.");
        memory.succeed("chop_jungle_log", "1,64,1", 110);
        assertEquals(
                Set.of("2,64,2"),
                memory.unreachablePoints(110, 1000),
                "Успех цели убирает её из недостижимых"
                        + " Иначе тест unreachablePoints_PathNeed_IsCollected перестанет ловить эту поломку. При смене unreachablePoints_PathNeed_IsCollected проверь это утверждение.");
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
                        + "забывает провал и карусель повторяется (observe25 B5)"
                        + " При смене unreachablePoints_DifferentNodes_CollectPointsForAllTasks проверь это утверждение.");
    }

    @Test
    void recent_KnownFailure_ReturnsCountAndNeed() {
        GraphFailureMemory memory = new GraphFailureMemory();
        memory.note("building:streets", "-180,69,-520", null, 500);

        GraphFeedback.Failure failure =
                memory.recent("building:streets", "-180,69,-520", 500).orElseThrow();

        assertEquals(
                1,
                failure.count(),
                "Недавний провал отдаётся с числом"
                        + " Иначе тест recent_KnownFailure_ReturnsCountAndNeed перестанет ловить эту поломку. При смене recent_KnownFailure_ReturnsCountAndNeed проверь это утверждение.");
        assertNull(
                failure.need(),
                "Провал без нужды (чанк) не выдумывает причину"
                        + " Иначе тест recent_KnownFailure_ReturnsCountAndNeed перестанет ловить эту поломку. При смене recent_KnownFailure_ReturnsCountAndNeed проверь это утверждение.");
    }

    @Test
    void note_TypedNeed_IsNotParsedFromText() {
        GraphFailureMemory memory = new GraphFailureMemory();
        GraphBlock.Need need = new GraphBlock.Tool("pickaxe", 1, "spirit-1");

        memory.note("mine_coal", "", need, 10);

        assertEquals(
                need,
                memory.recent("mine_coal", "", 10).orElseThrow().need(),
                "Нужда едет типом как есть"
                        + " Иначе тест note_TypedNeed_IsNotParsedFromText перестанет ловить эту поломку. При смене note_TypedNeed_IsNotParsedFromText проверь это утверждение.");
    }

    @Test
    void pathBlocks_RepeatedTarget_BlocksAfterThreshold() {
        GraphFailureMemory memory = new GraphFailureMemory();
        memory.note("chop_oak_log", "1,64,0", new GraphBlock.Path("1,64,0"), 10);

        assertTrue(
                memory.pathBlocks(10, 3000, 2).isEmpty(),
                "Один провал ещё не блокирует узел"
                        + " Иначе тест pathBlocks_RepeatedTarget_BlocksAfterThreshold перестанет ловить эту поломку. При смене pathBlocks_RepeatedTarget_BlocksAfterThreshold проверь это утверждение.");

        memory.note("chop_oak_log", "1,64,0", new GraphBlock.Path("1,64,0"), 11);

        List<GraphBlock> blocks = memory.pathBlocks(11, 3000, 2);
        assertEquals(
                1,
                blocks.size(),
                "Повтор той же недостижимой цели блокирует узел: " + blocks
                        + " Иначе тест pathBlocks_RepeatedTarget_BlocksAfterThreshold перестанет ловить эту поломку. При смене pathBlocks_RepeatedTarget_BlocksAfterThreshold проверь это утверждение.");
        assertEquals(
                "chop_oak_log",
                blocks.get(0).node(),
                "Блокировка адресована узлу"
                        + " Иначе тест pathBlocks_RepeatedTarget_BlocksAfterThreshold перестанет ловить эту поломку. При смене pathBlocks_RepeatedTarget_BlocksAfterThreshold проверь это утверждение.");
    }

    @Test
    void node_GraphTicket_LosesPrefixPartAndSpirit() {
        assertEquals(
                "make:dirt",
                GraphFailureMemory.node("graph:make:dirt:2", null),
                "Дубль части — тот же узел"
                        + " Иначе тест node_GraphTicket_LosesPrefixPartAndSpirit перестанет ловить эту поломку. При смене node_GraphTicket_LosesPrefixPartAndSpirit проверь это утверждение.");
        assertEquals(
                "make:dirt",
                GraphFailureMemory.node("graph:make:dirt", null),
                "Префикс графа снят"
                        + " Иначе тест node_GraphTicket_LosesPrefixPartAndSpirit перестанет ловить эту поломку. При смене node_GraphTicket_LosesPrefixPartAndSpirit проверь это утверждение.");
        assertEquals(
                "building:streets",
                GraphFailureMemory.node("graph:building:streets", "graph:building:streets"),
                "Постройка остаётся собой"
                        + " Иначе тест node_GraphTicket_LosesPrefixPartAndSpirit перестанет ловить эту поломку. При смене node_GraphTicket_LosesPrefixPartAndSpirit проверь это утверждение.");
        assertEquals(
                "mine_coal",
                GraphFailureMemory.node("graph:mine_coal@spirit-1", null),
                "Дух-исполнитель не часть узла"
                        + " Иначе тест node_GraphTicket_LosesPrefixPartAndSpirit перестанет ловить эту поломку. При смене node_GraphTicket_LosesPrefixPartAndSpirit проверь это утверждение.");
    }
}
