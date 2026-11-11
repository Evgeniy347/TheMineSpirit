package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.reload.TableHolder;
import com.theminespirit.core.reload.TableStatus;
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

class DecisionJournalTests {
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

    @Test
    void offer_FirstEvaluation_SetsBaselineWithoutRecord() {
        DecisionJournal journal =
                new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());

        Optional<DecisionRecord> record =
                journal.offer(observe(new Run(AdapterFixtures.collect(List.of())), List.of(), 0));

        assertTrue(
                record.isEmpty(),
                "Первая оценка не с чем сравнивать: журнал принимает её за основу и молчит, иначе каждая загрузка "
                        + "духа начинается с записи «было ничего»");
        assertEquals(List.of(), journal.last(5), "Без смены решений журнал пуст");
    }

    @Test
    void offer_HuskAppearsAfterCalm_WritesIgnoreToFightWithRuleAndFacts() {
        DecisionJournal journal =
                new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());
        Run run = new Run(AdapterFixtures.collect(List.of(), 6));

        assertTrue(
                journal.offer(observe(run, List.of(), 0)).isEmpty(), "Без врагов дух спокоен: основу журнала задаёт");
        Optional<DecisionRecord> record = journal.offer(observe(run, husks(1, 5), 100, 6));

        assertTrue(
                record.isPresent(),
                "Хаск подошёл в 5 блоках к бронированному духу: решение сменилось с IGNORE "
                        + "на FIGHT, журнал обязан записать, почему");
        DecisionRecord entry = record.get();
        assertEquals(DecisionSnapshot.DECISION, entry.changed(), "Сменилось решение: бой, отход или игнор");
        assertEquals(TacticalOption.IGNORE.name(), entry.before(), "До хаска дух игнорировал врагов");
        assertEquals(TacticalOption.FIGHT.name(), entry.after(), "С хаском дух дерется");
        assertFalse(entry.rule().isBlank(), "Запись называет сработавшее правило или оценку: по ней разбирают причину");
        assertEquals(1, entry.facts().enemies(), "В ключевых фактах один враг рядом");
        assertEquals("minecraft:husk в 5.0", entry.facts().nearest(), "Ближайший враг назван типом и дистанцией");
        assertEquals(20.0, entry.facts().hp(), "Здоровье духа в фактах записи");
        assertEquals(
                6.0,
                entry.facts().armor(),
                "Броня из SelfFacts попадает в факты: сборка железа меняет решение, и без неё запись неотличима");
    }

    @Test
    void offer_SameEvaluationRepeated_NoSecondRecord() {
        DecisionJournal journal =
                new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());
        Run run = new Run(AdapterFixtures.collect(List.of()));

        assertTrue(journal.offer(observe(run, List.of(), 0)).isEmpty(), "Первый вызов задаёт основу");
        assertTrue(
                journal.offer(observe(run, husks(1, 5), 10)).isPresent(),
                "Хаск появился на тике 10: смена решения обязана дать запись");

        for (long tick = 20; tick <= 200; tick += 10) {
            assertTrue(
                    journal.offer(observe(run, husks(1, 5), tick)).isEmpty(),
                    "Тик " + tick + ": враг тот же и решение то же — журнал молчит, иначе он пишет каждый тик");
        }

        assertEquals(1, journal.last(5).size(), "Смена была одна: в журнале ровно одна запись");
    }

    @Test
    void offer_ChangesWithinGap_AreSkippedAndRecordedWhenGapPasses() {
        int maxPerSecond = TacticsAdapterSettings.defaults().journalMaxPerSecond();
        DecisionJournal journal = new DecisionJournal(8, maxPerSecond);
        assertEquals(
                DecisionJournal.TICKS_PER_SECOND,
                journal.minGapTicks(),
                "Умолчание adapter.json — одна запись в секунду, значит зазор " + DecisionJournal.TICKS_PER_SECOND
                        + " тиков: журнал не превращается в поток строк на каждого духа");
        Run run = new Run(AdapterFixtures.collect(List.of()));

        assertTrue(journal.offer(observe(run, List.of(), 0)).isEmpty(), "Первая оценка задаёт основу");
        assertTrue(journal.offer(observe(run, husks(1, 5), 10)).isPresent(), "Смена на тике 10 записана");

        Optional<DecisionRecord> tooSoon = journal.offer(observe(run, List.of(), 10 + journal.minGapTicks() - 1));

        assertTrue(
                tooSoon.isEmpty(),
                "Смена через " + (journal.minGapTicks() - 1) + " тиков после записи: предел " + "частоты "
                        + maxPerSecond + " запись в секунду её пропускает");
        Optional<DecisionRecord> afterGap = journal.offer(observe(run, List.of(), 10 + journal.minGapTicks()));

        assertTrue(
                afterGap.isPresent(),
                "Та же смена после зазора записывается: предел частоты задерживает, " + "а не теряет решение навсегда");
        assertEquals(
                TacticalOption.FIGHT.name(),
                afterGap.get().before(),
                "Запись после зазора показывает состояние последней записи, а не пропущенную середину");
    }

    @Test
    void offer_HordeOverwhelmsOnScore_RecordNamesTopFactorsWithWeights() {
        DecisionJournal journal =
                new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());
        Run run = new Run(AdapterFixtures.collect(List.of()));

        assertTrue(journal.offer(observe(run, List.of(), 0)).isEmpty(), "Первая оценка задаёт основу");
        Optional<DecisionRecord> record = journal.offer(observe(run, husks(6, 4), 100));

        assertTrue(record.isPresent(), "Шесть хасков при одном мече: решение сменилось, запись обязана быть");
        DecisionRecord entry = record.get();
        assertEquals(TacticalOption.RETREAT.name(), entry.after(), "Шесть хасков: отход");
        assertTrue(entry.byScore(), "Отход по оценке: в Trace правило 'score'");
        assertFalse(
                entry.factors().isEmpty(),
                "Решение по оценке обязано назвать факторы с весами: по правилу " + "причина была бы не видна");
        assertTrue(
                entry.factors().size() <= DecisionReasons.TOP_FACTORS,
                "Факторов не больше " + DecisionReasons.TOP_FACTORS + ": запись читают в чате и в логе");
        assertTrue(
                entry.factors().get(0).weight()
                        >= entry.factors().get(entry.factors().size() - 1).weight(),
                "Факторы идут по убыванию веса: первый — главная причина");
    }

    @Test
    void offer_RecordsBeyondCapacity_KeepsLastOnes() {
        DecisionJournal journal =
                new DecisionJournal(2, TacticsAdapterSettings.defaults().journalMaxPerSecond());
        Run run = new Run(AdapterFixtures.collect(List.of()));
        assertTrue(journal.offer(observe(run, List.of(), 0)).isEmpty(), "Первая оценка задаёт основу");

        journal.offer(observe(run, husks(1, 5), 100));
        journal.offer(observe(run, List.of(), 200));
        journal.offer(observe(run, husks(1, 5), 300));

        assertEquals(2, journal.last(5).size(), "Кольцевой буфер держит последние записи, а не растёт без предела");
        assertEquals(2, journal.capacity(), "Предел буфера приходит из данных adapter.json");
        assertEquals(
                TacticalOption.FIGHT.name(),
                journal.last(5).get(1).before(),
                "Самая старая из уцелевших записей — FIGHT→IGNORE: первая (IGNORE→FIGHT) вытеснена буфером");
        assertEquals(TacticalOption.IGNORE.name(), journal.last(5).get(1).after(), "Отход от боя к покою записан");
    }

    @Test
    void offer_WithHash_RecordCarriesHash() {
        DecisionJournal journal = journal();
        Run run = new Run(AdapterFixtures.collect(List.of()));
        assertTrue(journal.offer(observe(run, List.of(), 0)).isEmpty(), "Первая оценка задаёт основу");

        Optional<DecisionRecord> record = journal.offer(observe(run, husks(1, 5), 100, 6), "hash-1");

        assertEquals(
                "hash-1",
                record.orElseThrow().configHash(),
                "Запись журнала решений несёт хэш конфигурации, по которой принято решение: без него "
                        + "нельзя отличить решения до и после /reload");
    }

    @Test
    void offer_AfterTableReplace_NewRecordsCarryNewHashOldStay() {
        TableHolder<String> holder = new TableHolder<>("board", "v1", TableStatus.Source.MOD, "mod", "hash-old", CLOCK);
        DecisionJournal journal = journal();
        Run run = new Run(AdapterFixtures.collect(List.of()));
        assertTrue(journal.offer(observe(run, List.of(), 0)).isEmpty(), "Первая оценка задаёт основу");
        Optional<DecisionRecord> first = journal.offer(observe(run, husks(1, 5), 100, 6), ConfigStamp.of(holder));
        assertEquals("hash-old", first.orElseThrow().configHash(), "Запись до замены несёт старый хэш");

        holder.replace("v2", TableStatus.Source.DATAPACK, "world/datapacks/p/data/board.json", "hash-new", CLOCK);
        Optional<DecisionRecord> second = journal.offer(observe(run, List.of(), 200), ConfigStamp.of(holder));

        assertEquals(
                "hash-new",
                second.orElseThrow().configHash(),
                "После замены таблицы новые записи несут новый хэш: /reload сменил конфигурацию");
        assertEquals(
                "hash-old",
                journal.last(5).get(1).configHash(),
                "Старая запись не переписывается: переписывание исказило бы журнал прошлого");
    }

    @Test
    void offer_NullHash_Rejected() {
        DecisionJournal journal = journal();
        Run run = new Run(AdapterFixtures.collect(List.of()));
        assertTrue(journal.offer(observe(run, List.of(), 0)).isEmpty(), "Первая оценка задаёт основу");

        assertThrows(
                NullPointerException.class,
                () -> journal.offer(observe(run, husks(1, 5), 100, 6), null),
                "Хэш null обязан отвергаться: запись без версии конфигурации несравнима со временем");
    }

    @Test
    void last_LimitAboveRecords_ReturnsAllNewestFirst() {
        DecisionJournal journal =
                new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());
        Run run = new Run(AdapterFixtures.collect(List.of()));
        assertTrue(journal.offer(observe(run, List.of(), 0)).isEmpty(), "Первая оценка задаёт основу");
        journal.offer(observe(run, husks(1, 5), 100));
        journal.offer(observe(run, List.of(), 200));

        List<DecisionRecord> records = journal.last(5);

        assertEquals(2, records.size(), "Просят пять, а записей две: отдаём обе, а не пустоту");
        assertTrue(records.get(0).tick() > records.get(1).tick(), "Свежая запись идёт первой: её cause ищут в чате");
    }

    @Test
    void constructor_ZeroCapacityOrZeroPerSecond_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DecisionJournal(0, 1),
                "Журнал без записи не показывает ничего: предел обязан быть хотя бы одна запись");
        assertThrows(
                IllegalArgumentException.class,
                () -> new DecisionJournal(8, 0),
                "Предел «ноль записей в секунду» запрещает журнал целиком: число приходит из данных и проверяется");
        assertThrows(
                IllegalArgumentException.class,
                () -> new DecisionJournal(8, DecisionJournal.TICKS_PER_SECOND + 1),
                "Чаще чем раз в тик запись не нужна: предел частности ограничен " + DecisionJournal.TICKS_PER_SECOND);
        assertThrows(
                NullPointerException.class,
                () -> journal().offer(null),
                "Журнал без наблюдения не может решить, было ли изменение, и молча падать не должен");
    }

    private static DecisionJournal journal() {
        return new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());
    }
}
