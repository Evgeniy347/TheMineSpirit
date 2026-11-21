package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TacticsEngine;
import com.theminespirit.core.tactics.TacticsMemory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DecisionTextTests {
    private static final String SPIRIT_ID = "6f1c2d3e-0000-4000-8000-000000000001";
    private static final String WARD = "ward-uuid";

    private static final class Run {
        private final TacticsEngine engine = new TacticsEngine(TacticsConfig.loadDefault());
        private TacticsMemory memory;

        TacticalOutcome step(Situation situation, long tick) {
            TacticsMemory base = memory == null
                    ? TacticsMemory.initial(
                            situation.orders().mode(), tick, situation.self().hpFraction())
                    : memory;
            TacticalOutcome outcome = engine.step(situation, base, tick);
            memory = outcome.memory();
            return outcome;
        }
    }

    private static DecisionObservation observe(Run run, List<EnemyFact> enemies, long tick, DecisionOrder order) {
        Situation situation = AdapterFixtures.collect(enemies);
        return new DecisionObservation(
                tick,
                SPIRIT_ID,
                "spirit",
                run.step(situation, tick),
                Optional.empty(),
                order,
                DecisionFacts.of(situation));
    }

    private static DecisionObservation observeTired(
            Run run, List<EnemyFact> enemies, double hpFraction, long tick, DecisionOrder order) {
        Situation situation = AdapterFixtures.tiredCollect(enemies, hpFraction, 0);
        return new DecisionObservation(
                tick,
                SPIRIT_ID,
                "spirit",
                run.step(situation, tick),
                Optional.empty(),
                order,
                DecisionFacts.of(situation));
    }

    private static DecisionOrder order(CombatMode mode) {
        return DecisionOrder.of(mode, false, Optional.empty(), Optional.empty());
    }

    private static DecisionRecord firstChange(
            Run run, DecisionJournal journal, List<EnemyFact> enemies, long tick, DecisionOrder order) {
        Optional<DecisionRecord> record = journal.offer(observe(run, enemies, tick, order));
        assertTrue(
                record.isPresent(),
                "Смена решения на тике " + tick + " обязана дать запись журнала"
                        + " Иначе тест механику перестанет ловить эту поломку. При смене механику проверь это утверждение.");
        return record.get();
    }

    @Test
    void format_RecordFromTrace_NamesChangeReasonAndFacts() {
        Run run = new Run();
        DecisionJournal journal = new DecisionJournal(8, DecisionJournal.TICKS_PER_SECOND);
        assertTrue(
                journal.offer(observe(run, List.of(), 0, order(CombatMode.AGGRESSIVE)))
                        .isEmpty(),
                "Первый вызов задаёт основу"
                        + " Иначе тест format_RecordFromTrace_NamesChangeReasonAndFacts перестанет ловить эту поломку. При смене format_RecordFromTrace_NamesChangeReasonAndFacts проверь это утверждение.");
        DecisionRecord record = firstChange(
                run, journal, AdapterFixtures.many("minecraft:husk", 1, 5), 100, order(CombatMode.AGGRESSIVE));

        String line = DecisionText.format(record);

        assertTrue(
                line.startsWith(DecisionText.LOG_PREFIX),
                "Строка журнала начинается с префикса " + DecisionText.LOG_PREFIX + ": по нему её ищут в логе "
                        + "сервера, получили: " + line
                        + " Иначе тест format_RecordFromTrace_NamesChangeReasonAndFacts перестанет ловить эту поломку. При смене format_RecordFromTrace_NamesChangeReasonAndFacts проверь это утверждение.");
        assertTrue(
                line.contains("тик 100"),
                "Запись называет тик решения: " + line
                        + " Иначе тест format_RecordFromTrace_NamesChangeReasonAndFacts перестанет ловить эту поломку. При смене format_RecordFromTrace_NamesChangeReasonAndFacts проверь это утверждение.");
        assertTrue(
                line.contains("дух spirit (" + SPIRIT_ID + ")"),
                "Запись называет духа именем и UUID: " + line
                        + " Иначе тест format_RecordFromTrace_NamesChangeReasonAndFacts перестанет ловить эту поломку. При смене format_RecordFromTrace_NamesChangeReasonAndFacts проверь это утверждение.");
        assertTrue(
                line.contains("решение IGNORE → FIGHT"),
                "Запись показывает, что было и что стало: " + line
                        + " Иначе тест format_RecordFromTrace_NamesChangeReasonAndFacts перестанет ловить эту поломку. При смене format_RecordFromTrace_NamesChangeReasonAndFacts проверь это утверждение.");
        assertTrue(
                line.contains("по оценке") || line.contains("по правилу "),
                "Запись называет причину: сработавшее правило или оценку: " + line
                        + " Иначе тест format_RecordFromTrace_NamesChangeReasonAndFacts перестанет ловить эту поломку. При смене format_RecordFromTrace_NamesChangeReasonAndFacts проверь это утверждение.");
        assertTrue(
                line.contains("врагов рядом 1 (ближайший minecraft:husk в 5.0)"),
                "Запись называет ключевые факты — врагов рядом и ближайшего: " + line
                        + " Иначе тест format_RecordFromTrace_NamesChangeReasonAndFacts перестанет ловить эту поломку. При смене format_RecordFromTrace_NamesChangeReasonAndFacts проверь это утверждение.");
        assertTrue(
                line.contains("здоровье 20/20"),
                "Запись называет здоровье духа: " + line
                        + " Иначе тест format_RecordFromTrace_NamesChangeReasonAndFacts перестанет ловить эту поломку. При смене format_RecordFromTrace_NamesChangeReasonAndFacts проверь это утверждение.");
        assertTrue(
                line.contains("броня 0"),
                "Запись называет броню духа: " + line
                        + " Иначе тест format_RecordFromTrace_NamesChangeReasonAndFacts перестанет ловить эту поломку. При смене format_RecordFromTrace_NamesChangeReasonAndFacts проверь это утверждение.");
        assertTrue(
                line.contains("бьёт: оружие ближнего боя"),
                "Запись называет, чем дух дерётся: " + line
                        + " Иначе тест format_RecordFromTrace_NamesChangeReasonAndFacts перестанет ловить эту поломку. При смене format_RecordFromTrace_NamesChangeReasonAndFacts проверь это утверждение.");
    }

    @Test
    void format_ScoreDecision_ListsFactorsWithWeights() {
        Run run = new Run();
        DecisionJournal journal = new DecisionJournal(8, DecisionJournal.TICKS_PER_SECOND);
        assertTrue(
                journal.offer(observe(run, List.of(), 0, order(CombatMode.AGGRESSIVE)))
                        .isEmpty(),
                "Первый вызов задаёт основу"
                        + " Иначе тест format_ScoreDecision_ListsFactorsWithWeights перестанет ловить эту поломку. При смене format_ScoreDecision_ListsFactorsWithWeights проверь это утверждение.");
        DecisionRecord record = firstChange(
                run, journal, AdapterFixtures.many("minecraft:husk", 6, 4), 100, order(CombatMode.AGGRESSIVE));

        String line = DecisionText.format(record);

        assertTrue(
                line.contains("по оценке"),
                "Отход по оценке обязан сказать «по оценке»: " + line
                        + " Иначе тест format_ScoreDecision_ListsFactorsWithWeights перестанет ловить эту поломку. При смене format_ScoreDecision_ListsFactorsWithWeights проверь это утверждение.");
        assertTrue(
                line.contains("факторы "),
                "Отход по оценке называет факторы с весами: " + line
                        + " Иначе тест format_ScoreDecision_ListsFactorsWithWeights перестанет ловить эту поломку. При смене format_ScoreDecision_ListsFactorsWithWeights проверь это утверждение.");
        assertTrue(
                line.matches(".*факторы .*[+-][0-9]+\\.[0-9]{2}.*"),
                "Вес фактора идёт со знаком и двумя знаками: решение по оценке без чисел не разобрать: " + line
                        + " Иначе тест format_ScoreDecision_ListsFactorsWithWeights перестанет ловить эту поломку. При смене format_ScoreDecision_ListsFactorsWithWeights проверь это утверждение.");
        assertFalse(
                line.contains("по правилу"),
                "По оценке правила нет: запись не выдумывает его: " + line
                        + " Иначе тест format_ScoreDecision_ListsFactorsWithWeights перестанет ловить эту поломку. При смене format_ScoreDecision_ListsFactorsWithWeights проверь это утверждение.");
    }

    @Test
    void format_ModeThenOrderChange_NamesBothFields() {
        Run run = new Run();
        DecisionJournal journal = new DecisionJournal(8, DecisionJournal.TICKS_PER_SECOND);
        assertTrue(
                journal.offer(observe(run, List.of(), 0, order(CombatMode.AGGRESSIVE)))
                        .isEmpty(),
                "Первый вызов задаёт основу"
                        + " Иначе тест format_ModeThenOrderChange_NamesBothFields перестанет ловить эту поломку. При смене format_ModeThenOrderChange_NamesBothFields проверь это утверждение.");

        Optional<DecisionRecord> mode =
                journal.offer(observeTired(run, List.of(), 0.28, 20, order(CombatMode.AGGRESSIVE)));
        assertTrue(
                mode.isPresent(),
                "Усталость при атакующем приказе сменяет режим: запись обязана быть"
                        + " Иначе тест format_ModeThenOrderChange_NamesBothFields перестанет ловить эту поломку. При смене format_ModeThenOrderChange_NamesBothFields проверь это утверждение.");
        assertEquals(
                "mode",
                mode.get().changed(),
                "Сменился режим боя: в записи поле режима, а не приказа"
                        + " Иначе тест format_ModeThenOrderChange_NamesBothFields перестанет ловить эту поломку. При смене format_ModeThenOrderChange_NamesBothFields проверь это утверждение.");
        assertTrue(
                DecisionText.format(mode.get()).contains("режим AGGRESSIVE → DEFENSIVE"),
                "Запись режима показывает, что было и что стало: " + DecisionText.format(mode.get())
                        + " Иначе тест format_ModeThenOrderChange_NamesBothFields перестанет ловить эту поломку. При смене format_ModeThenOrderChange_NamesBothFields проверь это утверждение.");

        Optional<DecisionRecord> coordinator = journal.offer(observeTired(
                run,
                List.of(),
                0.28,
                40,
                DecisionOrder.of(CombatMode.AGGRESSIVE, true, Optional.of(WARD), Optional.empty())));
        assertTrue(
                coordinator.isPresent(),
                "Зона и подопечный — приказ координатора: запись обязана быть"
                        + " Иначе тест format_ModeThenOrderChange_NamesBothFields перестанет ловить эту поломку. При смене format_ModeThenOrderChange_NamesBothFields проверь это утверждение.");
        assertEquals(
                "order",
                coordinator.get().changed(),
                "Зона и подопечный меняют приказ: в записи поле приказа"
                        + " Иначе тест format_ModeThenOrderChange_NamesBothFields перестанет ловить эту поломку. При смене format_ModeThenOrderChange_NamesBothFields проверь это утверждение.");
        String line = DecisionText.format(coordinator.get());
        assertTrue(
                line.contains("приказ AGGRESSIVE → AGGRESSIVE, зона охраны, подопечный " + WARD),
                "Приказ показан до и после со всеми частями: " + line
                        + " Иначе тест format_ModeThenOrderChange_NamesBothFields перестанет ловить эту поломку. При смене format_ModeThenOrderChange_NamesBothFields проверь это утверждение.");
    }

    @Test
    void fieldName_UnknownField_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DecisionText.fieldName("goal"),
                "Неизвестное поле записи — ошибка вызова: молчаливый подставившийся текст сделал бы строку "
                        + "журнала непонятной"
                        + " Иначе тест fieldName_UnknownField_IsRejected перестанет ловить эту поломку. При смене fieldName_UnknownField_IsRejected проверь это утверждение.");
    }
}
