package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void self_LiveObserve25LostSpiritWithBlocks_BridgesHome() throws IOException {
        JsonNode live = new ObjectMapper()
                .readTree(Files.readString(
                        Path.of("src/test/resources/live/observe25/rescue.json"), StandardCharsets.UTF_8));
        JsonNode lost = live.get("spirit4");
        BlockCoord feet = coord(lost.get("feet"));
        BlockCoord home = coord(lost.get("home"));
        int blocks = lost.get("cheap_blocks").asInt();

        assertEquals(
                RescuePlans.Self.BRIDGE,
                RescuePlans.self(new RescuePlans.Situation(false, false, false, true, true, blocks)),
                "spirit-4 observe25 стоял в 36 блоках от поселения без пути и без кирки, но с 38 дешёвыми "
                        + "блоками: он обязан мостить дорогу домой, а не объявлять спасение невозможным");
        assertEquals(
                RescuePlans.Mode.SELF,
                RescuePlans.assign(
                                UUID.fromString("7aa08db5-52c1-4bcd-9212-a50150e25038"),
                                34876371,
                                null,
                                Optional.empty())
                        .mode(),
                "Мост из своих блоков — свой выход: застрявший не ждёт помощника, которого нет");

        TaskNode plan = RescuePlans.bridge(feet, home, blocks);
        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        ScriptCall call = assertInstanceOf(ScriptCall.class, plan, "Переправа к дому — один скрипт bridge");
        assertEquals("bridge", call.script(), "Мост ставит скрипт bridge, а не повторный dig_out");
        int length = (Integer) call.params().get("length");
        int span = Math.max(Math.abs(home.x() - feet.x()), Math.abs(home.z() - feet.z()));
        assertTrue(length >= span, "Мост обязан покрыть разрыв " + span + " клеток, а не " + length);
        assertTrue(length <= blocks, "Мост не длиннее запаса: на " + length + " клеток у духа " + blocks + " блоков");
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
