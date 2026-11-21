package com.theminespirit.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanBudgetTests {
    private static final ScriptCall CALL = new ScriptCall("acquire", Map.of("item", "stick", "count", 1));

    @Test
    void measure_SingleCall_CountsOneNode() {
        PlanBudget.Budget budget = PlanBudget.measure(CALL);

        assertEquals(
                1,
                budget.depth(),
                "Одиночный вызов имеет глубину 1"
                        + " Иначе тест measure_SingleCall_CountsOneNode перестанет ловить эту поломку. При смене measure_SingleCall_CountsOneNode проверь это утверждение.");
        assertEquals(
                1,
                budget.nodes(),
                "Одиночный вызов считается одним узлом"
                        + " Иначе тест measure_SingleCall_CountsOneNode перестанет ловить эту поломку. При смене measure_SingleCall_CountsOneNode проверь это утверждение.");
        assertFalse(
                budget.exceeds(),
                "Маленький план не обязан выходить за пределы лимитов"
                        + " Иначе тест measure_SingleCall_CountsOneNode перестанет ловить эту поломку. При смене measure_SingleCall_CountsOneNode проверь это утверждение.");
    }

    @Test
    void measure_Sequence_CountsAllChildren() {
        TaskNode root = new SequenceNode(List.of(new SequenceNode(List.of(CALL, CALL)), new RepeatNode(3, CALL), CALL));

        PlanBudget.Budget budget = PlanBudget.measure(root);

        assertEquals(
                3,
                budget.depth(),
                "Глубина — самый длинный путь: sequence → sequence → script"
                        + " Иначе тест measure_Sequence_CountsAllChildren перестанет ловить эту поломку. При смене measure_Sequence_CountsAllChildren проверь это утверждение.");
        assertEquals(
                7,
                budget.nodes(),
                "Узлы: корень sequence + вложенный sequence с двумя вызовами + repeat с вызовом + одиночный вызов"
                        + " Иначе тест measure_Sequence_CountsAllChildren перестанет ловить эту поломку. При смене measure_Sequence_CountsAllChildren проверь это утверждение.");
    }

    @Test
    void measure_FallbackAndParallel_CountsSteps() {
        TaskNode root = new FallbackNode(List.of(new ParallelNode(ParallelMode.ALL, List.of(CALL, CALL)), CALL));

        PlanBudget.Budget budget = PlanBudget.measure(root);

        assertEquals(
                3,
                budget.depth(),
                "Глубина: fallback → parallel → script"
                        + " Иначе тест measure_FallbackAndParallel_CountsSteps перестанет ловить эту поломку. При смене measure_FallbackAndParallel_CountsSteps проверь это утверждение.");
        assertEquals(
                5,
                budget.nodes(),
                "Узлы: fallback + parallel + два script в parallel + script во второй ветке"
                        + " Иначе тест measure_FallbackAndParallel_CountsSteps перестанет ловить эту поломку. При смене measure_FallbackAndParallel_CountsSteps проверь это утверждение.");
    }

    @Test
    void measure_ConditionWithoutElse_CountsThenBranch() {
        TaskNode root = new ConditionNode("always", Map.of(), CALL, null);

        PlanBudget.Budget budget = PlanBudget.measure(root);

        assertEquals(
                2,
                budget.depth(),
                "Глубина: condition → then"
                        + " Иначе тест measure_ConditionWithoutElse_CountsThenBranch перестанет ловить эту поломку. При смене measure_ConditionWithoutElse_CountsThenBranch проверь это утверждение.");
        assertEquals(
                2,
                budget.nodes(),
                "Ветка else отсутствует — учитывается только then"
                        + " Иначе тест measure_ConditionWithoutElse_CountsThenBranch перестанет ловить эту поломку. При смене measure_ConditionWithoutElse_CountsThenBranch проверь это утверждение.");
    }

    @Test
    void measure_ConditionWithElse_CountsBothBranches() {
        TaskNode root = new ConditionNode("always", Map.of(), CALL, CALL);

        PlanBudget.Budget budget = PlanBudget.measure(root);

        assertEquals(
                2,
                budget.depth(),
                "Обе ветки на одной глубине"
                        + " Иначе тест measure_ConditionWithElse_CountsBothBranches перестанет ловить эту поломку. При смене measure_ConditionWithElse_CountsBothBranches проверь это утверждение.");
        assertEquals(
                3,
                budget.nodes(),
                "Узлы: condition + then + else"
                        + " Иначе тест measure_ConditionWithElse_CountsBothBranches перестанет ловить эту поломку. При смене measure_ConditionWithElse_CountsBothBranches проверь это утверждение.");
    }

    @Test
    void exceeds_DepthBeyondLimit_IsExceeded() {
        TaskNode root = CALL;
        for (int i = 0; i < PlanLimits.MAX_DEPTH; i++) {
            root = new SequenceNode(List.of(root));
        }

        PlanBudget.Budget budget = PlanBudget.measure(root);

        assertTrue(
                budget.exceeds(),
                "План глубже MAX_DEPTH обязан выходить за лимит бюджета"
                        + " Иначе тест exceeds_DepthBeyondLimit_IsExceeded перестанет ловить эту поломку. При смене exceeds_DepthBeyondLimit_IsExceeded проверь это утверждение.");
    }

    @Test
    void exceeds_NodesBeyondLimit_IsExceeded() {
        int extra = PlanLimits.MAX_NODES;
        TaskNode[] calls = new TaskNode[extra];
        for (int i = 0; i < extra; i++) {
            calls[i] = CALL;
        }

        PlanBudget.Budget budget = PlanBudget.measure(new SequenceNode(List.of(calls)));

        assertTrue(
                budget.exceeds(),
                "План больше MAX_NODES узлов обязан выходить за лимит бюджета"
                        + " Иначе тест exceeds_NodesBeyondLimit_IsExceeded перестанет ловить эту поломку. При смене exceeds_NodesBeyondLimit_IsExceeded проверь это утверждение.");
    }

    @Test
    void measure_NullRoot_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> PlanBudget.measure(null),
                "measure(null) обязан отказывать: бюджет считается по дереву задач"
                        + " Иначе тест measure_NullRoot_IsRejected перестанет ловить эту поломку. При смене measure_NullRoot_IsRejected проверь это утверждение.");
    }

    @Test
    void describe_BudgetAtLimits_MentionsDepthAndNodes() {
        PlanBudget.Budget budget = new PlanBudget.Budget(PlanLimits.MAX_DEPTH, PlanLimits.MAX_NODES);

        String text = budget.describe();

        assertTrue(
                text.contains(String.valueOf(PlanLimits.MAX_DEPTH)),
                "Описание обязано называть предел глубины: по нему игрок читает, что именно не так"
                        + " Иначе тест describe_BudgetAtLimits_MentionsDepthAndNodes перестанет ловить эту поломку. При смене describe_BudgetAtLimits_MentionsDepthAndNodes проверь это утверждение.");
        assertTrue(
                text.contains(String.valueOf(PlanLimits.MAX_NODES)),
                "Описание обязано называть предел узлов: по нему игрок читает, что именно не так"
                        + " Иначе тест describe_BudgetAtLimits_MentionsDepthAndNodes перестанет ловить эту поломку. При смене describe_BudgetAtLimits_MentionsDepthAndNodes проверь это утверждение.");
    }
}
