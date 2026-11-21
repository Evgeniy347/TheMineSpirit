package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.api.CastObsidianParams;
import com.theminespirit.core.script.api.LightPortalParams;
import com.theminespirit.core.script.api.ScriptCatalog;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortalPlansTests {
    private static final TicketTarget TARGET = TicketTarget.at(new BlockCoord(10, 64, -5));

    private static List<TaskNode> steps(TicketTarget target, String item, int count) {
        TaskNode plan = PortalPlans.special(target, item, count).orElseThrow();
        return assertInstanceOf(
                        SequenceNode.class,
                        plan,
                        "План портала обязан быть последовательностью шагов: " + plan
                                + " Иначе тест механику перестанет ловить эту поломку. При смене механику проверь это утверждение.")
                .steps();
    }

    private static ScriptCall call(TaskNode node) {
        return assertInstanceOf(
                ScriptCall.class,
                node,
                "Шаг плана портала обязан быть вызовом скрипта: " + node
                        + " Иначе тест механику перестанет ловить эту поломку. При смене механику проверь это утверждение.");
    }

    @Test
    void castObsidian_Plan_WithdrawsWaterThenCasts() {
        List<TaskNode> steps = steps(TARGET, PortalPlans.OBSIDIAN, 10);

        assertEquals(
                3,
                steps.size(),
                "План обсидиана: дойти, взять ведро воды, залить и добыть: " + steps
                        + " Иначе тест castObsidian_Plan_WithdrawsWaterThenCasts перестанет ловить эту поломку. При смене castObsidian_Plan_WithdrawsWaterThenCasts проверь это утверждение.");
        assertEquals(
                "goto",
                call(steps.get(0)).script(),
                "Сначала дух идёт к месту добычи"
                        + " Иначе тест castObsidian_Plan_WithdrawsWaterThenCasts перестанет ловить эту поломку. При смене castObsidian_Plan_WithdrawsWaterThenCasts проверь это утверждение.");
        FallbackNode withdraw = assertInstanceOf(
                FallbackNode.class,
                steps.get(1),
                "Взятие воды из сундука идёт запасным шагом: " + steps.get(1)
                        + " Иначе тест castObsidian_Plan_WithdrawsWaterThenCasts перестанет ловить эту поломку. При смене castObsidian_Plan_WithdrawsWaterThenCasts проверь это утверждение.");
        assertEquals(
                "withdraw",
                call(withdraw.steps().get(0)).script(),
                "Перед заливкой дух обязан взять ведро воды из сундука"
                        + " Иначе тест castObsidian_Plan_WithdrawsWaterThenCasts перестанет ловить эту поломку. При смене castObsidian_Plan_WithdrawsWaterThenCasts проверь это утверждение.");
        ScriptCall cast = call(steps.get(2));
        assertEquals(
                "cast_obsidian",
                cast.script(),
                "Последний шаг — залить лаву и сломать обсидиан: " + cast
                        + " Иначе тест castObsidian_Plan_WithdrawsWaterThenCasts перестанет ловить эту поломку. При смене castObsidian_Plan_WithdrawsWaterThenCasts проверь это утверждение.");
        assertEquals(
                10,
                cast.params().get("count"),
                "Скрипт делает столько обсидиана, сколько просит доска"
                        + " Иначе тест castObsidian_Plan_WithdrawsWaterThenCasts перестанет ловить эту поломку. При смене castObsidian_Plan_WithdrawsWaterThenCasts проверь это утверждение.");
        assertEquals(
                CastObsidianParams.RADIUS_DEFAULT,
                cast.params().get("radius"),
                "Радиус поиска лавы задан каталогом, а не выдуман в плане"
                        + " Иначе тест castObsidian_Plan_WithdrawsWaterThenCasts перестанет ловить эту поломку. При смене castObsidian_Plan_WithdrawsWaterThenCasts проверь это утверждение.");
    }

    @Test
    void lightPortal_Plan_WithdrawsFlintThenLights() {
        List<TaskNode> steps = steps(TARGET.withScheme(PortalPlans.PORTAL_LIT), "flint_and_steel", 1);

        assertEquals(
                3,
                steps.size(),
                "План портала: дойти до зала, взять огниво, поджечь рамку: " + steps
                        + " Иначе тест lightPortal_Plan_WithdrawsFlintThenLights перестанет ловить эту поломку. При смене lightPortal_Plan_WithdrawsFlintThenLights проверь это утверждение.");
        assertEquals(
                "goto",
                call(steps.get(0)).script(),
                "Сначала дух идёт к залу портала"
                        + " Иначе тест lightPortal_Plan_WithdrawsFlintThenLights перестанет ловить эту поломку. При смене lightPortal_Plan_WithdrawsFlintThenLights проверь это утверждение.");
        FallbackNode withdraw = assertInstanceOf(
                FallbackNode.class,
                steps.get(1),
                "Взятие огнива из сундука идёт запасным шагом: " + steps.get(1)
                        + " Иначе тест lightPortal_Plan_WithdrawsFlintThenLights перестанет ловить эту поломку. При смене lightPortal_Plan_WithdrawsFlintThenLights проверь это утверждение.");
        assertEquals(
                "withdraw",
                call(withdraw.steps().get(0)).script(),
                "Перед поджигом дух обязан взять огниво из сундука"
                        + " Иначе тест lightPortal_Plan_WithdrawsFlintThenLights перестанет ловить эту поломку. При смене lightPortal_Plan_WithdrawsFlintThenLights проверь это утверждение.");
        ScriptCall light = call(steps.get(2));
        assertEquals(
                "light_portal",
                light.script(),
                "Последний шаг — поджечь рамку огнивом: " + light
                        + " Иначе тест lightPortal_Plan_WithdrawsFlintThenLights перестанет ловить эту поломку. При смене lightPortal_Plan_WithdrawsFlintThenLights проверь это утверждение.");
        assertEquals(
                LightPortalParams.RADIUS_DEFAULT,
                light.params().get("radius"),
                "Радиус поиска рамки задан каталогом, а не выдуман в плане"
                        + " Иначе тест lightPortal_Plan_WithdrawsFlintThenLights перестанет ловить эту поломку. При смене lightPortal_Plan_WithdrawsFlintThenLights проверь это утверждение.");
    }

    @Test
    void special_OrdinaryItem_NoPortalPlan() {
        assertEquals(
                Optional.empty(),
                PortalPlans.special(TARGET, "cobblestone", 1),
                "Обычный камень идёт обычным планом сбора: портальный план не перехватывает всё подряд"
                        + " Иначе тест special_OrdinaryItem_NoPortalPlan перестанет ловить эту поломку. При смене special_OrdinaryItem_NoPortalPlan проверь это утверждение.");
    }

    @Test
    void castParams_BadCountAndRadius_BothRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CastObsidianParams(0, 16),
                "Нулевое число обсидиана не заказывают: скрипт не должен стартовать без цели"
                        + " Иначе тест castParams_BadCountAndRadius_BothRejected перестанет ловить эту поломку. При смене castParams_BadCountAndRadius_BothRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new CastObsidianParams(1, 33),
                "Радиус поиска лавы выше 32 гоняет духа через полкарты"
                        + " Иначе тест castParams_BadCountAndRadius_BothRejected перестанет ловить эту поломку. При смене castParams_BadCountAndRadius_BothRejected проверь это утверждение.");
    }

    @Test
    void lightParams_BadRadius_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LightPortalParams(0),
                "Нулевой радиус не находит рамку портала: скрипт обязан отказаться до старта"
                        + " Иначе тест lightParams_BadRadius_Rejected перестанет ловить эту поломку. При смене lightParams_BadRadius_Rejected проверь это утверждение.");
    }

    @Test
    void catalog_PortalScripts_RegisteredWithDefaults() {
        ScriptCatalog catalog = CoreScripts.defaultCatalog();

        ScriptSpec cast = catalog.find("cast_obsidian")
                .orElseThrow(() -> new AssertionError("В каталоге нет cast_obsidian: план доски не пройдёт валидацию"));
        ScriptSpec light = catalog.find("light_portal")
                .orElseThrow(() -> new AssertionError("В каталоге нет light_portal: план доски не пройдёт валидацию"));

        assertTrue(
                cast.params().stream().anyMatch(param -> "count".equals(param.name()) && !param.required()),
                "count у cast_obsidian необязателен, значение по умолчанию задаёт каталог"
                        + " Иначе тест catalog_PortalScripts_RegisteredWithDefaults перестанет ловить эту поломку. При смене catalog_PortalScripts_RegisteredWithDefaults проверь это утверждение.");
        assertTrue(
                cast.params().stream().anyMatch(param -> "radius".equals(param.name())),
                "У cast_obsidian есть радиус"
                        + " Иначе тест catalog_PortalScripts_RegisteredWithDefaults перестанет ловить эту поломку. При смене catalog_PortalScripts_RegisteredWithDefaults проверь это утверждение.");
        assertTrue(
                light.params().stream().anyMatch(param -> "radius".equals(param.name())),
                "У light_portal есть радиус поиска рамки"
                        + " Иначе тест catalog_PortalScripts_RegisteredWithDefaults перестанет ловить эту поломку. При смене catalog_PortalScripts_RegisteredWithDefaults проверь это утверждение.");
        assertEquals(
                Map.of("count", 1, "radius", CastObsidianParams.RADIUS_DEFAULT),
                defaults(cast),
                "Умолчания cast_obsidian: один обсидиан и радиус каталога"
                        + " Иначе тест catalog_PortalScripts_RegisteredWithDefaults перестанет ловить эту поломку. При смене catalog_PortalScripts_RegisteredWithDefaults проверь это утверждение.");
    }

    private static Map<String, Object> defaults(ScriptSpec spec) {
        Map<String, Object> values = new LinkedHashMap<>();
        spec.params().stream()
                .filter(param -> param.defaultValue() != null)
                .forEach(param -> values.put(param.name(), param.defaultValue()));
        return values;
    }
}
