package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.SelfEscape;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RescuePlansTests {
    private static final BlockCoord TRAPPED = new BlockCoord(10, 40, -5);

    @Test
    void plan_RescueCase_ReturnsPlan() {
        TaskNode plan = RescuePlans.plan(BoardCase.RESCUE_SPIRIT, TicketTarget.at(TRAPPED))
                .orElseThrow();

        assertTrue(plan instanceof SequenceNode, "спасение — последовательность: подойти, вскрыть клетку, подойти");
    }

    @Test
    void plan_OtherCase_IsEmpty() {
        assertTrue(
                RescuePlans.plan(BoardCase.REPAIR_FENCE, TicketTarget.at(TRAPPED))
                        .isEmpty(),
                "чужой кейс не получает план спасения: иначе доска подменит ремонт ограды");
    }

    @Test
    void plan_RescueCase_PassesCatalog() {
        TaskNode plan = RescuePlans.plan(BoardCase.RESCUE_SPIRIT, TicketTarget.at(TRAPPED))
                .orElseThrow();

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());

        assertEquals(
                List.of("goto", "wait", "clear_area", "goto"),
                scripts(plan),
                "план спасения проходит каталог: подойти, вскрыть клетку вокруг духа, снова подойти");
    }

    @Test
    void plan_RescueCase_OpensTwoHighBoxAroundTrapped() {
        TaskNode plan = RescuePlans.plan(BoardCase.RESCUE_SPIRIT, TicketTarget.at(TRAPPED))
                .orElseThrow();

        Map<String, Object> params = clearArea(plan);

        assertEquals(9, params.get("x1"), "слева от духа клетка вскрывается: стена не держит его");
        assertEquals(40, params.get("y1"), "пол под духом не трогаем: нижняя граница на уровне ног");
        assertEquals(-6, params.get("z1"), "по z клетка вокруг духа тоже вскрывается");
        assertEquals(11, params.get("x2"), "справа от духа клетка вскрывается");
        assertEquals(41, params.get("y2"), "потолок над духом вскрывается: проход высотой в два блока");
        assertEquals(-4, params.get("z2"), "по z с другой стороны клетка тоже вскрывается");
    }

    @Test
    void plan_SelfStep_PlacesStepsFirst() {
        SelfEscape.Plan self = new SelfEscape.Plan(SelfEscape.Method.STEP, new BlockCoord(10, 67, -5), 2, List.of());

        TaskNode plan = RescuePlans.plan(
                        BoardCase.RESCUE_SPIRIT, TicketTarget.at(TRAPPED).withRescue(self))
                .orElseThrow();

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                List.of("pillar_up", "goto"),
                scripts(plan),
                "самоспасение из ямы — сначала ступени, потом шаг на край");
    }

    @Test
    void plan_SelfStep_ReturnsToSettlementAfterPit() {
        SelfEscape.Plan self = new SelfEscape.Plan(SelfEscape.Method.STEP, new BlockCoord(10, 67, -5), 2, List.of());
        BlockCoord home = new BlockCoord(10, 69, -5);

        TaskNode plan = RescuePlans.plan(
                        BoardCase.RESCUE_SPIRIT,
                        TicketTarget.at(TRAPPED).withRescue(self).withRelocate(TRAPPED, home))
                .orElseThrow();

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        List<ScriptCall> calls = new ArrayList<>();
        collectCalls(plan, calls);
        ScriptCall last = calls.get(calls.size() - 1);
        assertEquals("goto", last.script(), "после выхода из ямы дух идёт к поселению");
        assertEquals(
                Map.of("x", home.x(), "y", home.y(), "z", home.z()),
                last.params(),
                "спасение возвращает духа к поселению, а не оставляет на краю ямы (observe11 N4)");
        assertTrue(scripts(plan).contains("dig_out"), "из ямы дух выбирается на поверхность, а не остаётся в пещере");
    }

    @Test
    void plan_SelfDigWalled_BreaksTwoCellsThenWalks() {
        SelfEscape.Plan self = new SelfEscape.Plan(
                SelfEscape.Method.DIG,
                new BlockCoord(11, 40, -5),
                0,
                List.of(new BlockCoord(11, 40, -5), new BlockCoord(11, 41, -5)));

        TaskNode plan = RescuePlans.plan(
                        BoardCase.RESCUE_SPIRIT, TicketTarget.at(TRAPPED).withRescue(self))
                .orElseThrow();

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                List.of("dig_area", "dig_area", "goto"),
                scripts(plan),
                "самоспасение из стены — сломать ноги и голову клетки впереди, затем шагнуть в проход");
    }

    @Test
    void priority_RescueSpirit_IsHighest() {
        assertEquals(
                100,
                BoardRules.priorityOf(BoardCase.RESCUE_SPIRIT),
                "спасение духа — самый высокий приоритет: застрявший дух не работает, пока его не выручат");
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

    private static Map<String, Object> clearArea(TaskNode plan) {
        List<ScriptCall> calls = new ArrayList<>();
        collectCalls(plan, calls);
        return calls.stream()
                .filter(call -> RescuePlans.CLEAR_AREA.equals(call.script()))
                .findFirst()
                .orElseThrow()
                .params();
    }

    private static void collectCalls(TaskNode plan, List<ScriptCall> calls) {
        if (plan instanceof ScriptCall call) {
            calls.add(call);
        } else if (plan instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collectCalls(step, calls));
        } else if (plan instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collectCalls(step, calls));
        }
    }
}
