package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class TaskRunnerProgressTests {
    private static final String FAST = "fast";
    private static final String SLOW = "slow";
    private static final String COUNTED = "counted";

    private static TaskNode sequence(TaskNode... steps) {
        return new SequenceNode(List.of(steps));
    }

    @Test
    void progress_SequenceOnSecondStep_ReportsTwoOfThree() {
        TaskRunner runner = TaskRunner.start(
                sequence(
                        new ScriptCall(FAST, Map.of()), new ScriptCall(SLOW, Map.of()), new ScriptCall(FAST, Map.of())),
                ScriptRegistry.of(Map.of(FAST, TestScripts.succeeding(1), SLOW, TestScripts.succeeding(3))),
                TestScripts.condition("yes", true));

        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "первый тик закрывает первый шаг sequence Иначе тест progress_SequenceOnSecondStep_ReportsTwoOfThree перестанет ловить эту поломку. При смене progress_SequenceOnSecondStep_ReportsTwoOfThree проверь это утверждение.");

        RunProgress between = runner.progress().orElseThrow();
        assertEquals(
                2,
                between.step(),
                "на втором шаге sequence прогресс обязан показывать шаг 2: по номеру шага клиент видит, сколько осталось Иначе тест progress_SequenceOnSecondStep_ReportsTwoOfThree перестанет ловить эту поломку. При смене progress_SequenceOnSecondStep_ReportsTwoOfThree проверь это утверждение.");
        assertEquals(
                3,
                between.totalSteps(),
                "число шагов sequence равно числу узлов плана: 3 шага — 3 всего Иначе тест progress_SequenceOnSecondStep_ReportsTwoOfThree перестанет ловить эту поломку. При смене progress_SequenceOnSecondStep_ReportsTwoOfThree проверь это утверждение.");

        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "второй тик поднимает второй шаг sequence Иначе тест progress_SequenceOnSecondStep_ReportsTwoOfThree перестанет ловить эту поломку. При смене progress_SequenceOnSecondStep_ReportsTwoOfThree проверь это утверждение.");

        RunProgress progress = runner.progress().orElseThrow();
        assertEquals(
                2,
                progress.step(),
                "второй шаг ещё выполняется — номер шага не сдвигается Иначе тест progress_SequenceOnSecondStep_ReportsTwoOfThree перестанет ловить эту поломку. При смене progress_SequenceOnSecondStep_ReportsTwoOfThree проверь это утверждение.");
        assertEquals(
                SLOW,
                progress.currentScript(),
                "текущий скрипт обязан называть исполняемый второй шаг Иначе тест progress_SequenceOnSecondStep_ReportsTwoOfThree перестанет ловить эту поломку. При смене progress_SequenceOnSecondStep_ReportsTwoOfThree проверь это утверждение.");
    }

    @Test
    void progress_NonSequenceRoot_ReportsSingleStep() {
        TaskRunner runner = TaskRunner.start(
                new ScriptCall(SLOW, Map.of()),
                TestScripts.singleRegistry(SLOW, TestScripts.succeeding(5)),
                TestScripts.condition("yes", true));

        runner.tick();

        RunProgress progress = runner.progress().orElseThrow();
        assertEquals(
                1,
                progress.step(),
                "корень-скрипт всегда первый шаг: других шагов у него нет Иначе тест progress_NonSequenceRoot_ReportsSingleStep перестанет ловить эту поломку. При смене progress_NonSequenceRoot_ReportsSingleStep проверь это утверждение.");
        assertEquals(
                1,
                progress.totalSteps(),
                "корень-скрипт всегда единственный шаг Иначе тест progress_NonSequenceRoot_ReportsSingleStep перестанет ловить эту поломку. При смене progress_NonSequenceRoot_ReportsSingleStep проверь это утверждение.");
        assertEquals(
                SLOW,
                progress.currentScript(),
                "корень-скрипт сам себя называет текущим Иначе тест progress_NonSequenceRoot_ReportsSingleStep перестанет ловить эту поломку. При смене progress_NonSequenceRoot_ReportsSingleStep проверь это утверждение.");
    }

    @Test
    void progress_BetweenSteps_KeepsLastDone() {
        TaskRunner runner = TaskRunner.start(
                sequence(new ScriptCall(COUNTED, Map.of()), new ScriptCall(SLOW, Map.of())),
                ScriptRegistry.of(Map.of(COUNTED, TestScripts.counting(1), SLOW, TestScripts.succeeding(3))),
                TestScripts.condition("yes", true));

        runner.tick();

        RunProgress between = runner.progress().orElseThrow();
        assertEquals(
                2,
                between.step(),
                "Счётный шаг закрылся за один тик — раннер уже на втором шаге Иначе тест progress_BetweenSteps_KeepsLastDone перестанет ловить эту поломку. При смене progress_BetweenSteps_KeepsLastDone проверь это утверждение.");
        assertNull(
                between.currentScript(),
                "Между шагами скрипта ещё нет: следующий спавнится на следующем тике Иначе тест progress_BetweenSteps_KeepsLastDone перестанет ловить эту поломку. При смене progress_BetweenSteps_KeepsLastDone проверь это утверждение.");
        assertEquals(
                1,
                between.done(),
                "Между шагами сделанное обязано помнить последний закрытый скрипт: иначе сторож застоя "
                        + "видит «нет прогресса» у работающей задачи (repair:clear_grounds, пустой бокс за тик) При смене progress_BetweenSteps_KeepsLastDone проверь это утверждение.");
        assertEquals(
                1,
                between.target(),
                "Цель последнего закрытого скрипта тоже сохраняется между шагами Иначе тест progress_BetweenSteps_KeepsLastDone перестанет ловить эту поломку. При смене progress_BetweenSteps_KeepsLastDone проверь это утверждение.");
    }

    @Test
    void progress_CountingScript_ReportsDoneAndTarget() {
        TaskRunner runner = TaskRunner.start(
                new ScriptCall(COUNTED, Map.of()),
                TestScripts.singleRegistry(COUNTED, TestScripts.counting(5)),
                TestScripts.condition("yes", true));

        runner.tick();
        RunProgress progress = runner.progress().orElseThrow();
        assertEquals(
                1,
                progress.done(),
                "после первого тика счётный скрипт обязан отчитаться о выполненном куске работы Иначе тест progress_CountingScript_ReportsDoneAndTarget перестанет ловить эту поломку. При смене progress_CountingScript_ReportsDoneAndTarget проверь это утверждение.");
        assertEquals(
                5,
                progress.target(),
                "цель счётного скрипта равна заявленному числу шагов работы Иначе тест progress_CountingScript_ReportsDoneAndTarget перестанет ловить эту поломку. При смене progress_CountingScript_ReportsDoneAndTarget проверь это утверждение.");
    }

    @Test
    void progress_AfterTerminal_NoProgressLeft() {
        TaskRunner runner = TaskRunner.start(
                new ScriptCall(FAST, Map.of()),
                TestScripts.singleRegistry(FAST, TestScripts.succeeding(1)),
                TestScripts.condition("yes", true));
        runner.tick();
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.status(),
                "однотиковый скрипт завершает задачу Иначе тест progress_AfterTerminal_NoProgressLeft перестанет ловить эту поломку. При смене progress_AfterTerminal_NoProgressLeft проверь это утверждение.");
        assertTrue(
                runner.progress().isEmpty(),
                "после терминального статуса прогресса нет: замороженные цифры выглядели бы как работающая задача Иначе тест progress_AfterTerminal_NoProgressLeft перестанет ловить эту поломку. При смене progress_AfterTerminal_NoProgressLeft проверь это утверждение.");
    }

    @Test
    void failedAt_CountingScriptFails_KeepsDoneAndTarget() {
        TaskRunner runner = TaskRunner.start(
                new ScriptCall(COUNTED, Map.of()),
                TestScripts.singleRegistry(COUNTED, TestScripts.failingCounting(4)),
                TestScripts.condition("yes", true));
        RunStatus status = RunStatus.RUNNING;
        for (int tick = 0; tick < 4 && status == RunStatus.RUNNING; tick++) {
            status = runner.tick();
        }
        assertEquals(
                RunStatus.FAILED,
                status,
                "Счётный скрипт проваливается — задача падает Иначе тест failedAt_CountingScriptFails_KeepsDoneAndTarget перестанет ловить эту поломку. При смене failedAt_CountingScriptFails_KeepsDoneAndTarget проверь это утверждение.");
        RunProgress failedAt = runner.failedAt().orElseThrow();
        assertEquals(
                4,
                failedAt.done(),
                "Провал обязан сохранить сделанное: иначе непонятно, где дух застрял При смене failedAt_CountingScriptFails_KeepsDoneAndTarget проверь это утверждение.");
        assertEquals(
                4,
                failedAt.target(),
                "Провал обязан сохранить цель счётного скрипта Иначе тест failedAt_CountingScriptFails_KeepsDoneAndTarget перестанет ловить эту поломку. При смене failedAt_CountingScriptFails_KeepsDoneAndTarget проверь это утверждение.");
    }

    @Test
    void failedAt_ScriptFailsOnFirstTick_NamesFailedScript() {
        TaskRunner runner = TaskRunner.start(
                new ScriptCall(FAST, Map.of()),
                TestScripts.singleRegistry(FAST, TestScripts.failing(1)),
                TestScripts.condition("yes", true));
        assertEquals(
                RunStatus.FAILED,
                runner.tick(),
                "скрипт, провалившийся на первом тике, закрывает задачу Иначе тест failedAt_ScriptFailsOnFirstTick_NamesFailedScript перестанет ловить эту поломку. При смене failedAt_ScriptFailsOnFirstTick_NamesFailedScript проверь это утверждение.");
        RunProgress failedAt = runner.failedAt().orElseThrow();
        assertEquals(
                FAST,
                failedAt.currentScript(),
                "script_failed обязан назвать скрипт даже при провале на первом тике, пока наблюдатель не успел его увидеть Иначе тест failedAt_ScriptFailsOnFirstTick_NamesFailedScript перестанет ловить эту поломку. При смене failedAt_ScriptFailsOnFirstTick_NamesFailedScript проверь это утверждение.");
        assertTrue(
                runner.progress().isEmpty(),
                "у завершённой задачи текущего прогресса нет: он только в failedAt Иначе тест failedAt_ScriptFailsOnFirstTick_NamesFailedScript перестанет ловить эту поломку. При смене failedAt_ScriptFailsOnFirstTick_NamesFailedScript проверь это утверждение.");
    }

    @Test
    void failedAt_SequenceFailsOnSecondStep_ReportsStepTwoOfThreeAndItsScript() {
        TaskRunner runner = TaskRunner.start(
                sequence(
                        new ScriptCall(FAST, Map.of()), new ScriptCall(SLOW, Map.of()), new ScriptCall(FAST, Map.of())),
                ScriptRegistry.of(Map.of(FAST, TestScripts.succeeding(1), SLOW, TestScripts.failing(2))),
                TestScripts.condition("yes", true));
        RunStatus status = RunStatus.RUNNING;
        for (int tick = 0; tick < 10 && status == RunStatus.RUNNING; tick++) {
            status = runner.tick();
        }
        assertEquals(
                RunStatus.FAILED,
                status,
                "второй шаг проваливается — вся sequence падает Иначе тест failedAt_SequenceFailsOnSecondStep_ReportsStepTwoOfThreeAndItsScript перестанет ловить эту поломку. При смене failedAt_SequenceFailsOnSecondStep_ReportsStepTwoOfThreeAndItsScript проверь это утверждение.");
        RunProgress failedAt = runner.failedAt().orElseThrow();
        assertEquals(
                2,
                failedAt.step(),
                "провал на втором шаге обязан показывать шаг 2 Иначе тест failedAt_SequenceFailsOnSecondStep_ReportsStepTwoOfThreeAndItsScript перестанет ловить эту поломку. При смене failedAt_SequenceFailsOnSecondStep_ReportsStepTwoOfThreeAndItsScript проверь это утверждение.");
        assertEquals(
                3,
                failedAt.totalSteps(),
                "всего шагов три Иначе тест failedAt_SequenceFailsOnSecondStep_ReportsStepTwoOfThreeAndItsScript перестанет ловить эту поломку. При смене failedAt_SequenceFailsOnSecondStep_ReportsStepTwoOfThreeAndItsScript проверь это утверждение.");
        assertEquals(
                SLOW,
                failedAt.currentScript(),
                "провалился скрипт второго шага Иначе тест failedAt_SequenceFailsOnSecondStep_ReportsStepTwoOfThreeAndItsScript перестанет ловить эту поломку. При смене failedAt_SequenceFailsOnSecondStep_ReportsStepTwoOfThreeAndItsScript проверь это утверждение.");
    }

    @Test
    void failedAt_UnknownScript_NamesRequestedScript() {
        TaskRunner runner = TaskRunner.start(
                new ScriptCall("missing", Map.of()),
                TestScripts.singleRegistry(FAST, TestScripts.succeeding(1)),
                TestScripts.condition("yes", true));
        runner.tick();
        assertEquals(
                "missing",
                runner.failedAt().orElseThrow().currentScript(),
                "незарегистрированный скрипт провалил задачу: его имя обязано попасть в failedAt Иначе тест failedAt_UnknownScript_NamesRequestedScript перестанет ловить эту поломку. При смене failedAt_UnknownScript_NamesRequestedScript проверь это утверждение.");
    }

    @Test
    void failedAt_RunningOrSucceeded_IsEmpty() {
        TaskRunner running = TaskRunner.start(
                new ScriptCall(SLOW, Map.of()),
                TestScripts.singleRegistry(SLOW, TestScripts.succeeding(5)),
                TestScripts.condition("yes", true));
        running.tick();
        TaskRunner done = TaskRunner.start(
                new ScriptCall(FAST, Map.of()),
                TestScripts.singleRegistry(FAST, TestScripts.succeeding(1)),
                TestScripts.condition("yes", true));
        done.tick();
        assertTrue(
                running.failedAt().isEmpty(),
                "идущая задача не провалена Иначе тест failedAt_RunningOrSucceeded_IsEmpty перестанет ловить эту поломку. При смене failedAt_RunningOrSucceeded_IsEmpty проверь это утверждение.");
        assertTrue(
                done.failedAt().isEmpty(),
                "успешная задача не провалена Иначе тест failedAt_RunningOrSucceeded_IsEmpty перестанет ловить эту поломку. При смене failedAt_RunningOrSucceeded_IsEmpty проверь это утверждение.");
    }

    @Property(tries = 200)
    void progress_WhileRunning_StepWithinBoundsAndNonDecreasing(
            @ForAll @IntRange(min = 1, max = 8) int steps, @ForAll @IntRange(min = 1, max = 4) int ticksEach) {
        List<TaskNode> nodes = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            nodes.add(new ScriptCall(SLOW, Map.of()));
        }
        TaskRunner runner = TaskRunner.start(
                new SequenceNode(nodes),
                TestScripts.singleRegistry(SLOW, TestScripts.succeeding(ticksEach)),
                TestScripts.condition("yes", true));
        int previousStep = 0;
        int ticks = 0;
        while (runner.status() == RunStatus.RUNNING && ticks <= steps * ticksEach + 2) {
            Optional<RunProgress> progress = runner.progress();
            if (progress.isPresent()) {
                RunProgress current = progress.get();
                assertTrue(
                        current.step() >= 1 && current.step() <= current.totalSteps(),
                        "шаг обязан жить в границах 1..totalSteps: шаг=" + current.step() + ", total="
                                + current.totalSteps()
                                + ", steps= Иначе тест progress_WhileRunning_StepWithinBoundsAndNonDecreasing перестанет ловить эту поломку. При смене progress_WhileRunning_StepWithinBoundsAndNonDecreasing проверь это утверждение."
                                + steps);
                assertTrue(
                        current.step() >= previousStep,
                        "номер шага не убывает за время выполнения: был " + previousStep
                                + ", стал  Иначе тест progress_WhileRunning_StepWithinBoundsAndNonDecreasing перестанет ловить эту поломку. При смене progress_WhileRunning_StepWithinBoundsAndNonDecreasing проверь это утверждение."
                                + current.step());
                previousStep = current.step();
            }
            runner.tick();
            ticks++;
        }
        assertTrue(
                ticks <= steps * ticksEach + 2,
                "выполнение обязано завершиться за steps×ticksEach тиков, получили  Иначе тест progress_WhileRunning_StepWithinBoundsAndNonDecreasing перестанет ловить эту поломку. При смене progress_WhileRunning_StepWithinBoundsAndNonDecreasing проверь это утверждение."
                        + ticks);
    }
}
