package com.theminespirit.core.engine;

import static com.theminespirit.core.engine.TaskRunnerRestoreTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TaskRunnerRestoreTests {
    @Test
    void restore_SequenceAtSecondStep_RunsOnlyRemainingSteps() {
        List<String> created = new ArrayList<>();
        TaskNode plan = new SequenceNode(List.of(script("a"), script("b"), script("c")));
        ScriptRegistry registry = registry(
                factory("a", created, 1, ScriptStatus.SUCCEEDED, null),
                factory("b", created, 1, ScriptStatus.SUCCEEDED, null),
                factory("c", created, 1, ScriptStatus.SUCCEEDED, null));
        TaskRunner runner = TaskRunner.start(plan, registry, conditions());
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "первый тик завершает шаг a и переводит sequence на шаг 1 Иначе тест restore_SequenceAtSecondStep_RunsOnlyRemainingSteps перестанет ловить эту поломку. При смене restore_SequenceAtSecondStep_RunsOnlyRemainingSteps проверь это утверждение.");
        assertEquals(
                List.of("a"),
                created,
                "до снимка исполнен ровно первый шаг Иначе тест restore_SequenceAtSecondStep_RunsOnlyRemainingSteps перестанет ловить эту поломку. При смене restore_SequenceAtSecondStep_RunsOnlyRemainingSteps проверь это утверждение.");

        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions());
        assertEquals(
                RunStatus.SUCCEEDED,
                runToTerminal(resumed),
                "восстановленный sequence обязан дойти до конца Иначе тест restore_SequenceAtSecondStep_RunsOnlyRemainingSteps перестанет ловить эту поломку. При смене restore_SequenceAtSecondStep_RunsOnlyRemainingSteps проверь это утверждение.");
        assertEquals(
                List.of("b", "c"),
                created,
                "после восстановления с шага 1 создаются только b и c: повтор a означал бы, что индекс шага "
                        + "потерян и дух повторит уже сделанное Иначе тест restore_SequenceAtSecondStep_RunsOnlyRemainingSteps перестанет ловить эту поломку. При смене restore_SequenceAtSecondStep_RunsOnlyRemainingSteps проверь это утверждение.");
    }

    @Test
    void restore_RepeatAfterTwoPasses_RunsRemainingThree() {
        List<String> created = new ArrayList<>();
        TaskNode plan = new RepeatNode(5, script("step"));
        ScriptRegistry registry = registry(factory("step", created, 1, ScriptStatus.SUCCEEDED, null));
        TaskRunner runner = TaskRunner.start(plan, registry, conditions());
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "первый повтор завершён Иначе тест restore_RepeatAfterTwoPasses_RunsRemainingThree перестанет ловить эту поломку. При смене restore_RepeatAfterTwoPasses_RunsRemainingThree проверь это утверждение.");
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "второй повтор завершён Иначе тест restore_RepeatAfterTwoPasses_RunsRemainingThree перестанет ловить эту поломку. При смене restore_RepeatAfterTwoPasses_RunsRemainingThree проверь это утверждение.");

        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions());
        assertEquals(
                RunStatus.SUCCEEDED,
                runToTerminal(resumed),
                "repeat(5) обязан завершиться успехом Иначе тест restore_RepeatAfterTwoPasses_RunsRemainingThree перестанет ловить эту поломку. При смене restore_RepeatAfterTwoPasses_RunsRemainingThree проверь это утверждение.");
        assertEquals(
                3,
                created.size(),
                "после двух пройденных повторов остаётся три: неверный индекс заставил бы духа выполнить лишние "
                        + "или недостающие итерации, создано  Иначе тест restore_RepeatAfterTwoPasses_RunsRemainingThree перестанет ловить эту поломку. При смене restore_RepeatAfterTwoPasses_RunsRemainingThree проверь это утверждение."
                        + created.size());
    }

    @Test
    void restore_FallbackOnSecondBranch_DoesNotReturnToFirst() {
        List<String> created = new ArrayList<>();
        TaskNode plan = new FallbackNode(List.of(script("first"), script("second")));
        ScriptRegistry registry = registry(
                factory("first", created, 1, ScriptStatus.FAILED, "первая ветка не смогла"),
                factory("second", created, 1, ScriptStatus.SUCCEEDED, null));
        TaskRunner runner = TaskRunner.start(plan, registry, conditions());
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "провал первой ветки переводит fallback на вторую Иначе тест restore_FallbackOnSecondBranch_DoesNotReturnToFirst перестанет ловить эту поломку. При смене restore_FallbackOnSecondBranch_DoesNotReturnToFirst проверь это утверждение.");

        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions());
        assertEquals(
                RunStatus.SUCCEEDED,
                runToTerminal(resumed),
                "вторая ветка обязана принести успех Иначе тест restore_FallbackOnSecondBranch_DoesNotReturnToFirst перестанет ловить эту поломку. При смене restore_FallbackOnSecondBranch_DoesNotReturnToFirst проверь это утверждение.");
        assertEquals(
                List.of("second"),
                created,
                "после восстановления на второй ветке fallback не возвращается к первой: повтор первой ветки "
                        + "заново выполнил бы уже провалившееся действие Иначе тест restore_FallbackOnSecondBranch_DoesNotReturnToFirst перестанет ловить эту поломку. При смене restore_FallbackOnSecondBranch_DoesNotReturnToFirst проверь это утверждение.");
    }

    @Test
    void restore_ParallelWithCompletedChild_FinishesOnlyRunningChild() {
        List<String> created = new ArrayList<>();
        TaskNode plan = new ParallelNode(ParallelMode.ALL, List.of(script("done"), script("slow")));
        ScriptRegistry registry = registry(
                factory("done", created, 1, ScriptStatus.SUCCEEDED, null),
                factory("slow", created, 3, ScriptStatus.SUCCEEDED, null));
        TaskRunner runner = TaskRunner.start(plan, registry, conditions());
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "режим ALL ждёт медленный шаг, быстрый уже завершён Иначе тест restore_ParallelWithCompletedChild_FinishesOnlyRunningChild перестанет ловить эту поломку. При смене restore_ParallelWithCompletedChild_FinishesOnlyRunningChild проверь это утверждение.");

        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions());
        assertEquals(
                RunStatus.SUCCEEDED,
                runToTerminal(resumed),
                "parallel(all) обязан завершиться успехом Иначе тест restore_ParallelWithCompletedChild_FinishesOnlyRunningChild перестанет ловить эту поломку. При смене restore_ParallelWithCompletedChild_FinishesOnlyRunningChild проверь это утверждение.");
        assertEquals(
                List.of("slow"),
                created,
                "завершённый потомок parallel не перезапускается, доделывается только running: иначе дух заново "
                        + "сделает уже выполненный шаг При смене restore_ParallelWithCompletedChild_FinishesOnlyRunningChild проверь это утверждение.");
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
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "первый тик выбирает ветку then и запускает её шаг Иначе тест restore_ConditionChosenBranch_EvaluatorNotCalledAgain перестанет ловить эту поломку. При смене restore_ConditionChosenBranch_EvaluatorNotCalledAgain проверь это утверждение.");
        assertTrue(
                runner.snapshot().isPresent(),
                "работающий condition обязан отдавать снимок Иначе тест restore_ConditionChosenBranch_EvaluatorNotCalledAgain перестанет ловить эту поломку. При смене restore_ConditionChosenBranch_EvaluatorNotCalledAgain проверь это утверждение.");

        evaluations.set(0);
        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions);
        assertEquals(
                RunStatus.SUCCEEDED,
                runToTerminal(resumed),
                "выбранная ветка обязана дойти до конца Иначе тест restore_ConditionChosenBranch_EvaluatorNotCalledAgain перестанет ловить эту поломку. При смене restore_ConditionChosenBranch_EvaluatorNotCalledAgain проверь это утверждение.");
        assertEquals(
                0,
                evaluations.get(),
                "после восстановления condition не вычисляется второй раз: повторный расчёт мог бы увести "
                        + "в другую ветку и изменить уже принятое решение Иначе тест restore_ConditionChosenBranch_EvaluatorNotCalledAgain перестанет ловить эту поломку. При смене restore_ConditionChosenBranch_EvaluatorNotCalledAgain проверь это утверждение.");
        assertEquals(
                List.of("thenStep"),
                created,
                "исполняется только уже выбранная ветка Иначе тест restore_ConditionChosenBranch_EvaluatorNotCalledAgain перестанет ловить эту поломку. При смене restore_ConditionChosenBranch_EvaluatorNotCalledAgain проверь это утверждение.");
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
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "скрипт в пять тиков ещё работает Иначе тест restore_RunningScript_ReceivesSavedStateThroughRestoreState перестанет ловить эту поломку. При смене restore_RunningScript_ReceivesSavedStateThroughRestoreState проверь это утверждение.");
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "скрипт в пять тиков ещё работает Иначе тест restore_RunningScript_ReceivesSavedStateThroughRestoreState перестанет ловить эту поломку. При смене restore_RunningScript_ReceivesSavedStateThroughRestoreState проверь это утверждение.");

        restoredElapsed.clear();
        created.clear();
        TaskRunner resumed = restore(runner, registry, conditions());
        assertEquals(
                RunStatus.SUCCEEDED,
                runToTerminal(resumed),
                "восстановленный скрипт обязан завершиться за оставшиеся три тика, а не начать пять заново Иначе тест restore_RunningScript_ReceivesSavedStateThroughRestoreState перестанет ловить эту поломку. При смене restore_RunningScript_ReceivesSavedStateThroughRestoreState проверь это утверждение.");
        assertEquals(
                List.of(2),
                restoredElapsed,
                "состояние из saveState обязано дойти до экземпляра через restoreState: иначе счётный скрипт "
                        + "начнёт счёт заново. Получено  При смене restore_RunningScript_ReceivesSavedStateThroughRestoreState проверь это утверждение."
                        + restoredElapsed);
        assertEquals(
                List.of("stateful"),
                created,
                "восстановление обязано создать экземпляр скрипта ровно один раз: второй экземпляр начал бы "
                        + "работу с нуля рядом с восстановленным. Получено  Иначе тест restore_RunningScript_ReceivesSavedStateThroughRestoreState перестанет ловить эту поломку. При смене restore_RunningScript_ReceivesSavedStateThroughRestoreState проверь это утверждение."
                        + created);
    }

    @Test
    void restore_TerminalSnapshots_RunNothingAndKeepReason() {
        AtomicInteger creations = new AtomicInteger();
        ScriptRegistry counting = ScriptRegistry.of(Map.of("s", params -> {
            creations.incrementAndGet();
            return () -> ScriptStatus.SUCCEEDED;
        }));

        TaskRunner succeeded = TaskRunner.restore(terminal(RunStatus.SUCCEEDED, null), counting, conditions());
        assertEquals(
                RunStatus.SUCCEEDED,
                succeeded.status(),
                "восстановленный SUCCEEDED хранит итог Иначе тест restore_TerminalSnapshots_RunNothingAndKeepReason перестанет ловить эту поломку. При смене restore_TerminalSnapshots_RunNothingAndKeepReason проверь это утверждение.");
        assertEquals(
                RunStatus.SUCCEEDED,
                succeeded.tick(),
                "SUCCEEDED не запускает ничего Иначе тест restore_TerminalSnapshots_RunNothingAndKeepReason перестанет ловить эту поломку. При смене restore_TerminalSnapshots_RunNothingAndKeepReason проверь это утверждение.");

        TaskRunner failed = TaskRunner.restore(terminal(RunStatus.FAILED, "не дошёл до цели"), counting, conditions());
        assertEquals(
                RunStatus.FAILED,
                failed.status(),
                "восстановленный FAILED хранит итог Иначе тест restore_TerminalSnapshots_RunNothingAndKeepReason перестанет ловить эту поломку. При смене restore_TerminalSnapshots_RunNothingAndKeepReason проверь это утверждение.");
        assertEquals(
                "не дошёл до цели",
                failed.failureReason().orElse(null),
                "причина провала сохраняется дословно: без неё отчёт о задаче теряет смысл Иначе тест restore_TerminalSnapshots_RunNothingAndKeepReason перестанет ловить эту поломку. При смене restore_TerminalSnapshots_RunNothingAndKeepReason проверь это утверждение.");
        assertEquals(
                RunStatus.FAILED,
                failed.tick(),
                "FAILED не запускает ничего Иначе тест restore_TerminalSnapshots_RunNothingAndKeepReason перестанет ловить эту поломку. При смене restore_TerminalSnapshots_RunNothingAndKeepReason проверь это утверждение.");

        TaskRunner cancelled = TaskRunner.restore(terminal(RunStatus.CANCELLED, null), counting, conditions());
        assertEquals(
                RunStatus.CANCELLED,
                cancelled.status(),
                "восстановленный CANCELLED хранит итог Иначе тест restore_TerminalSnapshots_RunNothingAndKeepReason перестанет ловить эту поломку. При смене restore_TerminalSnapshots_RunNothingAndKeepReason проверь это утверждение.");
        assertEquals(
                RunStatus.CANCELLED,
                cancelled.tick(),
                "отменённая задача не оживает Иначе тест restore_TerminalSnapshots_RunNothingAndKeepReason перестанет ловить эту поломку. При смене restore_TerminalSnapshots_RunNothingAndKeepReason проверь это утверждение.");

        assertEquals(
                0,
                creations.get(),
                "терминальный снимок не создаёт ни одного экземпляра скрипта Иначе тест restore_TerminalSnapshots_RunNothingAndKeepReason перестанет ловить эту поломку. При смене restore_TerminalSnapshots_RunNothingAndKeepReason проверь это утверждение.");
    }

    @Test
    void restore_FailedSnapshotWithoutPlan_ReportsNoPlan() {
        TaskRunner failed =
                TaskRunner.restore(terminal(RunStatus.FAILED, "не удалось сохранить задачу"), registry(), conditions());

        assertEquals(
                Optional.empty(),
                failed.plan(),
                "Завершённый снимок не несёт план (так пишет и сохранение после сбоя): восстановленная задача "
                        + "обязана отвечать «плана нет», а не null — иначе запрос плана духа падает NPE При смене restore_FailedSnapshotWithoutPlan_ReportsNoPlan проверь это утверждение.");
    }
}
