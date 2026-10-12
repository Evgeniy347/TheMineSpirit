package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        TaskRunner runner = TaskRunner.start(sequence(
                new ScriptCall(FAST, Map.of()),
                new ScriptCall(SLOW, Map.of()),
                new ScriptCall(FAST, Map.of())),
                ScriptRegistry.of(Map.of(FAST, TestScripts.succeeding(1), SLOW, TestScripts.succeeding(3))),
                TestScripts.condition("yes", true));

        assertEquals(RunStatus.RUNNING, runner.tick(), "первый тик закрывает первый шаг sequence");

        RunProgress between = runner.progress().orElseThrow();
        assertEquals(2, between.step(),
                "на втором шаге sequence прогресс обязан показывать шаг 2: по номеру шага клиент видит, сколько осталось");
        assertEquals(3, between.totalSteps(),
                "число шагов sequence равно числу узлов плана: 3 шага — 3 всего");

        assertEquals(RunStatus.RUNNING, runner.tick(), "второй тик поднимает второй шаг sequence");

        RunProgress progress = runner.progress().orElseThrow();
        assertEquals(2, progress.step(), "второй шаг ещё выполняется — номер шага не сдвигается");
        assertEquals(SLOW, progress.currentScript(),
                "текущий скрипт обязан называть исполняемый второй шаг");
    }

    @Test
    void progress_NonSequenceRoot_ReportsSingleStep() {
        TaskRunner runner = TaskRunner.start(new ScriptCall(SLOW, Map.of()),
                TestScripts.singleRegistry(SLOW, TestScripts.succeeding(5)),
                TestScripts.condition("yes", true));

        runner.tick();

        RunProgress progress = runner.progress().orElseThrow();
        assertEquals(1, progress.step(), "корень-скрипт всегда первый шаг: других шагов у него нет");
        assertEquals(1, progress.totalSteps(), "корень-скрипт всегда единственный шаг");
        assertEquals(SLOW, progress.currentScript(), "корень-скрипт сам себя называет текущим");
    }

    @Test
    void progress_CountingScript_ReportsDoneAndTarget() {
        TaskRunner runner = TaskRunner.start(new ScriptCall(COUNTED, Map.of()),
                TestScripts.singleRegistry(COUNTED, TestScripts.counting(5)),
                TestScripts.condition("yes", true));

        runner.tick();

        RunProgress progress = runner.progress().orElseThrow();
        assertEquals(1, progress.done(),
                "после первого тика счётный скрипт обязан отчитаться о выполненном куске работы");
        assertEquals(5, progress.target(),
                "цель счётного скрипта равна заявленному числу шагов работы");
    }

    @Test
    void progress_AfterTerminal_NoProgressLeft() {
        TaskRunner runner = TaskRunner.start(new ScriptCall(FAST, Map.of()),
                TestScripts.singleRegistry(FAST, TestScripts.succeeding(1)),
                TestScripts.condition("yes", true));
        runner.tick();

        assertEquals(RunStatus.SUCCEEDED, runner.status(), "однотиковый скрипт завершает задачу");
        assertTrue(runner.progress().isEmpty(),
                "после терминального статуса прогресса нет: замороженные цифры выглядели бы как работающая задача");
    }

    @Property(tries = 200)
    void progress_WhileRunning_StepWithinBoundsAndNonDecreasing(
            @ForAll @IntRange(min = 1, max = 8) int steps,
            @ForAll @IntRange(min = 1, max = 4) int ticksEach) {
        List<TaskNode> nodes = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            nodes.add(new ScriptCall(SLOW, Map.of()));
        }
        TaskRunner runner = TaskRunner.start(new SequenceNode(nodes),
                TestScripts.singleRegistry(SLOW, TestScripts.succeeding(ticksEach)),
                TestScripts.condition("yes", true));

        int previousStep = 0;
        int ticks = 0;
        while (runner.status() == RunStatus.RUNNING && ticks <= steps * ticksEach + 2) {
            Optional<RunProgress> progress = runner.progress();
            if (progress.isPresent()) {
                RunProgress current = progress.get();
                assertTrue(current.step() >= 1 && current.step() <= current.totalSteps(),
                        "шаг обязан жить в границах 1..totalSteps: шаг=" + current.step()
                                + ", total=" + current.totalSteps() + ", steps=" + steps);
                assertTrue(current.step() >= previousStep,
                        "номер шага не убывает за время выполнения: был " + previousStep + ", стал " + current.step());
                previousStep = current.step();
            }
            runner.tick();
            ticks++;
        }
        assertTrue(ticks <= steps * ticksEach + 2,
                "выполнение обязано завершиться за steps×ticksEach тиков, получили " + ticks);
    }
}
