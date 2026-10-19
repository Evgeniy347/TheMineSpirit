package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TaskRunnerRestoreTests {
    private static final String CHECK = "seen";

    @Test
    void restore_SequenceAtSecondStep_RunsOnlyRemainingSteps() {
        List<String> created = new ArrayList<>();
        TaskNode plan = new SequenceNode(List.of(script("a"), script("b"), script("c")));
        ScriptRegistry registry = registry(
                factory("a", created, 1, ScriptStatus.SUCCEEDED, null),
                factory("b", created, 1, ScriptStatus.SUCCEEDED, null),
                factory("c", created, 1, ScriptStatus.SUCCEEDED, null));
        TaskRunner runner = TaskRunner.start(plan, registry, conditions());
        assertEquals(RunStatus.RUNNING, runner.tick(), "первый тик завершает шаг a и переводит sequence на шаг 1");
        assertEquals(List.of("a"), created, "до снимка исполнен ровно первый шаг");

        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions());
        assertEquals(RunStatus.SUCCEEDED, runToTerminal(resumed), "восстановленный sequence обязан дойти до конца");
        assertEquals(
                List.of("b", "c"),
                created,
                "после восстановления с шага 1 создаются только b и c: повтор a означал бы, что индекс шага "
                        + "потерян и дух повторит уже сделанное");
    }

    @Test
    void restore_RepeatAfterTwoPasses_RunsRemainingThree() {
        List<String> created = new ArrayList<>();
        TaskNode plan = new RepeatNode(5, script("step"));
        ScriptRegistry registry = registry(factory("step", created, 1, ScriptStatus.SUCCEEDED, null));
        TaskRunner runner = TaskRunner.start(plan, registry, conditions());
        assertEquals(RunStatus.RUNNING, runner.tick(), "первый повтор завершён");
        assertEquals(RunStatus.RUNNING, runner.tick(), "второй повтор завершён");

        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions());
        assertEquals(RunStatus.SUCCEEDED, runToTerminal(resumed), "repeat(5) обязан завершиться успехом");
        assertEquals(
                3,
                created.size(),
                "после двух пройденных повторов остаётся три: неверный индекс заставил бы духа выполнить лишние "
                        + "или недостающие итерации, создано " + created.size());
    }

    @Test
    void restore_FallbackOnSecondBranch_DoesNotReturnToFirst() {
        List<String> created = new ArrayList<>();
        TaskNode plan = new FallbackNode(List.of(script("first"), script("second")));
        ScriptRegistry registry = registry(
                factory("first", created, 1, ScriptStatus.FAILED, "первая ветка не смогла"),
                factory("second", created, 1, ScriptStatus.SUCCEEDED, null));
        TaskRunner runner = TaskRunner.start(plan, registry, conditions());
        assertEquals(RunStatus.RUNNING, runner.tick(), "провал первой ветки переводит fallback на вторую");

        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions());
        assertEquals(RunStatus.SUCCEEDED, runToTerminal(resumed), "вторая ветка обязана принести успех");
        assertEquals(
                List.of("second"),
                created,
                "после восстановления на второй ветке fallback не возвращается к первой: повтор первой ветки "
                        + "заново выполнил бы уже провалившееся действие");
    }

    @Test
    void restore_ParallelWithCompletedChild_FinishesOnlyRunningChild() {
        List<String> created = new ArrayList<>();
        TaskNode plan = new ParallelNode(ParallelMode.ALL, List.of(script("done"), script("slow")));
        ScriptRegistry registry = registry(
                factory("done", created, 1, ScriptStatus.SUCCEEDED, null),
                factory("slow", created, 3, ScriptStatus.SUCCEEDED, null));
        TaskRunner runner = TaskRunner.start(plan, registry, conditions());
        assertEquals(RunStatus.RUNNING, runner.tick(), "режим ALL ждёт медленный шаг, быстрый уже завершён");

        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions());
        assertEquals(RunStatus.SUCCEEDED, runToTerminal(resumed), "parallel(all) обязан завершиться успехом");
        assertEquals(
                List.of("slow"),
                created,
                "завершённый потомок parallel не перезапускается, доделывается только running: иначе дух заново "
                        + "сделает уже выполненный шаг");
    }

    @Test
    void restore_ConditionChosenBranch_EvaluatorNotCalledAgain() {
        AtomicInteger evaluations = new AtomicInteger();
        ConditionRegistry conditions = ConditionRegistry.of(Map.of(CHECK, params -> {
            evaluations.incrementAndGet();
            return true;
        }));
        List<String> created = new ArrayList<>();
        TaskNode plan = new ConditionNode(CHECK, Map.of(), script("thenStep"), script("elseStep"));
        ScriptRegistry registry = registry(
                factory("thenStep", created, 3, ScriptStatus.SUCCEEDED, null),
                factory("elseStep", created, 3, ScriptStatus.SUCCEEDED, null));
        TaskRunner runner = TaskRunner.start(plan, registry, conditions);
        assertEquals(RunStatus.RUNNING, runner.tick(), "первый тик выбирает ветку then и запускает её шаг");
        assertTrue(runner.snapshot().isPresent(), "работающий condition обязан отдавать снимок");

        evaluations.set(0);
        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions);
        assertEquals(RunStatus.SUCCEEDED, runToTerminal(resumed), "выбранная ветка обязана дойти до конца");
        assertEquals(
                0,
                evaluations.get(),
                "после восстановления condition не вычисляется второй раз: повторный расчёт мог бы увести "
                        + "в другую ветку и изменить уже принятое решение");
        assertEquals(List.of("thenStep"), created, "исполняется только уже выбранная ветка");
    }

    @Test
    void restore_RunningScript_ReceivesSavedStateThroughRestoreState() {
        List<Integer> restoredElapsed = new ArrayList<>();
        List<String> created = new ArrayList<>();
        ScriptRegistry registry = registry(named("stateful", params -> {
            created.add("stateful");
            return new StatefulInstance(restoredElapsed);
        }));
        TaskRunner runner = TaskRunner.start(script("stateful"), registry, conditions());
        assertEquals(RunStatus.RUNNING, runner.tick(), "скрипт в пять тиков ещё работает");
        assertEquals(RunStatus.RUNNING, runner.tick(), "скрипт в пять тиков ещё работает");

        restoredElapsed.clear();
        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions());
        assertEquals(
                RunStatus.SUCCEEDED,
                runToTerminal(resumed),
                "восстановленный скрипт обязан завершиться за оставшиеся три тика, а не начать пять заново");
        assertEquals(
                List.of(2),
                restoredElapsed,
                "состояние из saveState обязано дойти до экземпляра через restoreState: иначе счётный скрипт "
                        + "начнёт счёт заново. Получено " + restoredElapsed);
        assertEquals(
                List.of("stateful"),
                created,
                "восстановление обязано создать экземпляр скрипта ровно один раз: второй экземпляр начал бы "
                        + "работу с нуля рядом с восстановленным. Получено " + created);
    }

    @Test
    void restore_TerminalSnapshots_RunNothingAndKeepReason() {
        AtomicInteger creations = new AtomicInteger();
        ScriptRegistry counting = ScriptRegistry.of(Map.of("s", params -> {
            creations.incrementAndGet();
            return () -> ScriptStatus.SUCCEEDED;
        }));

        TaskRunner succeeded = TaskRunner.restore(terminal(RunStatus.SUCCEEDED, null), counting, conditions());
        assertEquals(RunStatus.SUCCEEDED, succeeded.status(), "восстановленный SUCCEEDED хранит итог");
        assertEquals(RunStatus.SUCCEEDED, succeeded.tick(), "SUCCEEDED не запускает ничего");

        TaskRunner failed = TaskRunner.restore(terminal(RunStatus.FAILED, "не дошёл до цели"), counting, conditions());
        assertEquals(RunStatus.FAILED, failed.status(), "восстановленный FAILED хранит итог");
        assertEquals(
                "не дошёл до цели",
                failed.failureReason().orElse(null),
                "причина провала сохраняется дословно: без неё отчёт о задаче теряет смысл");
        assertEquals(RunStatus.FAILED, failed.tick(), "FAILED не запускает ничего");

        TaskRunner cancelled = TaskRunner.restore(terminal(RunStatus.CANCELLED, null), counting, conditions());
        assertEquals(RunStatus.CANCELLED, cancelled.status(), "восстановленный CANCELLED хранит итог");
        assertEquals(RunStatus.CANCELLED, cancelled.tick(), "отменённая задача не оживает");

        assertEquals(0, creations.get(), "терминальный снимок не создаёт ни одного экземпляра скрипта");
    }

    @Test
    void restore_FailedSnapshotWithoutPlan_ReportsNoPlan() {
        TaskRunner failed =
                TaskRunner.restore(terminal(RunStatus.FAILED, "не удалось сохранить задачу"), registry(), conditions());

        assertEquals(
                Optional.empty(),
                failed.plan(),
                "Завершённый снимок не несёт план (так пишет и сохранение после сбоя): восстановленная задача "
                        + "обязана отвечать «плана нет», а не null — иначе запрос плана духа падает NPE");
    }

    private static TaskSnapshot terminal(RunStatus status, String reason) {
        return new TaskSnapshot(TaskSnapshot.VERSION, status, reason, null, null);
    }

    private static TaskRunner restore(TaskRunner runner, ScriptRegistry registry, ConditionRegistry conditions) {
        TaskSnapshot snapshot = runner.snapshot()
                .orElseThrow(() -> new AssertionError("работающая задача обязана отдавать снимок для восстановления"));
        TaskSnapshot decoded = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot));
        return TaskRunner.restore(decoded, registry, conditions);
    }

    private static RunStatus runToTerminal(TaskRunner runner) {
        for (int i = 0; i < 1000; i++) {
            RunStatus status = runner.tick();
            if (status != RunStatus.RUNNING) {
                return status;
            }
        }
        throw new AssertionError("план не завершился за 1000 тиков");
    }

    private static TaskNode script(String id) {
        return new ScriptCall(id, Map.of());
    }

    private static ScriptRegistry registry(NamedFactory... factories) {
        Map<String, ScriptFactory> byId = new LinkedHashMap<>();
        for (NamedFactory factory : factories) {
            byId.put(factory.id, factory);
        }
        return ScriptRegistry.of(byId);
    }

    private static NamedFactory factory(
            String id, List<String> created, int ticks, ScriptStatus terminal, String reason) {
        return named(id, params -> {
            created.add(id);
            return new CountingInstance(ticks, terminal, reason);
        });
    }

    private static NamedFactory named(String id, ScriptFactory delegate) {
        return new NamedFactory(id, delegate);
    }

    private static ConditionRegistry conditions() {
        return ConditionRegistry.of(Map.of(CHECK, params -> true));
    }

    private static final class NamedFactory implements ScriptFactory {
        private final String id;
        private final ScriptFactory delegate;

        NamedFactory(String id, ScriptFactory delegate) {
            this.id = id;
            this.delegate = delegate;
        }

        @Override
        public ScriptInstance create(Map<String, Object> params) {
            return delegate.create(params);
        }
    }

    private static final class CountingInstance implements ScriptInstance {
        private final int total;
        private final ScriptStatus terminal;
        private final String reason;
        private int elapsed;

        CountingInstance(int total, ScriptStatus terminal, String reason) {
            this.total = total;
            this.terminal = terminal;
            this.reason = reason;
        }

        @Override
        public ScriptStatus tick() {
            elapsed++;
            return elapsed >= total ? terminal : ScriptStatus.RUNNING;
        }

        @Override
        public String failureReason() {
            return reason;
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of("elapsed", elapsed);
        }

        @Override
        public void restoreState(Map<String, Object> state) {
            Object value = state.get("elapsed");
            if (value instanceof Number number) {
                elapsed = number.intValue();
            }
        }
    }

    private static final class StatefulInstance implements ScriptInstance {
        private final List<Integer> restoredElapsed;
        private int elapsed;

        StatefulInstance(List<Integer> restoredElapsed) {
            this.restoredElapsed = restoredElapsed;
        }

        @Override
        public ScriptStatus tick() {
            elapsed++;
            return elapsed >= 5 ? ScriptStatus.SUCCEEDED : ScriptStatus.RUNNING;
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of("elapsed", elapsed);
        }

        @Override
        public void restoreState(Map<String, Object> state) {
            Object value = state.get("elapsed");
            if (value instanceof Number number) {
                elapsed = number.intValue();
            }
            restoredElapsed.add(elapsed);
        }
    }
}
