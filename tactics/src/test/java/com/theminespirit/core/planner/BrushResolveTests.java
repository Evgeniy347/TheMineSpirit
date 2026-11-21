package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.api.Ingredient;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import com.theminespirit.core.planner.api.UnplannableGoalException;
import com.theminespirit.core.script.BrushParams;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.api.ConditionCatalog;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BrushResolveTests {
    @Test
    void resolve_ArmadilloScute_BrushCall() {
        TaskNode plan = ResourceResolver.resolve(
                "armadillo_scute", 3, new Brushed(), CoreScripts.defaultCatalog(), ConditionCatalog.empty());

        ScriptCall call = assertInstanceOf(
                ScriptCall.class,
                plan,
                "Щетину броненосца добывают шагом brush"
                        + " Иначе тест resolve_ArmadilloScute_BrushCall перестанет ловить эту поломку. При смене resolve_ArmadilloScute_BrushCall проверь это утверждение.");
        assertEquals(
                "brush",
                call.script(),
                "Предмет броненосца — не блок и не рецепт: путь добычи — щётка"
                        + " Иначе тест resolve_ArmadilloScute_BrushCall перестанет ловить эту поломку. При смене resolve_ArmadilloScute_BrushCall проверь это утверждение.");
        assertEquals(
                3,
                call.params().get("count"),
                "Число щетин из задания передаётся щётке"
                        + " Иначе тест resolve_ArmadilloScute_BrushCall перестанет ловить эту поломку. При смене resolve_ArmadilloScute_BrushCall проверь это утверждение.");
    }

    @Test
    void resolve_OtherItem_NotBrushed() {
        assertThrows(
                UnplannableGoalException.class,
                () -> ResourceResolver.resolve(
                        "bone", 1, new Brushed(), CoreScripts.defaultCatalog(), ConditionCatalog.empty()),
                "brush не закоулок для всех предметов: кость добывается не щёткой"
                        + " Иначе тест resolve_OtherItem_NotBrushed перестанет ловить эту поломку. При смене resolve_OtherItem_NotBrushed проверь это утверждение.");
    }

    @Test
    void brushParams_ValidRadiusAndCount_Accepted() {
        assertEquals(
                new BrushParams(16, 4),
                BrushParams.from(Map.of("radius", 16, "count", 4)),
                "Параметры щётки — радиус поиска и число щетин"
                        + " Иначе тест brushParams_ValidRadiusAndCount_Accepted перестанет ловить эту поломку. При смене brushParams_ValidRadiusAndCount_Accepted проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new BrushParams(0, 1),
                "Нулевой радиус щётки отвергается: броненосца в нуле не найти"
                        + " Иначе тест brushParams_ValidRadiusAndCount_Accepted перестанет ловить эту поломку. При смене brushParams_ValidRadiusAndCount_Accepted проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new BrushParams(16, 65),
                "Больше 64 щетин за запуск не чистим: это предел счётных задач"
                        + " Иначе тест brushParams_ValidRadiusAndCount_Accepted перестанет ловить эту поломку. При смене brushParams_ValidRadiusAndCount_Accepted проверь это утверждение.");
    }

    private static final class Brushed implements ResourceKnowledge {
        @Override
        public boolean isGatherable(String item) {
            return false;
        }

        @Override
        public boolean isBrushed(String item) {
            return "armadillo_scute".equals(item);
        }

        @Override
        public boolean isCraftable(String item) {
            return false;
        }

        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return List.of();
        }

        @Override
        public boolean isSmeltable(String item) {
            return false;
        }

        @Override
        public String smeltInputFor(String item) {
            return item;
        }

        @Override
        public Optional<String> toolForBlock(String block) {
            return Optional.empty();
        }
    }
}
