package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.util.List;
import org.junit.jupiter.api.Test;

class HighClearPlanTests {
    @Test
    void plan_ClearHigh_ClimbsClearsAndDismantlesOwnTower() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.CLEAR_HIGH,
                TicketTarget.at(HighClearFixture.SCAFFOLD.anchor()).withScaffold(HighClearFixture.SCAFFOLD));

        assertTrue(
                plan instanceof SequenceNode sequence && sequence.steps().size() == 3,
                "План подмости: подойти, " + "взять блоки на башню, снести с башни — три шага: " + plan);
        SequenceNode steps = (SequenceNode) plan;
        assertEquals("goto", ((ScriptCall) steps.steps().get(0)).script(), "Сначала дух идёт к точке башни");
        ScriptCall clear = (ScriptCall) steps.steps().get(2);
        assertEquals("clear_high", clear.script(), "Работа карточки — скрипт подмости, а не обычная расчистка");
        assertEquals(
                HighClearFixture.SCAFFOLD.id(),
                clear.params().get("scaffold"),
                "Скрипт знает id подмости: по нему он ставит и снимает защиту");
        assertEquals(19, clear.params().get("height"), "Высота башни идёт из плана подмостей");
        assertEquals(List.of("10,79,-5"), clear.params().get("cells"), "Снос идёт по клеткам столбов сверху вниз");
        assertEquals(
                List.of(
                        "11,78,-5",
                        "11,77,-5",
                        "11,76,-5",
                        "11,75,-5",
                        "11,74,-5",
                        "11,73,-5",
                        "11,72,-5",
                        "11,71,-5",
                        "11,70,-5",
                        "11,69,-5",
                        "11,68,-5",
                        "11,67,-5",
                        "11,66,-5",
                        "11,65,-5",
                        "11,64,-5",
                        "11,63,-5",
                        "11,62,-5",
                        "11,61,-5",
                        "11,60,-5"),
                clear.params().get("tower"),
                "Своя башня разбирается сверху вниз: последним снимается нижний блок");
    }

    @Test
    void plan_ClearHigh_PassesScriptCatalog() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.CLEAR_HIGH,
                TicketTarget.at(HighClearFixture.SCAFFOLD.anchor()).withScaffold(HighClearFixture.SCAFFOLD));

        TaskNode validated = PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());

        assertTrue(
                validated instanceof SequenceNode sequence && sequence.steps().size() == 3,
                "План подмости "
                        + "обязан пройти каталог без перестройки: иначе доска выдаст задание, которое валидатор отвергнет");
    }
}
