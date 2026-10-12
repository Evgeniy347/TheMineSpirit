package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class TacticsScaleTests {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();

    private static Situation fullCrowd() {
        SituationBuilder builder = SituationBuilder.create().cover(6).home(30).blocks(10).ammo(5);
        for (int i = 0; i < Situation.MAX_ENEMIES; i++) {
            builder.enemy(i % 3 == 0 ? "skeleton" : "zombie", 4 + i);
        }
        return builder.build();
    }

    @Test
    void step_MaxEnemies_EvaluatesEachFactorOnceAndEachRuleAtMostOnce() {
        TacticsEngine engine = new TacticsEngine(CONFIG);
        Situation crowd = fullCrowd();

        engine.step(crowd, Memories.fresh(crowd), 0);

        assertEquals(Situation.MAX_ENEMIES, crowd.enemies().size(),
                "Проверка масштаба идёт на ситуации ровно с MAX_ENEMIES врагами");
        assertEquals(Factor.values().length, engine.scorer().factorEvaluations(),
                "Число вычислений факторов за вызов равно числу факторов, а не числу факторов × враги × варианты");
        assertTrue(engine.ruleCounter().checks() <= engine.totalRules(),
                "Обращений к таблицам правил за вызов не больше числа правил (" + engine.totalRules() + "), а было "
                        + engine.ruleCounter().checks() + ": правило проверяется не более одного раза");
    }

    @Test
    void step_RepeatedCalls_CostTheSameEveryTime() {
        TacticsEngine engine = new TacticsEngine(CONFIG);
        Situation crowd = fullCrowd();
        TacticsMemory memory = Memories.fresh(crowd);

        engine.step(crowd, memory, 0);
        long factorsAfterOne = engine.scorer().factorEvaluations();
        long checksAfterOne = engine.ruleCounter().checks();
        engine.step(crowd, memory, 10);

        assertEquals(List.of(2 * factorsAfterOne, 2 * checksAfterOne),
                List.of(engine.scorer().factorEvaluations(), engine.ruleCounter().checks()),
                "Стоимость вызова не растёт от вызова к вызову: нет накопления состояния внутри движка");
    }

    @Test
    void step_AfterDefaultLoad_NeverReadsResourcesAgain() {
        TacticsConfig config = TacticsConfig.loadDefault();
        int readsBefore = TacticsResources.reads();
        TacticsEngine engine = new TacticsEngine(config);
        Situation crowd = fullCrowd();

        for (int tick = 0; tick < 50; tick++) {
            engine.step(crowd, Memories.fresh(crowd), tick);
            TacticsConfig.loadDefault();
        }

        assertEquals(List.of(readsBefore, 1), List.of(TacticsResources.reads(), TacticsConfig.defaultLoads()),
                "После loadDefault() файлы тактики не читаются: чтение с диска в тике - дефект производительности, "
                        + "а конфигурация по умолчанию загружается один раз за процесс");
    }

    @Test
    void config_AllCollections_AreImmutableForSharedReading() {
        Rules rules = CONFIG.rules();

        assertThrows(UnsupportedOperationException.class, () -> rules.hard().add(rules.hard().get(0)),
                "Список жёстких правил неизменяем: конфигурация общая для всех духов и читается из многих потоков");
        assertThrows(UnsupportedOperationException.class, () -> rules.states().clear(),
                "Таблица состояний неизменяема");
        assertThrows(UnsupportedOperationException.class,
                () -> CONFIG.weights().matrix().get(Factor.BIAS).put(TacticalOption.FIGHT, 5.0),
                "Матрица весов неизменяема");
        assertThrows(UnsupportedOperationException.class, () -> CONFIG.enemies().byType().clear(),
                "Профили врагов неизменяемы");
        assertThrows(UnsupportedOperationException.class, () -> CONFIG.modes().params().clear(),
                "Параметры режимов неизменяемы");
    }

    @Test
    void step_ManyThreadsOnSharedEngine_GiveSameOutcomesAsSequentialRun() throws InterruptedException,
            ExecutionException {
        TacticsEngine engine = new TacticsEngine(CONFIG);
        Situation crowd = fullCrowd();
        TacticsMemory memory = Memories.fresh(crowd);
        TacticalOutcome expected = engine.step(crowd, memory, 5);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<TacticalOutcome>> results = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                results.add(pool.submit(() -> engine.step(crowd, memory, 5)));
            }
            for (Future<TacticalOutcome> result : results) {
                assertEquals(expected, result.get(),
                        "Чтение общей конфигурации из разных потоков не меняет результат: конфигурация неизменяема");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
