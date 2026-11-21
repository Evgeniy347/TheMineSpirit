package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class More7RulesTests {
    @Test
    void phase_DayBoundaries_AreStable() {
        assertEquals(
                More7Rules.DayPhase.MORNING,
                More7Rules.phase(0),
                "Рассвет — утро Иначе тест phase_DayBoundaries_AreStable перестанет ловить эту поломку. При смене phase_DayBoundaries_AreStable проверь это утверждение.");
        assertEquals(
                More7Rules.DayPhase.MORNING,
                More7Rules.phase(3_999),
                "До конца утра — ещё утро Иначе тест phase_DayBoundaries_AreStable перестанет ловить эту поломку. При смене phase_DayBoundaries_AreStable проверь это утверждение.");
        assertEquals(
                More7Rules.DayPhase.DAY,
                More7Rules.phase(4_000),
                "После утра начинается день Иначе тест phase_DayBoundaries_AreStable перестанет ловить эту поломку. При смене phase_DayBoundaries_AreStable проверь это утверждение.");
        assertEquals(
                More7Rules.DayPhase.DAY,
                More7Rules.phase(9_999),
                "До вечера — день Иначе тест phase_DayBoundaries_AreStable перестанет ловить эту поломку. При смене phase_DayBoundaries_AreStable проверь это утверждение.");
        assertEquals(
                More7Rules.DayPhase.EVENING,
                More7Rules.phase(10_000),
                "С десяти тысяч — вечер Иначе тест phase_DayBoundaries_AreStable перестанет ловить эту поломку. При смене phase_DayBoundaries_AreStable проверь это утверждение.");
        assertEquals(
                More7Rules.DayPhase.EVENING,
                More7Rules.phase(13_999),
                "До ночи — ещё вечер Иначе тест phase_DayBoundaries_AreStable перестанет ловить эту поломку. При смене phase_DayBoundaries_AreStable проверь это утверждение.");
        assertEquals(
                More7Rules.DayPhase.NIGHT,
                More7Rules.phase(14_000),
                "С четырнадцати тысяч — ночь Иначе тест phase_DayBoundaries_AreStable перестанет ловить эту поломку. При смене phase_DayBoundaries_AreStable проверь это утверждение.");
        assertEquals(
                More7Rules.DayPhase.NIGHT,
                More7Rules.phase(-1),
                "Отрицательное время суток заворачивается Иначе тест phase_DayBoundaries_AreStable перестанет ловить эту поломку. При смене phase_DayBoundaries_AreStable проверь это утверждение.");
        assertEquals(
                More7Rules.DayPhase.MORNING,
                More7Rules.phase(48_000),
                "Полные сутки — снова утро Иначе тест phase_DayBoundaries_AreStable перестанет ловить эту поломку. При смене phase_DayBoundaries_AreStable проверь это утверждение.");
    }

    @Test
    void workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled() {
        More7Rules.Workload piled = More7Rules.workload(List.of(spirit("one", "mine"), spirit("two", "mine")), 3);
        assertTrue(
                piled.piled(),
                "Все духи на одной задаче, а нужды висят — задачу надо развести Иначе тест workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled перестанет ловить эту поломку. При смене workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled проверь это утверждение.");
        assertFalse(
                More7Rules.workload(List.of(spirit("one", "mine"), spirit("two", "farm")), 3)
                        .piled(),
                "Разные задачи уже разведены Иначе тест workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled перестанет ловить эту поломку. При смене workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled проверь это утверждение.");
        assertFalse(
                More7Rules.workload(List.of(spirit("one", "mine"), spirit("two", "mine")), 0)
                        .piled(),
                "Без висящих нужд делить нечего Иначе тест workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled перестанет ловить эту поломку. При смене workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled проверь это утверждение.");
        assertFalse(
                More7Rules.workload(List.of(spirit("one", "mine")), 3).piled(),
                "Один дух не может «все на одном» Иначе тест workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled перестанет ловить эту поломку. При смене workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled проверь это утверждение.");
        assertFalse(
                More7Rules.workload(List.of(spirit("one", null), spirit("two", " ")), 3)
                        .piled(),
                "Праздные духи не работают на одной задаче Иначе тест workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled перестанет ловить эту поломку. При смене workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled проверь это утверждение.");
    }

    @Test
    void workload_BusiestOverWorking_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new More7Rules.Workload(1, 2, 0),
                "На одной задаче не может быть больше духов, чем работает Иначе тест workload_BusiestOverWorking_Rejected перестанет ловить эту поломку. При смене workload_BusiestOverWorking_Rejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More7Rules.Workload(-1, 0, 0),
                "Отрицательное число работающих не имеет смысла Иначе тест workload_BusiestOverWorking_Rejected перестанет ловить эту поломку. При смене workload_BusiestOverWorking_Rejected проверь это утверждение.");
    }

    @Test
    void capacity_AtOrOverLimit_IsFull() {
        assertTrue(
                new More7Rules.Capacity(10, 10).full(),
                "Поголовье на пределе — загон полон Иначе тест capacity_AtOrOverLimit_IsFull перестанет ловить эту поломку. При смене capacity_AtOrOverLimit_IsFull проверь это утверждение.");
        assertTrue(
                new More7Rules.Capacity(11, 10).full(),
                "Переполненный загон тоже полон Иначе тест capacity_AtOrOverLimit_IsFull перестанет ловить эту поломку. При смене capacity_AtOrOverLimit_IsFull проверь это утверждение.");
        assertFalse(
                new More7Rules.Capacity(9, 10).full(),
                "Есть место — загон не полон Иначе тест capacity_AtOrOverLimit_IsFull перестанет ловить эту поломку. При смене capacity_AtOrOverLimit_IsFull проверь это утверждение.");
        assertFalse(
                new More7Rules.Capacity(0, 0).full(),
                "Без загона заполненности нет Иначе тест capacity_AtOrOverLimit_IsFull перестанет ловить эту поломку. При смене capacity_AtOrOverLimit_IsFull проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More7Rules.Capacity(-1, 10),
                "Отрицательное поголовье не имеет смысла Иначе тест capacity_AtOrOverLimit_IsFull перестанет ловить эту поломку. При смене capacity_AtOrOverLimit_IsFull проверь это утверждение.");
    }

    private static GraphFacts.Spirit spirit(String id, String activity) {
        return new GraphFacts.Spirit(
                id,
                new GraphFacts.Position(0, 64, 0),
                20,
                Math.max(20, 1f),
                null,
                20,
                300,
                false,
                false,
                false,
                activity,
                Map.of(),
                List.of(),
                List.of(),
                null);
    }
}
