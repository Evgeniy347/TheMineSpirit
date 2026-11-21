package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.ConfigStamp;
import com.theminespirit.core.reload.api.TableHolder;
import com.theminespirit.core.reload.api.TableStatus;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TacticsEngine;
import com.theminespirit.core.tactics.TacticsMemory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DecisionJournalPart2Tests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
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

    private static DecisionJournal journal() {
        return new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());
    }

    @Test
    void offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights() {
        DecisionJournal journal =
                new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());
        Run run = new Run(AdapterFixtures.collect(List.of()));

        assertTrue(
                journal.offer(observe(run, List.of(), 0)).isEmpty(),
                "Первая оценка задаёт основу Иначе тест offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights перестанет ловить эту поломку. При смене offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights проверь это утверждение.");
        Optional<DecisionRecord> record = journal.offer(observe(run, husks(6, 4), 100));

        assertTrue(
                record.isPresent(),
                "Шесть хасков при одном мече: решение сменилось, запись обязана быть Иначе тест offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights перестанет ловить эту поломку. При смене offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights проверь это утверждение.");
        DecisionRecord entry = record.get();
        assertEquals(
                TacticalOption.RETREAT.name(),
                entry.after(),
                "Шесть хасков: отход Иначе тест offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights перестанет ловить эту поломку. При смене offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights проверь это утверждение.");
        assertTrue(
                entry.byScore(),
                "Отход по оценке: в Trace правило 'score' Иначе тест offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights перестанет ловить эту поломку. При смене offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights проверь это утверждение.");
        assertFalse(
                entry.factors().isEmpty(),
                "Решение по оценке обязано назвать факторы с весами: по правилу "
                        + "причина была бы не видна Иначе тест offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights перестанет ловить эту поломку. При смене offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights проверь это утверждение.");
        assertTrue(
                entry.factors().size() <= DecisionReasons.TOP_FACTORS,
                "Факторов не больше " + DecisionReasons.TOP_FACTORS
                        + ": запись читают в чате и в логе Иначе тест offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights перестанет ловить эту поломку. При смене offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights проверь это утверждение.");
        assertTrue(
                entry.factors().get(0).weight()
                        >= entry.factors().get(entry.factors().size() - 1).weight(),
                "Факторы идут по убыванию веса: первый — главная причина Иначе тест offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights перестанет ловить эту поломку. При смене offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights проверь это утверждение.");
    }

    @Test
    void offer_RecordsBeyondCapacity_KeepsLastOnes() {
        DecisionJournal journal =
                new DecisionJournal(2, TacticsAdapterSettings.defaults().journalMaxPerSecond());
        Run run = new Run(AdapterFixtures.collect(List.of()));
        assertTrue(
                journal.offer(observe(run, List.of(), 0)).isEmpty(),
                "Первая оценка задаёт основу Иначе тест offer_RecordsBeyondCapacity_KeepsLastOnes перестанет ловить эту поломку. При смене offer_RecordsBeyondCapacity_KeepsLastOnes проверь это утверждение.");

        journal.offer(observe(run, husks(1, 5), 100));
        journal.offer(observe(run, List.of(), 200));
        journal.offer(observe(run, husks(1, 5), 300));

        assertEquals(
                2,
                journal.last(5).size(),
                "Кольцевой буфер держит последние записи, а не растёт без предела Иначе тест offer_RecordsBeyondCapacity_KeepsLastOnes перестанет ловить эту поломку. При смене offer_RecordsBeyondCapacity_KeepsLastOnes проверь это утверждение.");
        assertEquals(
                2,
                journal.capacity(),
                "Предел буфера приходит из данных adapter.json Иначе тест offer_RecordsBeyondCapacity_KeepsLastOnes перестанет ловить эту поломку. При смене offer_RecordsBeyondCapacity_KeepsLastOnes проверь это утверждение.");
        assertEquals(
                TacticalOption.FIGHT.name(),
                journal.last(5).get(1).before(),
                "Самая старая из уцелевших записей — FIGHT→IGNORE: первая (IGNORE→FIGHT) вытеснена буфером Иначе тест offer_RecordsBeyondCapacity_KeepsLastOnes перестанет ловить эту поломку. При смене offer_RecordsBeyondCapacity_KeepsLastOnes проверь это утверждение.");
        assertEquals(
                TacticalOption.IGNORE.name(),
                journal.last(5).get(1).after(),
                "Отход от боя к покою записан Иначе тест offer_RecordsBeyondCapacity_KeepsLastOnes перестанет ловить эту поломку. При смене offer_RecordsBeyondCapacity_KeepsLastOnes проверь это утверждение.");
    }

    @Test
    void offer_WithHash_RecordCarriesHash() {
        DecisionJournal journal = journal();
        Run run = new Run(AdapterFixtures.collect(List.of()));
        assertTrue(
                journal.offer(observe(run, List.of(), 0)).isEmpty(),
                "Первая оценка задаёт основу Иначе тест offer_WithHash_RecordCarriesHash перестанет ловить эту поломку. При смене offer_WithHash_RecordCarriesHash проверь это утверждение.");

        Optional<DecisionRecord> record = journal.offer(observe(run, husks(1, 5), 100, 6), "hash-1");

        assertEquals(
                "hash-1",
                record.orElseThrow().configHash(),
                "Запись журнала решений несёт хэш конфигурации, по которой принято решение: без него "
                        + "нельзя отличить решения до и после /reload Иначе тест offer_WithHash_RecordCarriesHash перестанет ловить эту поломку. При смене offer_WithHash_RecordCarriesHash проверь это утверждение.");
    }

    @Test
    void offer_AfterTableReplace_NewRecordsCarryNewHashOldStay() {
        TableHolder<String> holder = new TableHolder<>("board", "v1", TableStatus.Source.MOD, "mod", "hash-old", CLOCK);
        DecisionJournal journal = journal();
        Run run = new Run(AdapterFixtures.collect(List.of()));
        assertTrue(
                journal.offer(observe(run, List.of(), 0)).isEmpty(),
                "Первая оценка задаёт основу Иначе тест offer_AfterTableReplace_NewRecordsCarryNewHashOldStay перестанет ловить эту поломку. При смене offer_AfterTableReplace_NewRecordsCarryNewHashOldStay проверь это утверждение.");
        Optional<DecisionRecord> first = journal.offer(observe(run, husks(1, 5), 100, 6), ConfigStamp.of(holder));
        assertEquals(
                "hash-old",
                first.orElseThrow().configHash(),
                "Запись до замены несёт старый хэш Иначе тест offer_AfterTableReplace_NewRecordsCarryNewHashOldStay перестанет ловить эту поломку. При смене offer_AfterTableReplace_NewRecordsCarryNewHashOldStay проверь это утверждение.");

        holder.replace("v2", TableStatus.Source.DATAPACK, "world/datapacks/p/data/board.json", "hash-new", CLOCK);
        Optional<DecisionRecord> second = journal.offer(observe(run, List.of(), 200), ConfigStamp.of(holder));

        assertEquals(
                "hash-new",
                second.orElseThrow().configHash(),
                "После замены таблицы новые записи несут новый хэш: /reload сменил конфигурацию Иначе тест offer_AfterTableReplace_NewRecordsCarryNewHashOldStay перестанет ловить эту поломку. При смене offer_AfterTableReplace_NewRecordsCarryNewHashOldStay проверь это утверждение.");
        assertEquals(
                "hash-old",
                journal.last(5).get(1).configHash(),
                "Старая запись не переписывается: переписывание исказило бы журнал прошлого Иначе тест offer_AfterTableReplace_NewRecordsCarryNewHashOldStay перестанет ловить эту поломку. При смене offer_AfterTableReplace_NewRecordsCarryNewHashOldStay проверь это утверждение.");
    }

    @Test
    void offer_NullHash_Rejected() {
        DecisionJournal journal = journal();
        Run run = new Run(AdapterFixtures.collect(List.of()));
        assertTrue(
                journal.offer(observe(run, List.of(), 0)).isEmpty(),
                "Первая оценка задаёт основу Иначе тест offer_NullHash_Rejected перестанет ловить эту поломку. При смене offer_NullHash_Rejected проверь это утверждение.");

        assertThrows(
                NullPointerException.class,
                () -> journal.offer(observe(run, husks(1, 5), 100, 6), null),
                "Хэш null обязан отвергаться: запись без версии конфигурации несравнима со временем Иначе тест offer_NullHash_Rejected перестанет ловить эту поломку. При смене offer_NullHash_Rejected проверь это утверждение.");
    }

    @Test
    void last_LimitAboveRecords_ReturnsAllNewestFirst() {
        DecisionJournal journal =
                new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());
        Run run = new Run(AdapterFixtures.collect(List.of()));
        assertTrue(
                journal.offer(observe(run, List.of(), 0)).isEmpty(),
                "Первая оценка задаёт основу Иначе тест last_LimitAboveRecords_ReturnsAllNewestFirst перестанет ловить эту поломку. При смене last_LimitAboveRecords_ReturnsAllNewestFirst проверь это утверждение.");
        journal.offer(observe(run, husks(1, 5), 100));
        journal.offer(observe(run, List.of(), 200));

        List<DecisionRecord> records = journal.last(5);

        assertEquals(
                2,
                records.size(),
                "Просят пять, а записей две: отдаём обе, а не пустоту Иначе тест last_LimitAboveRecords_ReturnsAllNewestFirst перестанет ловить эту поломку. При смене last_LimitAboveRecords_ReturnsAllNewestFirst проверь это утверждение.");
        assertTrue(
                records.get(0).tick() > records.get(1).tick(),
                "Свежая запись идёт первой: её cause ищут в чате Иначе тест last_LimitAboveRecords_ReturnsAllNewestFirst перестанет ловить эту поломку. При смене last_LimitAboveRecords_ReturnsAllNewestFirst проверь это утверждение.");
    }

    @Test
    void constructor_ZeroCapacityOrZeroPerSecond_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DecisionJournal(0, 1),
                "Журнал без записи не показывает ничего: предел обязан быть хотя бы одна запись Иначе тест constructor_ZeroCapacityOrZeroPerSecond_IsRejected перестанет ловить эту поломку. При смене constructor_ZeroCapacityOrZeroPerSecond_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new DecisionJournal(8, 0),
                "Предел «ноль записей в секунду» запрещает журнал целиком: число приходит из данных и проверяется Иначе тест constructor_ZeroCapacityOrZeroPerSecond_IsRejected перестанет ловить эту поломку. При смене constructor_ZeroCapacityOrZeroPerSecond_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new DecisionJournal(8, DecisionJournal.TICKS_PER_SECOND + 1),
                "Чаще чем раз в тик запись не нужна: предел частности ограничен  Иначе тест constructor_ZeroCapacityOrZeroPerSecond_IsRejected перестанет ловить эту поломку. При смене constructor_ZeroCapacityOrZeroPerSecond_IsRejected проверь это утверждение."
                        + DecisionJournal.TICKS_PER_SECOND);
        assertThrows(
                NullPointerException.class,
                () -> journal().offer(null),
                "Журнал без наблюдения не может решить, было ли изменение, и молча падать не должен Иначе тест constructor_ZeroCapacityOrZeroPerSecond_IsRejected перестанет ловить эту поломку. При смене constructor_ZeroCapacityOrZeroPerSecond_IsRejected проверь это утверждение.");
    }
}
