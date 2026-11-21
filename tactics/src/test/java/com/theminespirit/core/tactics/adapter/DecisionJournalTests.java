package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TacticsEngine;
import com.theminespirit.core.tactics.TacticsMemory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DecisionJournalTests {
    private static final String SPIRIT_ID = "6f1c2d3e-0000-4000-8000-000000000001";
    private static final String HUSK = "minecraft:husk";
    private static final DecisionOrder ORDER =
            DecisionOrder.of(CombatMode.AGGRESSIVE, false, Optional.empty(), Optional.empty());

    private static final class Run {
        private final TacticsEngine engine = new TacticsEngine(TacticsConfig.loadDefault());
        private TacticsMemory memory;

        Run(Situation first) {
            this.memory =
                    TacticsMemory.initial(first.orders().mode(), 0, first.self().hpFraction());
        }

        TacticalOutcome step(Situation situation, long tick) {
            TacticalOutcome outcome = engine.step(situation, memory, tick);
            memory = outcome.memory();
            return outcome;
        }
    }

    private static DecisionObservation observe(Run run, List<EnemyFact> enemies, long tick) {
        return observe(run, enemies, tick, 0);
    }

    private static DecisionObservation observe(Run run, List<EnemyFact> enemies, long tick, double armor) {
        Situation situation = AdapterFixtures.collect(enemies, armor);
        return new DecisionObservation(
                tick,
                SPIRIT_ID,
                "spirit",
                run.step(situation, tick),
                Optional.empty(),
                ORDER,
                DecisionFacts.of(situation));
    }

    private static List<EnemyFact> husks(int count, double from) {
        return AdapterFixtures.many(HUSK, count, from);
    }

    @Test
    void offer_FirstEvaluation_SetsBaselineWithoutRecord() {
        DecisionJournal journal =
                new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());

        Optional<DecisionRecord> record =
                journal.offer(observe(new Run(AdapterFixtures.collect(List.of())), List.of(), 0));

        assertTrue(
                record.isEmpty(),
                "Первая оценка не с чем сравнивать: журнал принимает её за основу и молчит, иначе каждая загрузка "
                        + "духа начинается с записи «было ничего» При смене offer_FirstEvaluation_SetsBaselineWithoutRecord проверь это утверждение.");
        assertEquals(
                List.of(),
                journal.last(5),
                "Без смены решений журнал пуст Иначе тест offer_FirstEvaluation_SetsBaselineWithoutRecord перестанет ловить эту поломку. При смене offer_FirstEvaluation_SetsBaselineWithoutRecord проверь это утверждение.");
    }

    @Test
    void offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts() {
        DecisionJournal journal =
                new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());
        Run run = new Run(AdapterFixtures.collect(List.of(), 6));

        assertTrue(
                journal.offer(observe(run, List.of(), 0)).isEmpty(),
                "Без врагов дух спокоен: основу журнала задаёт Иначе тест offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts перестанет ловить эту поломку. При смене offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts проверь это утверждение.");
        Optional<DecisionRecord> record = journal.offer(observe(run, husks(1, 5), 100, 6));

        assertTrue(
                record.isPresent(),
                "Хаск подошёл в 5 блоках к бронированному духу: решение сменилось с IGNORE "
                        + "на FIGHT, журнал обязан записать, почему Иначе тест offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts перестанет ловить эту поломку. При смене offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts проверь это утверждение.");
        DecisionRecord entry = record.get();
        assertEquals(
                DecisionSnapshot.DECISION,
                entry.changed(),
                "Сменилось решение: бой, отход или игнор Иначе тест offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts перестанет ловить эту поломку. При смене offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts проверь это утверждение.");
        assertEquals(
                TacticalOption.IGNORE.name(),
                entry.before(),
                "До хаска дух игнорировал врагов Иначе тест offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts перестанет ловить эту поломку. При смене offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts проверь это утверждение.");
        assertEquals(
                TacticalOption.FIGHT.name(),
                entry.after(),
                "С хаском дух дерется Иначе тест offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts перестанет ловить эту поломку. При смене offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts проверь это утверждение.");
        assertFalse(
                entry.rule().isBlank(),
                "Запись называет сработавшее правило или оценку: по ней разбирают причину Иначе тест offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts перестанет ловить эту поломку. При смене offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts проверь это утверждение.");
        assertEquals(
                1,
                entry.facts().enemies(),
                "В ключевых фактах один враг рядом Иначе тест offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts перестанет ловить эту поломку. При смене offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts проверь это утверждение.");
        assertEquals(
                "minecraft:husk в 5.0",
                entry.facts().nearest(),
                "Ближайший враг назван типом и дистанцией Иначе тест offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts перестанет ловить эту поломку. При смене offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts проверь это утверждение.");
        assertEquals(
                20.0,
                entry.facts().hp(),
                "Здоровье духа в фактах записи Иначе тест offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts перестанет ловить эту поломку. При смене offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts проверь это утверждение.");
        assertEquals(
                6.0,
                entry.facts().armor(),
                "Броня из SelfFacts попадает в факты: сборка железа меняет решение, и без неё запись неотличима Иначе тест offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts перестанет ловить эту поломку. При смене offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts проверь это утверждение.");
    }

    @Test
    void offer_SameEvaluationRepeated_NoSecondRecord() {
        DecisionJournal journal =
                new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());
        Run run = new Run(AdapterFixtures.collect(List.of()));

        assertTrue(
                journal.offer(observe(run, List.of(), 0)).isEmpty(),
                "Первый вызов задаёт основу Иначе тест offer_SameEvaluationRepeated_NoSecondRecord перестанет ловить эту поломку. При смене offer_SameEvaluationRepeated_NoSecondRecord проверь это утверждение.");
        assertTrue(
                journal.offer(observe(run, husks(1, 5), 10)).isPresent(),
                "Хаск появился на тике 10: смена решения обязана дать запись Иначе тест offer_SameEvaluationRepeated_NoSecondRecord перестанет ловить эту поломку. При смене offer_SameEvaluationRepeated_NoSecondRecord проверь это утверждение.");

        for (long tick = 20; tick <= 200; tick += 10) {
            assertTrue(
                    journal.offer(observe(run, husks(1, 5), tick)).isEmpty(),
                    "Тик " + tick
                            + ": враг тот же и решение то же — журнал молчит, иначе он пишет каждый тик При смене offer_SameEvaluationRepeated_NoSecondRecord проверь это утверждение.");
        }

        assertEquals(
                1,
                journal.last(5).size(),
                "Смена была одна: в журнале ровно одна запись Иначе тест offer_SameEvaluationRepeated_NoSecondRecord перестанет ловить эту поломку. При смене offer_SameEvaluationRepeated_NoSecondRecord проверь это утверждение.");
    }

    @Test
    void offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses() {
        int maxPerSecond = TacticsAdapterSettings.defaults().journalMaxPerSecond();
        DecisionJournal journal = new DecisionJournal(8, maxPerSecond);
        assertEquals(
                DecisionJournal.TICKS_PER_SECOND,
                journal.minGapTicks(),
                "Умолчание adapter.json — одна запись в секунду, значит зазор " + DecisionJournal.TICKS_PER_SECOND
                        + " тиков: журнал не превращается в поток строк на каждого духа Иначе тест offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses перестанет ловить эту поломку. При смене offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses проверь это утверждение.");
        Run run = new Run(AdapterFixtures.collect(List.of()));

        assertTrue(
                journal.offer(observe(run, List.of(), 0)).isEmpty(),
                "Первая оценка задаёт основу Иначе тест offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses перестанет ловить эту поломку. При смене offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses проверь это утверждение.");
        assertTrue(
                journal.offer(observe(run, husks(1, 5), 10)).isPresent(),
                "Смена на тике 10 записана Иначе тест offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses перестанет ловить эту поломку. При смене offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses проверь это утверждение.");

        Optional<DecisionRecord> tooSoon = journal.offer(observe(run, List.of(), 10 + journal.minGapTicks() - 1));

        assertTrue(
                tooSoon.isEmpty(),
                "Смена через " + (journal.minGapTicks() - 1) + " тиков после записи: предел " + "частоты "
                        + maxPerSecond
                        + " запись в секунду её пропускает Иначе тест offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses перестанет ловить эту поломку. При смене offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses проверь это утверждение.");
        Optional<DecisionRecord> afterGap = journal.offer(observe(run, List.of(), 10 + journal.minGapTicks()));

        assertTrue(
                afterGap.isPresent(),
                "Та же смена после зазора записывается: предел частоты задерживает, "
                        + "а не теряет решение навсегда Иначе тест offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses перестанет ловить эту поломку. При смене offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses проверь это утверждение.");
        assertEquals(
                TacticalOption.FIGHT.name(),
                afterGap.get().before(),
                "Запись после зазора показывает состояние последней записи, а не пропущенную середину Иначе тест offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses перестанет ловить эту поломку. При смене offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses проверь это утверждение.");
    }
}
