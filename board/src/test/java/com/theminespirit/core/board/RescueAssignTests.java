package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RescueAssignTests {
    private static final UUID HELPER = UUID.fromString("f58b83b6-c9a0-406d-967a-822843debf54");

    @Test
    void assign_SelfPlanExists_SelfAssignee() {
        UUID stuck = UUID.randomUUID();

        RescuePlans.Assignment assignment = RescuePlans.assign(stuck, 140, null, Optional.empty());

        assertEquals(RescuePlans.Mode.SELF, assignment.mode(), "Сам выходит, если у него есть план выхода");
        assertEquals(Optional.of(stuck), assignment.assignee(), "Самоспасение выдаётся самому застрявшему");
        assertEquals(
                "graph:rescue_spirit@140",
                assignment.cause(),
                "Причина несёт тик эпизода: по ней доска отличает новое застревание от закрытого");
    }

    @Test
    void assign_SelfPlanExists_HelperNotSpent() {
        UUID stuck = UUID.randomUUID();

        RescuePlans.Assignment assignment = RescuePlans.assign(stuck, 140, null, Optional.of(HELPER));

        assertEquals(
                RescuePlans.Mode.SELF,
                assignment.mode(),
                "Свой выход дешевле помощника: дух не ждёт чужую работу, когда может выбраться сам");
        assertEquals(Optional.of(stuck), assignment.assignee(), "Самоспасение остаётся адресным застрявшему");
    }

    @Test
    void assign_NoSelfPlanWithHelper_HelperAssigned() {
        RescuePlans.Assignment assignment =
                RescuePlans.assign(UUID.randomUUID(), 140, "нет кирки", Optional.of(HELPER));

        assertEquals(RescuePlans.Mode.HELPER, assignment.mode(), "Без своего плана выход ставит помощник");
        assertEquals(
                Optional.of(HELPER),
                assignment.assignee(),
                "Помощник назначен тот, до кого проверен достижимый путь: выдача не ищет его заново");
        assertTrue(
                assignment.why().contains("помощник") && assignment.why().contains("нет кирки"),
                "Причина называет и помощника, и почему сам не выйдет: " + assignment.why());
    }

    @Test
    void assign_NoSelfPlanNoHelper_Impossible() {
        RescuePlans.Assignment assignment = RescuePlans.assign(UUID.randomUUID(), 140, "нет кирки", Optional.empty());

        assertEquals(
                RescuePlans.Mode.IMPOSSIBLE,
                assignment.mode(),
                "Без плана и без помощника задание не ставится: иначе дух снова получит ложное спасение");
        assertTrue(
                assignment.why().contains("невозможно") && assignment.why().contains("нет кирки"),
                "Отказ объясняет, почему спасти нельзя: " + assignment.why());
    }

    @Test
    void assign_NewStuckEpisode_DifferentCause() {
        assertNotEquals(
                RescuePlans.cause(140),
                RescuePlans.cause(9000),
                "Новый эпизод застревания даёт новую причину: DONE прошлого спасения не закрывает новое");
    }

    @Test
    void self_PitWithSteps_Pit() {
        assertEquals(
                RescuePlans.Self.PIT,
                RescuePlans.self(new RescuePlans.Situation(true, true, false, false, false)),
                "Из ямы со ступенями дух поднимается сам, помощник не нужен");
    }

    @Test
    void self_PitWithoutStepsOrTool_None() {
        assertEquals(
                RescuePlans.Self.NONE,
                RescuePlans.self(new RescuePlans.Situation(true, false, false, false, false)),
                "Без ступеней и без кирки из ямы не выйти: нужен помощник");
    }

    @Test
    void self_SurfaceWithKnownPath_Walk() {
        assertEquals(
                RescuePlans.Self.WALK,
                RescuePlans.self(new RescuePlans.Situation(false, false, false, true, false)),
                "На поверхности с известным путём домой дух идёт сам без ломки блоков");
    }

    @Test
    void self_SurfaceNoPathWithTool_Dig() {
        assertEquals(
                RescuePlans.Self.DIG,
                RescuePlans.self(new RescuePlans.Situation(false, false, true, true, true)),
                "Пути нет, но кирка есть: дух прокладывает выход к дому сам");
    }

    @Test
    void self_NoPathWithoutTool_None() {
        assertEquals(
                RescuePlans.Self.NONE,
                RescuePlans.self(new RescuePlans.Situation(false, false, false, true, true)),
                "Пути нет и прокопать нечем: сам не выйдет, нужен свободный помощник (observe23 A)");
    }

    @Test
    void self_SealedWithoutTool_None() {
        assertEquals(
                RescuePlans.Self.NONE,
                RescuePlans.self(new RescuePlans.Situation(false, false, false, false, false)),
                "Под землёй без кирки выхода нет: повторять self-rescue бессмысленно (observe23 A)");
    }
}
