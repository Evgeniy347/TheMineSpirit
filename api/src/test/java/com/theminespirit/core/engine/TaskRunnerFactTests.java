package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskRunnerFactTests {
    private static final String SCRIPT = "do";
    private static final String CHECK = "yes";

    @Test
    void tick_FailingStepWithFact_LeavesFactForTheBoard() {
        TaskRunner runner = TaskRunner.start(
                new SequenceNode(List.of(new ScriptCall("go", Map.of()), new ScriptCall("later", Map.of()))),
                ScriptRegistry.of(Map.of(
                        "go", TestScripts.failingWithFact(1, FailureFact.NO_PATH),
                        "later", TestScripts.succeeding(1))),
                TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.FAILED, runner.tick(), "Шаг с фактом «пути нет» всё равно проваливает задачу");

        assertEquals(
                Optional.of(FailureFact.NO_PATH),
                runner.failureFact(),
                "Факт провала идёт от провалившегося скрипта через sequence: доска обязана ставить rescue, "
                        + "а не выводить «пути нет» из вида блокировки");
    }

    @Test
    void tick_FailureWithoutFact_LeavesNoFact() {
        TaskRunner runner = TaskRunner.start(
                new ScriptCall(SCRIPT, Map.of()),
                TestScripts.singleRegistry(SCRIPT, TestScripts.failing(1)),
                TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.FAILED, runner.tick(), "Скрипт без факта проваливается как раньше");

        assertTrue(
                runner.failureFact().isEmpty(),
                "Обычный провал не приписывает духу «пути нет»: иначе метка застревания ставилась бы на любую "
                        + "ошибку скрипта и дух ждал бы rescue без причины");
    }

    @Test
    void tick_FallbackAllFailed_DropsPathFactOfEarlierBranch() {
        TaskRunner runner = TaskRunner.start(
                new FallbackNode(List.of(new ScriptCall("go", Map.of()), new ScriptCall(SCRIPT, Map.of()))),
                ScriptRegistry.of(Map.of(
                        "go", TestScripts.failingWithFact(1, FailureFact.NO_PATH), SCRIPT, TestScripts.failing(1))),
                TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.RUNNING, runner.tick(), "После провала первой ветки fallback пробует вторую");
        assertEquals(RunStatus.FAILED, runner.tick(), "Второй провал второй ветки завершает fallback");
        assertEquals(
                Optional.empty(),
                runner.failureFact(),
                "«Пути нет» относится к оставленной ветке: вторая ветка упала без факта, и выдавать ей чужое "
                        + "«пути нет» нельзя");
    }

    @Test
    void tick_FallbackAllFailed_KeepsWorldFactOfEarlierBranch() {
        TaskRunner runner = TaskRunner.start(
                new FallbackNode(List.of(new ScriptCall("go", Map.of()), new ScriptCall(SCRIPT, Map.of()))),
                ScriptRegistry.of(Map.of(
                        "go", TestScripts.failingWithFact(1, FailureFact.NO_TREES), SCRIPT, TestScripts.failing(1))),
                TestScripts.condition(CHECK, true));

        assertEquals(RunStatus.RUNNING, runner.tick(), "После провала первой ветки fallback пробует вторую");
        assertEquals(RunStatus.FAILED, runner.tick(), "Второй провал второй ветки завершает fallback");
        assertEquals(
                Optional.of(FailureFact.NO_TREES),
                runner.failureFact(),
                "Факт «деревьев нет» — про мир и не исчезает от провала запасной ветки: иначе дух снова и снова "
                        + "ставит рубку, которую нечем выполнить (живой случай 11.11)");
    }
}
