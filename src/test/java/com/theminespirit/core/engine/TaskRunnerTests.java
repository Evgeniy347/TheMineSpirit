package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TaskRunnerTests {
    private static final String SCRIPT = "do";
    private static final String CHECK = "yes";

    @Test
    void tick_SequenceOfTwoScripts_RunsToSuccessInTwoTicks() {
        TaskNode plan = sequence(script(SCRIPT), script(SCRIPT));
        TaskRunner runner = TaskRunner.start(plan, TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(1)),
                TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.RUNNING, runner.tick(), "первый тик завершает первый шаг и оставляет узел в работе");
        assertEquals(RunStatus.SUCCEEDED, runner.tick(),
                "sequence обязан дойти до конца без провала и завершиться успехом");
    }

    @Test
    void tick_SequenceWithFirstFailure_StopsAtFirstFailureAndSkipsRest() {
        CountingFactory first = new CountingFactory(TestScripts.failing(1));
        CountingFactory second = new CountingFactory(TestScripts.succeeding(1));
        TaskNode plan = sequence(new ScriptCall("first", Map.of()), new ScriptCall("second", Map.of()));
        TaskRunner runner = TaskRunner.start(plan,
                ScriptRegistry.of(Map.of("first", first.asFactory(), "second", second.asFactory())),
                TestScripts.condition(CHECK, true));

        RunStatus status = runner.tick();

        assertEquals(RunStatus.FAILED, status,
                "провал шага sequence обязан проваливать весь узел: иначе дух продолжит план после неудачи");
        assertEquals(1, first.created, "проваленный первый шаг sequence исполняется ровно один раз");
        assertEquals(0, second.created, "после провала первого шага остальные шаги sequence не создаются");
    }

    @Test
    void tick_Repeat_ExecutesStepExactlyTimesAndCreatesInstanceEachIteration() {
        CountingFactory factory = new CountingFactory(TestScripts.succeeding(1));
        TaskRunner runner = TaskRunner.start(new RepeatNode(3, new ScriptCall(SCRIPT, Map.of())),
                ScriptRegistry.of(Map.of(SCRIPT, factory.asFactory())), TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.RUNNING, runner.tick(), "после первого повтора из трёх узел ещё работает");
        assertEquals(RunStatus.RUNNING, runner.tick(), "после второго повтора из трёх узел ещё работает");
        assertEquals(RunStatus.SUCCEEDED, runner.tick(),
                "repeat(3) обязан завершиться успехом ровно за три тика однотикового шага");
        assertEquals(3, factory.created,
                "repeat обязан создать экземпляр шага на каждую итерацию: повтор с общим экземпляром нарушает изоляцию");
    }

    @Test
    void tick_RepeatZeroTimes_SucceedsWithoutRunningStep() {
        CountingFactory factory = new CountingFactory(TestScripts.succeeding(1));
        TaskRunner runner = TaskRunner.start(new RepeatNode(0, new ScriptCall(SCRIPT, Map.of())),
                ScriptRegistry.of(Map.of(SCRIPT, factory.asFactory())), TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.SUCCEEDED, runner.tick(),
                "repeat с times <= 0 обязан успешно завершиться сразу: ноль повторов — это готовое действие");
        assertEquals(0, factory.created, "при times = 0 шаг не создаётся и не исполняется");
    }

    @Test
    void tick_Fallback_ReturnsResultOfFirstSuccessfulStep() {
        CountingFactory first = new CountingFactory(TestScripts.failing(1));
        CountingFactory second = new CountingFactory(TestScripts.succeeding(1));
        CountingFactory third = new CountingFactory(TestScripts.failing(1));
        TaskNode plan = new FallbackNode(List.of(
                new ScriptCall("first", Map.of()),
                new ScriptCall("second", Map.of()),
                new ScriptCall("third", Map.of())));
        TaskRunner runner = TaskRunner.start(plan, ScriptRegistry.of(Map.of(
                "first", first.asFactory(), "second", second.asFactory(), "third", third.asFactory())),
                TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.RUNNING, runner.tick(), "после провала первого шага fallback пробует следующий");
        assertEquals(RunStatus.SUCCEEDED, runner.tick(),
                "fallback обязан остановиться на первом успешном шаге, не исполняя остальные");
        assertEquals(1, first.created, "первый шаг fallback исполняется до провала");
        assertEquals(1, second.created, "второй шаг fallback исполняется и приносит успех");
        assertEquals(0, third.created, "после успеха fallback остальные шаги не создаются");
    }

    @Test
    void tick_FallbackAllStepsFail_ReturnsFailure() {
        TaskNode plan = new FallbackNode(List.of(script(SCRIPT), script(SCRIPT)));
        TaskRunner runner = TaskRunner.start(plan, TestScripts.singleRegistry(SCRIPT, TestScripts.failing(1)),
                TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.RUNNING, runner.tick(), "после провала первого шага fallback пробует следующий");
        assertEquals(RunStatus.FAILED, runner.tick(),
                "fallback обязан провалиться, когда провалились все ветки: тихий успех скрыл бы невыполнимую задачу");
        assertTrue(runner.failureReason().isPresent(),
                "провал обязан нести причину: иначе в отчёте задачи нельзя объяснить, почему цель не достигнута");
    }

    @Test
    void tick_ParallelAll_WaitsUntilEveryStepSucceeds() {
        TaskNode plan = new ParallelNode(ParallelMode.ALL, List.of(script(SCRIPT), script(SCRIPT)));
        ScriptRegistry registry = ScriptRegistry.of(Map.of(SCRIPT, TestScripts.succeeding(2)));
        TaskRunner runner = TaskRunner.start(plan, registry, TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.RUNNING, runner.tick(), "режим ALL ждёт, пока успешны все шаги");
        assertEquals(RunStatus.SUCCEEDED, runner.tick(),
                "режим ALL обязан завершиться успехом, когда успешны все шаги");
    }

    @Test
    void tick_ParallelAllAnyStepFails_ReturnsFailure() {
        TaskNode plan = new ParallelNode(ParallelMode.ALL, List.of(script(SCRIPT), script(SCRIPT)));
        ScriptRegistry registry = ScriptRegistry.of(Map.of(SCRIPT, TestScripts.failing(1)));
        TaskRunner runner = TaskRunner.start(plan, registry, TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.FAILED, runner.tick(),
                "режим ALL обязан провалиться, как только любой шаг провалился: иначе провал шага замалчивается");
    }

    @Test
    void tick_ParallelAnyOneStepSucceeds_ReturnsSuccess() {
        TaskNode plan = new ParallelNode(ParallelMode.ANY, List.of(script(SCRIPT), script(SCRIPT)));
        ScriptRegistry registry = ScriptRegistry.of(Map.of(
                SCRIPT, TestScripts.succeeding(1)));
        TaskRunner runner = TaskRunner.start(plan, registry, TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.SUCCEEDED, runner.tick(),
                "режим ANY обязан завершиться успехом, когда успешен любой шаг");
    }

    @Test
    void tick_ParallelAnyAllStepsFail_ReturnsFailure() {
        TaskNode plan = new ParallelNode(ParallelMode.ANY, List.of(script(SCRIPT), script(SCRIPT)));
        TaskRunner runner = TaskRunner.start(plan, TestScripts.singleRegistry(SCRIPT, TestScripts.failing(2)),
                TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.RUNNING, runner.tick(), "режим ANY ждёт, пока есть шанс на успех");
        assertEquals(RunStatus.FAILED, runner.tick(),
                "режим ANY обязан провалиться, когда провалились все шаги");
    }

    @Test
    void tick_ConditionCheckTrue_RunsThenBranch() {
        CountingFactory thenFactory = new CountingFactory(TestScripts.succeeding(1));
        CountingFactory elseFactory = new CountingFactory(TestScripts.succeeding(1));
        TaskNode plan = new ConditionNode(CHECK, Map.of(),
                new ScriptCall("then_script", Map.of()), new ScriptCall("else_script", Map.of()));
        TaskRunner runner = TaskRunner.start(plan, ScriptRegistry.of(Map.of(
                "then_script", thenFactory.asFactory(), "else_script", elseFactory.asFactory())),
                TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.SUCCEEDED, runner.tick(),
                "истинная проверка condition обязана исполнять ветку then");
        assertEquals(1, thenFactory.created, "истинная проверка обязана создать экземпляр ветки then");
        assertEquals(0, elseFactory.created, "истинная проверка не должна исполнять ветку else");
    }

    @Test
    void tick_ConditionCheckFalse_RunsElseBranch() {
        CountingFactory thenFactory = new CountingFactory(TestScripts.succeeding(1));
        CountingFactory elseFactory = new CountingFactory(TestScripts.succeeding(1));
        TaskNode plan = new ConditionNode(CHECK, Map.of(),
                new ScriptCall("then_script", Map.of()), new ScriptCall("else_script", Map.of()));
        TaskRunner runner = TaskRunner.start(plan, ScriptRegistry.of(Map.of(
                "then_script", thenFactory.asFactory(), "else_script", elseFactory.asFactory())),
                TestScripts.condition(CHECK, false));

        assertEquals(RunStatus.SUCCEEDED, runner.tick(),
                "ложная проверка condition обязана исполнять ветку else");
        assertEquals(0, thenFactory.created, "ложная проверка не должна исполнять ветку then");
        assertEquals(1, elseFactory.created, "ложная проверка обязана создать экземпляр ветки else");
    }

    @Test
    void tick_ConditionWithoutElseAndFalseCheck_SucceedsWithoutRunningThen() {
        CountingFactory factory = new CountingFactory(TestScripts.succeeding(1));
        TaskNode plan = new ConditionNode(CHECK, Map.of(), script(SCRIPT), null);
        TaskRunner runner = TaskRunner.start(plan, ScriptRegistry.of(Map.of(SCRIPT, factory.asFactory())),
                TestScripts.condition(CHECK, false));

        assertEquals(RunStatus.SUCCEEDED, runner.tick(),
                "condition без else при ложной проверке трактуется как успех: нечего делать — это не провал");
        assertEquals(0, factory.created, "при ложной проверке без else ветка then не исполняется");
    }

    @Test
    void tick_ConditionUnknownCheck_ReturnsFailureNamingCheck() {
        TaskNode plan = new ConditionNode("ghost_check", Map.of(), script(SCRIPT), null);
        TaskRunner runner = TaskRunner.start(plan, TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(1)),
                ConditionRegistry.of(Map.of()));

        RunStatus status = runner.tick();

        assertEquals(RunStatus.FAILED, status,
                "неизвестная проверка обязана проваливать узел: иначе условие молча выберет ветку наугад");
        assertTrue(runner.failureReason().orElse("").contains("ghost_check"),
                "причина обязана называть неизвестную проверку 'ghost_check'. Фактическое: "
                        + runner.failureReason().orElse(""));
    }

    @Test
    void tick_UnknownScript_ReturnsFailureWithoutThrowing() {
        TaskRunner runner = TaskRunner.start(script("ghost"), ScriptRegistry.of(Map.of()),
                TestScripts.condition(CHECK, true));

        RunStatus status = runner.tick();

        assertEquals(RunStatus.FAILED, status,
                "неизвестный скрипт обязан проваливать узел, а не бросать исключение наружу: движок не должен "
                        + "ронять серверный тик из-за плана");
        assertTrue(runner.failureReason().orElse("").contains("ghost"),
                "причина обязана называть неизвестный скрипт 'ghost'. Фактическое: "
                        + runner.failureReason().orElse(""));
    }

    @Test
    void cancel_RunningRun_TransitionsToCancelledAndFreezesStatus() {
        TaskRunner runner = TaskRunner.start(script(SCRIPT), TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(5)),
                TestScripts.condition(CHECK, true));
        assertEquals(RunStatus.RUNNING, runner.tick(), "перед отменой задача обязана быть в работе");

        runner.cancel();

        assertEquals(RunStatus.CANCELLED, runner.status(), "cancel обязан перевести задачу в CANCELLED");
        assertEquals(RunStatus.CANCELLED, runner.tick(), "после отмены tick не меняет статус");
        assertTrue(runner.currentScript().isEmpty(), "после отмены нет текущего скрипта");
    }

    @Test
    void tick_AfterTerminalStatus_DoesNotChangeStatus() {
        AtomicInteger creations = new AtomicInteger();
        ScriptFactory flip = params -> creations.incrementAndGet() == 1
                ? new SucceedingInstance()
                : new FailingInstance();
        TaskRunner runner = TaskRunner.start(script(SCRIPT), ScriptRegistry.of(Map.of(SCRIPT, flip)),
                TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.SUCCEEDED, runner.tick(), "первый тик доводит успешный скрипт до терминала");

        assertEquals(RunStatus.SUCCEEDED, runner.tick(),
                "после терминала tick обязан возвращать тот же статус: повторный tick не должен перезапускать скрипт");
        assertEquals(1, creations.get(),
                "после терминала движок не должен создавать новый экземпляр скрипта");
    }

    @Test
    void currentScript_WhileRunning_ReportsActiveScriptAndClearsAfterTerminal() {
        TaskRunner runner = TaskRunner.start(script(SCRIPT), TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(2)),
                TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.RUNNING, runner.tick(), "скрипт в два тика ещё работает");
        assertEquals(SCRIPT, runner.currentScript().orElse(null),
                "пока скрипт исполняется, currentScript обязан называть его id: по нему bridge показывает прогресс");

        assertEquals(RunStatus.SUCCEEDED, runner.tick(), "второй тик завершает скрипт");
        assertTrue(runner.currentScript().isEmpty(), "после завершения текущего скрипта нет");
    }

    @Test
    void start_NullArgument_IsRejected() {
        NullPointerException planError = assertThrows(NullPointerException.class,
                () -> TaskRunner.start(null, ScriptRegistry.of(Map.of()), ConditionRegistry.of(Map.of())),
                "start с null-планом обязан отказывать: без дерева задач исполнять нечего");
        assertTrue(planError.getMessage().contains("plan"),
                "отказ за null-план обязан называть поле 'plan': по сообщению видно, что именно не задано. "
                        + "Фактическое: " + planError.getMessage());
        assertThrows(NullPointerException.class,
                () -> TaskRunner.start(script(SCRIPT), null, ConditionRegistry.of(Map.of())),
                "start с null-реестром скриптов обязан отказывать: движку неоткуда взять реализации");
        assertThrows(NullPointerException.class,
                () -> TaskRunner.start(script(SCRIPT), ScriptRegistry.of(Map.of()), null),
                "start с null-реестром условий обязан отказывать: условие не сможет выбрать ветку");
    }

    @Test
    void failureReason_BeforeFailure_IsEmpty() {
        TaskRunner runner = TaskRunner.start(script(SCRIPT), TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(1)),
                TestScripts.condition(CHECK, true));

        assertTrue(runner.failureReason().isEmpty(),
                "до провала причина отсутствует: пустой Optional отличает 'нет ошибки' от 'ошибка без текста'");
    }

    @Test
    void script_FactoryReceivesParams_FromPlanNode() {
        Map<String, Object> params = Map.of("ticks", 4);
        TaskRunner runner = TaskRunner.start(new ScriptCall(SCRIPT, params),
                ScriptRegistry.of(Map.of(SCRIPT, received -> {
                    assertEquals(params, received, "фабрика обязана получить параметры узла без потерь");
                    return new SucceedingInstance();
                })), TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.SUCCEEDED, runner.tick(),
                "фабрика, проверившая параметры, возвращает мгновенно успешный экземпляр");
    }

    private static TaskNode script(String id) {
        return new ScriptCall(id, Map.of());
    }

    private static TaskNode sequence(TaskNode... steps) {
        return new SequenceNode(List.of(steps));
    }

    private static final class CountingFactory {
        private final ScriptFactory delegate;
        private int created;

        CountingFactory(ScriptFactory delegate) {
            this.delegate = delegate;
        }

        ScriptFactory asFactory() {
            return params -> {
                created++;
                return delegate.create(params);
            };
        }
    }

    private static final class SucceedingInstance implements ScriptInstance {
        @Override
        public ScriptStatus tick() {
            return ScriptStatus.SUCCEEDED;
        }
    }

    private static final class FailingInstance implements ScriptInstance {
        @Override
        public ScriptStatus tick() {
            return ScriptStatus.FAILED;
        }
    }
}
