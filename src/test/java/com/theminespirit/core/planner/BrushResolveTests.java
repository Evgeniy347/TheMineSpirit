package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.BrushParams;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BrushResolveTests {
    @Test
    void resolve_ArmadilloScute_BrushCall() {
        TaskNode plan = ResourceResolver.resolve(
                "armadillo_scute", 3, new Brushed(), CoreScripts.defaultCatalog(), ConditionCatalog.empty());

        ScriptCall call = assertInstanceOf(ScriptCall.class, plan, "Щетину броненосца добывают шагом brush");
        assertEquals("brush", call.script(), "Предмет броненосца — не блок и не рецепт: путь добычи — щётка");
        assertEquals(3, call.params().get("count"), "Число щетин из задания передаётся щётке");
    }

    @Test
    void resolve_OtherItem_NotBrushed() {
        assertThrows(
                UnplannableGoalException.class,
                () -> ResourceResolver.resolve(
                        "bone", 1, new Brushed(), CoreScripts.defaultCatalog(), ConditionCatalog.empty()),
                "brush не закоулок для всех предметов: кость добывается не щёткой");
    }

    @Test
    void brushParams_ValidRadiusAndCount_Accepted() {
        assertEquals(
                new BrushParams(16, 4),
                BrushParams.from(Map.of("radius", 16, "count", 4)),
                "Параметры щётки — радиус поиска и число щетин");
        assertThrows(
                IllegalArgumentException.class,
                () -> new BrushParams(0, 1),
                "Нулевой радиус щётки отвергается: броненосца в нуле не найти");
        assertThrows(
                IllegalArgumentException.class,
                () -> new BrushParams(16, 65),
                "Больше 64 щетин за запуск не чистим: это предел счётных задач");
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
