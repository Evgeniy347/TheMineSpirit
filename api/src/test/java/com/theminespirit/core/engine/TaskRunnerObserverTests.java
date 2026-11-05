package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TaskRunnerObserverTests {
    private static final String WATCH = "watch";
    private static final String MOVE = "move";
    private static final String CHECK = "yes";

    private static ScriptRegistry registry() {
        return ScriptRegistry.of(Map.of(
                WATCH, TestScripts.observing(3, true),
                MOVE, TestScripts.observing(3, false)));
    }

    @Test
    void observesWhileTacticsOwns_ScriptFlag_PropagatesToRunner() {
        TaskRunner observing =
                TaskRunner.start(new ScriptCall(WATCH, Map.of()), registry(), TestScripts.condition(CHECK, true));
        TaskRunner moving =
                TaskRunner.start(new ScriptCall(MOVE, Map.of()), registry(), TestScripts.condition(CHECK, true));

        observing.tick();
        moving.tick();

        assertTrue(
                observing.observesWhileTacticsOwns(),
                "скрипт-наблюдатель обязан сообщать это через раннер: иначе тактика заморозит его и он не увидит мир");
        assertFalse(
                moving.observesWhileTacticsOwns(),
                "обычный скрипт не наблюдатель: под управлением тактики он обязан оставаться приостановленным");
    }

    @Test
    void observesWhileTacticsOwns_BeforeFirstTick_ReportsFromPlannedScript() {
        TaskRunner runner =
                TaskRunner.start(new ScriptCall(WATCH, Map.of()), registry(), TestScripts.condition(CHECK, true));

        assertTrue(
                runner.observesWhileTacticsOwns(),
                "флаг обязан быть известен до первого тика: иначе при уже взятой тактикой инициативе скрипт не начнёт наблюдаться");
    }

    @Test
    void observesWhileTacticsOwns_AfterTerminalStatus_ReportsFalse() {
        TaskRunner runner =
                TaskRunner.start(new ScriptCall(WATCH, Map.of()), registry(), TestScripts.condition(CHECK, true));
        runner.tick();
        runner.tick();
        runner.tick();

        assertFalse(
                runner.observesWhileTacticsOwns(),
                "завершённая задача не наблюдает: флаг обязан гаснуть вместе с задачей");
    }

    @Test
    void observesWhileTacticsOwns_BeforeFirstTick_DoesNotCreateScriptInstance() {
        AtomicInteger creations = new AtomicInteger();
        ScriptRegistry registry = ScriptRegistry.of(Map.of(WATCH, params -> {
            creations.incrementAndGet();
            return new ScriptInstance() {
                @Override
                public ScriptStatus tick() {
                    return ScriptStatus.SUCCEEDED;
                }
            };
        }));
        TaskRunner runner =
                TaskRunner.start(new ScriptCall(WATCH, Map.of()), registry, TestScripts.condition(CHECK, true));

        runner.observesWhileTacticsOwns();

        assertEquals(
                0,
                creations.get(),
                "флаг обязан читаться без конструирования скрипта: фабрика вызвана " + creations.get()
                        + " раз — конструирование имеет побочные эффекты и может упасть на невалидных параметрах");
    }

    @Test
    void observesWhileTacticsOwns_Sequence_ReportsCurrentStep() {
        TaskNode plan = new SequenceNode(List.of(new ScriptCall(WATCH, Map.of()), new ScriptCall(MOVE, Map.of())));
        TaskRunner runner = TaskRunner.start(plan, registry(), TestScripts.condition(CHECK, true));

        runner.tick();
        assertTrue(
                runner.observesWhileTacticsOwns(),
                "внутри последовательности флаг обязан отражать текущий шаг-наблюдатель");

        runner.tick();
        runner.tick();
        runner.tick();
        assertFalse(runner.observesWhileTacticsOwns(), "после перехода к шагу без наблюдения флаг обязан погаснуть");
    }

    @Test
    void observesWhileTacticsOwns_Parallel_RequiresEveryRunningStepToObserve() {
        TaskRunner allWatching = TaskRunner.start(
                new ParallelNode(
                        ParallelMode.ALL, List.of(new ScriptCall(WATCH, Map.of()), new ScriptCall(WATCH, Map.of()))),
                registry(),
                TestScripts.condition(CHECK, true));
        TaskRunner mixed = TaskRunner.start(
                new ParallelNode(
                        ParallelMode.ALL, List.of(new ScriptCall(WATCH, Map.of()), new ScriptCall(MOVE, Map.of()))),
                registry(),
                TestScripts.condition(CHECK, true));

        allWatching.tick();
        mixed.tick();

        assertTrue(
                allWatching.observesWhileTacticsOwns(),
                "параллель из наблюдателей обязана наблюдаться: иначе ветки заморозятся под тактикой");
        assertFalse(
                mixed.observesWhileTacticsOwns(),
                "если хоть одна ветка параллели не наблюдатель, вся параллель обязана считаться управляемой");
    }
}
