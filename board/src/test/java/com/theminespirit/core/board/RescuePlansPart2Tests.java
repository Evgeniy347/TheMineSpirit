package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.movement.SelfEscape;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.api.ConditionCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RescuePlansPart2Tests {
    private static final BlockCoord TRAPPED = new BlockCoord(10, 40, -5);

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

    @Test
    void plan_RescueCase_OpensTwoHighBoxAroundTrapped() {
        TaskNode plan = RescuePlans.plan(BoardCase.RESCUE_SPIRIT, TicketTarget.at(TRAPPED))
                .orElseThrow();
        Map<String, Object> params = clearArea(plan);
        assertEquals(
                9,
                params.get("x1"),
                "слева от духа клетка вскрывается: стена не держит его Иначе тест plan_RescueCase_OpensTwoHighBoxAroundTrapped перестанет ловить эту поломку. При смене plan_RescueCase_OpensTwoHighBoxAroundTrapped проверь это утверждение.");
        assertEquals(
                40,
                params.get("y1"),
                "пол под духом не трогаем: нижняя граница на уровне ног Иначе тест plan_RescueCase_OpensTwoHighBoxAroundTrapped перестанет ловить эту поломку. При смене plan_RescueCase_OpensTwoHighBoxAroundTrapped проверь это утверждение.");
        assertEquals(
                -6,
                params.get("z1"),
                "по z клетка вокруг духа тоже вскрывается Иначе тест plan_RescueCase_OpensTwoHighBoxAroundTrapped перестанет ловить эту поломку. При смене plan_RescueCase_OpensTwoHighBoxAroundTrapped проверь это утверждение.");
        assertEquals(
                11,
                params.get("x2"),
                "справа от духа клетка вскрывается Иначе тест plan_RescueCase_OpensTwoHighBoxAroundTrapped перестанет ловить эту поломку. При смене plan_RescueCase_OpensTwoHighBoxAroundTrapped проверь это утверждение.");
        assertEquals(
                41,
                params.get("y2"),
                "потолок над духом вскрывается: проход высотой в два блока Иначе тест plan_RescueCase_OpensTwoHighBoxAroundTrapped перестанет ловить эту поломку. При смене plan_RescueCase_OpensTwoHighBoxAroundTrapped проверь это утверждение.");
        assertEquals(
                -4,
                params.get("z2"),
                "по z с другой стороны клетка тоже вскрывается Иначе тест plan_RescueCase_OpensTwoHighBoxAroundTrapped перестанет ловить эту поломку. При смене plan_RescueCase_OpensTwoHighBoxAroundTrapped проверь это утверждение.");
    }

    @Test
    void plan_SelfStep_PlacesStepsFirst() {
        SelfEscape.Plan self = new SelfEscape.Plan(SelfEscape.Method.STEP, new BlockCoord(10, 67, -5), 2, List.of());
        TaskNode plan = RescuePlans.plan(
                        BoardCase.RESCUE_SPIRIT, TicketTarget.at(TRAPPED).withRescue(self))
                .orElseThrow();
        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                List.of("acquire", "pillar_up", "goto"),
                scripts(plan),
                "самоспасение из ямы — сначала земля на столб, потом ступени, потом шаг на край Иначе тест plan_SelfStep_PlacesStepsFirst перестанет ловить эту поломку. При смене plan_SelfStep_PlacesStepsFirst проверь это утверждение.");
    }

    @Test
    void plan_SelfDescend_DigOutThenGoto() {
        SelfEscape.Plan self = new SelfEscape.Plan(SelfEscape.Method.DESCEND, new BlockCoord(10, 40, -5), 0, List.of());
        BlockCoord home = new BlockCoord(10, 39, -5);
        TaskNode plan = RescuePlans.plan(
                        BoardCase.RESCUE_SPIRIT,
                        TicketTarget.at(TRAPPED).withRescue(self).withRelocate(TRAPPED, home))
                .orElseThrow();
        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                List.of("dig_out", "goto"),
                scripts(plan),
                "с крыши дух спускается через dig_out и только потом идёт к поселению Иначе тест plan_SelfDescend_DigOutThenGoto перестанет ловить эту поломку. При смене plan_SelfDescend_DigOutThenGoto проверь это утверждение.");
        List<ScriptCall> calls = new ArrayList<>();
        collectCalls(plan, calls);
        assertEquals(
                Map.of("x", home.x(), "y", home.y(), "z", home.z(), "rescue", true),
                calls.get(0).params(),
                "спуск ведёт к дому на земле, а не к крыше Иначе тест plan_SelfDescend_DigOutThenGoto перестанет ловить эту поломку. При смене plan_SelfDescend_DigOutThenGoto проверь это утверждение.");
    }

    @Test
    void plan_SelfGoto_WalksWithoutBreaking() {
        SelfEscape.Plan self = new SelfEscape.Plan(SelfEscape.Method.GOTO, new BlockCoord(10, 67, -5), 0, List.of());
        TaskNode plan = RescuePlans.plan(
                        BoardCase.RESCUE_SPIRIT, TicketTarget.at(TRAPPED).withRescue(self))
                .orElseThrow();
        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                List.of("goto"),
                scripts(plan),
                "Стоящий в открытом месте дух спасается шагом к цели, без ломки блоков постройки Иначе тест plan_SelfGoto_WalksWithoutBreaking перестанет ловить эту поломку. При смене plan_SelfGoto_WalksWithoutBreaking проверь это утверждение.");
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
        assertEquals(
                "goto",
                last.script(),
                "после выхода из ямы дух идёт к поселению Иначе тест plan_SelfStep_ReturnsToSettlementAfterPit перестанет ловить эту поломку. При смене plan_SelfStep_ReturnsToSettlementAfterPit проверь это утверждение.");
        assertEquals(
                Map.of("x", home.x(), "y", home.y(), "z", home.z()),
                last.params(),
                "спасение возвращает духа к поселению, а не оставляет на краю ямы (observe11 N4) Иначе тест plan_SelfStep_ReturnsToSettlementAfterPit перестанет ловить эту поломку. При смене plan_SelfStep_ReturnsToSettlementAfterPit проверь это утверждение.");
        assertTrue(
                scripts(plan).contains("dig_out"),
                "из ямы дух выбирается на поверхность, а не остаётся в пещере Иначе тест plan_SelfStep_ReturnsToSettlementAfterPit перестанет ловить эту поломку. При смене plan_SelfStep_ReturnsToSettlementAfterPit проверь это утверждение.");
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
                "самоспасение из стены — сломать ноги и голову клетки впереди, затем шагнуть в проход Иначе тест plan_SelfDigWalled_BreaksTwoCellsThenWalks перестанет ловить эту поломку. При смене plan_SelfDigWalled_BreaksTwoCellsThenWalks проверь это утверждение.");
    }

    @Test
    void walk_PathHome_IsWalkCarefulWithoutDigging() {
        TaskNode plan = RescuePlans.walk(TRAPPED);
        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                List.of("walk_careful"),
                scripts(plan),
                "Путь домой уже открыт: ведомый идёт по нему и не трогает мир Иначе тест walk_PathHome_IsWalkCarefulWithoutDigging перестанет ловить эту поломку. При смене walk_PathHome_IsWalkCarefulWithoutDigging проверь это утверждение.");
    }

    @Test
    void priority_RescueSpirit_IsHighest() {
        assertTrue(
                Arrays.stream(BoardCase.values())
                        .allMatch(other ->
                                BoardRules.priorityOf(BoardCase.RESCUE_SPIRIT) >= BoardRules.priorityOf(other)),
                "спасение духа — самый высокий приоритет: застрявший дух не работает, пока его не выручат Иначе тест priority_RescueSpirit_IsHighest перестанет ловить эту поломку. При смене priority_RescueSpirit_IsHighest проверь это утверждение.");
    }

    @Test
    void helper_RestoredPlan_IsSameAsPostedPlan() {
        BlockCoord home = new BlockCoord(10, 64, -5);
        TaskNode posted = RescuePlans.helper(TRAPPED, home);
        TaskNode restored = RescuePlans.plan(
                        BoardCase.RESCUE_SPIRIT, TicketTarget.at(TRAPPED).withRelocate(TRAPPED, home))
                .orElseThrow();
        PlanValidator.validate(posted, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                scripts(restored),
                scripts(posted),
                "План помощника один: выставление задания и восстановление из NBT не расходятся Иначе тест helper_RestoredPlan_IsSameAsPostedPlan перестанет ловить эту поломку. При смене helper_RestoredPlan_IsSameAsPostedPlan проверь это утверждение.");
        assertEquals(
                List.of("goto", "wait", "clear_area", "goto"),
                scripts(posted),
                "Помощник идёт к духу, вскрывает клетку и возвращается к дому; копать вниз он не начинает Иначе тест helper_RestoredPlan_IsSameAsPostedPlan перестанет ловить эту поломку. При смене helper_RestoredPlan_IsSameAsPostedPlan проверь это утверждение.");
        assertFalse(
                scripts(posted).contains("dig_out"),
                "В плане помощника нет dig_out: путь вниз на десятки блоков — причина провала 0 из 6 (observe24 B3) Иначе тест helper_RestoredPlan_IsSameAsPostedPlan перестанет ловить эту поломку. При смене helper_RestoredPlan_IsSameAsPostedPlan проверь это утверждение.");
    }

    @Test
    void helper_WithoutHome_StaysAtTrapped() {
        TaskNode plan = RescuePlans.helper(TRAPPED, null);
        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                List.of("goto", "wait", "clear_area"),
                scripts(plan),
                "Без дома помощник вскрывает клетку на месте, а не уходит в пустоту Иначе тест helper_WithoutHome_StaysAtTrapped перестанет ловить эту поломку. При смене helper_WithoutHome_StaysAtTrapped проверь это утверждение.");
        assertFalse(
                scripts(plan).contains("dig_out"),
                "Без дома помощник не копает вниз к застрявшему: бездну не пробивают (observe24 B3) Иначе тест helper_WithoutHome_StaysAtTrapped перестанет ловить эту поломку. При смене helper_WithoutHome_StaysAtTrapped проверь это утверждение.");
    }

    @Test
    void claimNote_Helper_NamesHelperAndRescuee() {
        UUID helper = new UUID(0, 1);
        UUID rescuee = new UUID(0, 2);
        assertEquals(
                "Айвенго назначен помощником Мирный",
                RescuePlans.claimNote(helper, rescuee, "Айвенго", "Мирный"),
                "Журнал выдачи называет имя помощника: без него непонятно, кто выручает Иначе тест claimNote_Helper_NamesHelperAndRescuee перестанет ловить эту поломку. При смене claimNote_Helper_NamesHelperAndRescuee проверь это утверждение.");
    }

    @Test
    void claimNote_Self_NamesRescueeOnly() {
        UUID rescuee = new UUID(0, 2);
        assertEquals(
                "Мирный спасает себя",
                RescuePlans.claimNote(rescuee, rescuee, "Мирный", "Мирный"),
                "Выдача самому себе не выдаёт себя за помощника Иначе тест claimNote_Self_NamesRescueeOnly перестанет ловить эту поломку. При смене claimNote_Self_NamesRescueeOnly проверь это утверждение.");
    }
}
