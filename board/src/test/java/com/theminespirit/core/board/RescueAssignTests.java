package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RescueAssignTests {
    private static final UUID HELPER = UUID.fromString("f58b83b6-c9a0-406d-967a-822843debf54");
    private static final BlockCoord TRAPPED = new BlockCoord(10, 40, -5);

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
    void self_SurfaceNoPathWithTool_WalkOtherRoute() {
        assertEquals(
                RescuePlans.Self.WALK,
                RescuePlans.self(new RescuePlans.Situation(false, false, true, true, true)),
                "На поверхности путь ищут заново другим маршрутом: кирка нужна под землёй, а не на земле");
    }

    @Test
    void self_SurfaceNoPathWithoutTool_WalkOtherRoute() {
        assertEquals(
                RescuePlans.Self.WALK,
                RescuePlans.self(new RescuePlans.Situation(false, false, false, true, true)),
                "Потерянный на поверхности дух без кирки возвращается другим маршрутом, а не объявляется "
                        + "невозможным (observe25 B3)");
    }

    @Test
    void self_SealedWithoutTool_None() {
        assertEquals(
                RescuePlans.Self.NONE,
                RescuePlans.self(new RescuePlans.Situation(false, false, false, false, false)),
                "Под землёй без кирки выхода нет: повторять self-rescue бессмысленно (observe23 A)");
    }

    @Test
    void self_SurfaceNoPathOnOwnPillar_DescendsBeforeBridge() {
        assertEquals(
                RescuePlans.Self.DESCEND,
                RescuePlans.self(new RescuePlans.Situation(false, false, true, true, true, 10, true)),
                "Дух на своём столбе без пути домой обязан разобрать столб и спуститься: мост по воздуху "
                        + "дороже и опаснее, даже когда блоки есть");
        assertEquals(
                RescuePlans.Self.DESCEND,
                RescuePlans.self(new RescuePlans.Situation(false, false, false, false, true, 0, true)),
                "Свой столб разбирают и под крышей: небо — не условие спуска, иначе дух уходит в отказ");
    }

    @Test
    void self_SurfaceNoPathOnOwnPillarWithoutBlocks_Descends() {
        assertEquals(
                RescuePlans.Self.DESCEND,
                RescuePlans.self(new RescuePlans.Situation(false, false, true, true, true, 0, true)),
                "Без блоков моста не построить: собственный столб — единственный честный спуск");
    }

    @Test
    void descent_Plan_RunsPillarDownThenReturnsHome() {
        TaskNode plan = RescuePlans.descent(TRAPPED);

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                List.of("pillar_down", "dig_out", "goto"),
                scripts(plan),
                "Спуск со своего столба: сначала пошаговый разбор под собой, затем дорога к дому; "
                        + "мост не строится");
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
        int span = Math.max(Math.abs(home.x() - feet.x()), Math.abs(home.z() - feet.z()));

        assertTrue(
                lost.get("inventory").get("minecraft:dirt").asInt() < span,
                "живая фикстура: у spirit-4 "
                        + lost.get("inventory").get("minecraft:dirt").asInt() + " земли при разрыве " + span
                        + " — одного вида не хватает, мост обязан смешивать блоки");
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
        assertFalse(
                call.params().containsKey("block"),
                "мост не привязан к одному виду блока: у spirit-4 26 земли и 8 досок, мост обязан смешивать запас");
        int length = (Integer) call.params().get("length");
        assertTrue(length >= span, "Мост обязан покрыть разрыв " + span + " клеток, а не " + length);
        assertTrue(length <= blocks, "Мост не длиннее запаса: на " + length + " клеток у духа " + blocks + " блоков");
    }

    @Test
    void self_UndergroundWithBlocks_None() {
        assertEquals(
                RescuePlans.Self.NONE,
                RescuePlans.self(new RescuePlans.Situation(false, false, false, false, true, 38)),
                "под землёй блоки не заменяют кирку: мост строят на поверхности, в толще нужен проход");
    }

    @Test
    void situation_NegativeBlocks_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RescuePlans.Situation(false, false, false, true, true, -1),
                "отрицательный запас блоков — ошибка входа: молча превращать его в ноль значит врать о мосте");
    }

    @Test
    void bridge_NoBlocks_Rejected() {
        BlockCoord feet = new BlockCoord(0, 64, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> RescuePlans.bridge(feet, new BlockCoord(10, 64, 0), 0),
                "мост без материала не начинают: пустой bridge-вызов только сожжёт попытку спасения");
    }

    @Test
    void bridge_StockShorterThanSpan_CappedByStock() {
        BlockCoord feet = new BlockCoord(0, 64, 0);
        ScriptCall call = assertInstanceOf(
                ScriptCall.class,
                RescuePlans.bridge(feet, new BlockCoord(30, 64, 0), 5),
                "мост к дому остаётся одним скриптом bridge");

        assertEquals(
                5,
                call.params().get("length"),
                "мост не длиннее запаса: на 30 клеток разрыва у духа 5 блоков — ставит 5, дальше решает следующий прогон");
    }

    @Test
    void bridge_SpanBeyondCatalogLimit_CappedByLimit() {
        BlockCoord feet = new BlockCoord(0, 64, 0);
        ScriptCall call = assertInstanceOf(
                ScriptCall.class,
                RescuePlans.bridge(feet, new BlockCoord(200, 64, 0), 500),
                "дальний дом тоже получает bridge-план, а не отказ");

        assertEquals(
                CatalogLimits.BRIDGE_LENGTH_MAX,
                call.params().get("length"),
                "длина моста не выходит за предел каталога: PlanValidator отверг бы такой план");
    }

    private static List<String> scripts(TaskNode plan) {
        List<String> names = new ArrayList<>();
        collect(plan, names);
        return names;
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

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
