package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RescueAssignTests {
    private static final UUID HELPER = UUID.fromString("f58b83b6-c9a0-406d-967a-822843debf54");

    @Test
    void assign_SelfPlanExists_SelfAssignee() {
        UUID stuck = UUID.randomUUID();

        RescuePlans.Assignment assignment = RescuePlans.assign(stuck, 140, null, Optional.empty());
        assertEquals(
                RescuePlans.Mode.SELF,
                assignment.mode(),
                "Сам выходит, если у него есть план выхода Иначе тест assign_SelfPlanExists_SelfAssignee перестанет ловить эту поломку. При смене assign_SelfPlanExists_SelfAssignee проверь это утверждение.");
        assertEquals(
                Optional.of(stuck),
                assignment.assignee(),
                "Самоспасение выдаётся самому застрявшему Иначе тест assign_SelfPlanExists_SelfAssignee перестанет ловить эту поломку. При смене assign_SelfPlanExists_SelfAssignee проверь это утверждение.");
        assertEquals(
                "graph:rescue_spirit@140",
                assignment.cause(),
                "Причина несёт тик эпизода: по ней доска отличает новое застревание от закрытого Иначе тест assign_SelfPlanExists_SelfAssignee перестанет ловить эту поломку. При смене assign_SelfPlanExists_SelfAssignee проверь это утверждение.");
    }

    @Test
    void assign_SelfPlanExists_HelperNotSpent() {
        UUID stuck = UUID.randomUUID();
        RescuePlans.Assignment assignment = RescuePlans.assign(stuck, 140, null, Optional.of(HELPER));
        assertEquals(
                RescuePlans.Mode.SELF,
                assignment.mode(),
                "Свой выход дешевле помощника: дух не ждёт чужую работу, когда может выбраться сам Иначе тест assign_SelfPlanExists_HelperNotSpent перестанет ловить эту поломку. При смене assign_SelfPlanExists_HelperNotSpent проверь это утверждение.");
        assertEquals(
                Optional.of(stuck),
                assignment.assignee(),
                "Самоспасение остаётся адресным застрявшему Иначе тест assign_SelfPlanExists_HelperNotSpent перестанет ловить эту поломку. При смене assign_SelfPlanExists_HelperNotSpent проверь это утверждение.");
    }

    @Test
    void assign_NoSelfPlanWithHelper_HelperAssigned() {
        RescuePlans.Assignment assignment =
                RescuePlans.assign(UUID.randomUUID(), 140, "нет кирки", Optional.of(HELPER));
        assertEquals(
                RescuePlans.Mode.HELPER,
                assignment.mode(),
                "Без своего плана выход ставит помощник Иначе тест assign_NoSelfPlanWithHelper_HelperAssigned перестанет ловить эту поломку. При смене assign_NoSelfPlanWithHelper_HelperAssigned проверь это утверждение.");
        assertEquals(
                Optional.of(HELPER),
                assignment.assignee(),
                "Помощник назначен тот, до кого проверен достижимый путь: выдача не ищет его заново Иначе тест assign_NoSelfPlanWithHelper_HelperAssigned перестанет ловить эту поломку. При смене assign_NoSelfPlanWithHelper_HelperAssigned проверь это утверждение.");
        assertTrue(
                assignment.why().contains("помощник") && assignment.why().contains("нет кирки"),
                "Причина называет и помощника, и почему сам не выйдет:  Иначе тест assign_NoSelfPlanWithHelper_HelperAssigned перестанет ловить эту поломку. При смене assign_NoSelfPlanWithHelper_HelperAssigned проверь это утверждение."
                        + assignment.why());
    }

    @Test
    void assign_NoSelfPlanNoHelper_Impossible() {
        RescuePlans.Assignment assignment = RescuePlans.assign(UUID.randomUUID(), 140, "нет кирки", Optional.empty());
        assertEquals(
                RescuePlans.Mode.IMPOSSIBLE,
                assignment.mode(),
                "Без плана и без помощника задание не ставится: иначе дух снова получит ложное спасение При смене assign_NoSelfPlanNoHelper_Impossible проверь это утверждение.");
        assertTrue(
                assignment.why().contains("невозможно") && assignment.why().contains("нет кирки"),
                "Отказ объясняет, почему спасти нельзя:  Иначе тест assign_NoSelfPlanNoHelper_Impossible перестанет ловить эту поломку. При смене assign_NoSelfPlanNoHelper_Impossible проверь это утверждение."
                        + assignment.why());
    }

    @Test
    void assign_NewStuckEpisode_DifferentCause() {
        assertNotEquals(
                RescuePlans.cause(140),
                RescuePlans.cause(9000),
                "Новый эпизод застревания даёт новую причину: DONE прошлого спасения не закрывает новое Иначе тест assign_NewStuckEpisode_DifferentCause перестанет ловить эту поломку. При смене assign_NewStuckEpisode_DifferentCause проверь это утверждение.");
    }

    @Test
    void self_PitWithSteps_Pit() {
        assertEquals(
                RescuePlans.Self.PIT,
                RescuePlans.self(new RescuePlans.Situation(true, true, false, false, false)),
                "Из ямы со ступенями дух поднимается сам, помощник не нужен Иначе тест self_PitWithSteps_Pit перестанет ловить эту поломку. При смене self_PitWithSteps_Pit проверь это утверждение.");
    }

    private static void collect(TaskNode plan, List<String> names) {
        if (plan instanceof ScriptCall call) {
            names.add(call.script());
        } else if (plan instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, names));
        } else if (plan instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, names));
        }
    }
}
