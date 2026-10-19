package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildMaterialsTests {
    @Test
    void plan_AllPresent_GivesEmptySteps() {
        BuildSupplies supplies = stub(8, List.of());

        BuildMaterials.Plan plan = BuildMaterials.plan("cobblestone", 5, supplies);

        assertEquals(
                List.of(),
                plan.steps(),
                "Когда материала хватает, шагов withdraw быть не должно: иначе дух ходит к сундукам без нужды");
        assertEquals(
                0,
                plan.missing(),
                "Когда материала хватает, нехватка обязана быть 0: иначе стройка откажет при полном инвентаре");
        assertEquals(5, plan.have(), "have обязан ограничиваться нужным числом: иначе итог врёт о запасах");
    }

    @Test
    void plan_PartiallyPresent_StepsForRemainder() {
        ScriptCall step = new ScriptCall("withdraw", Map.of("item", "cobblestone", "count", 5));
        BuildSupplies supplies = stub(3, List.of(step));

        BuildMaterials.Plan plan = BuildMaterials.plan("cobblestone", 8, supplies);

        assertEquals(
                List.of(step),
                plan.steps(),
                "При частичной нехватке шаги обязаны запрашивать остаток: иначе дух идёт за всем объёмом заново");
        assertEquals(
                5,
                plan.missing(),
                "Нехватка обязана равняться нужному минус своё: иначе отказ называет неверные числа");
    }

    @Test
    void plan_NothingPresent_MissingEqualsNeeded() {
        BuildSupplies supplies = stub(0, List.of());

        BuildMaterials.Plan plan = BuildMaterials.plan("cobblestone", 8, supplies);

        assertEquals(
                8,
                plan.missing(),
                "При пустом инвентаре нехватка обязана равняться нужному: иначе стратег не узнает, сколько нести");
    }

    @Test
    void plan_StepOrder_PreservedAsGiven() {
        ScriptCall first = new ScriptCall("withdraw", Map.of("item", "cobblestone", "count", 2));
        ScriptCall second = new ScriptCall("withdraw", Map.of("item", "cobblestone", "count", 3));

        BuildMaterials.Plan plan = BuildMaterials.plan("cobblestone", 8, stub(3, List.of(first, second)));

        assertEquals(
                List.of(first, second),
                plan.steps(),
                "Порядок шагов обязан сохраняться: ближний сундук идёт первым, перестановка гонит духа далеко");
    }

    @Test
    void plan_NullArguments_AreRejected() {
        assertThrows(
                NullPointerException.class,
                () -> BuildMaterials.plan(null, 5, stub(5, List.of())),
                "plan без id блока обязан отказывать: без id неизвестно, что считать");
        assertThrows(
                NullPointerException.class,
                () -> BuildMaterials.plan("cobblestone", 5, null),
                "plan без снабжения обязан отказывать: без него неизвестно, сколько материала есть");
    }

    @Test
    void plan_BlankBlock_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildMaterials.plan("   ", 5, stub(5, List.of())),
                "Пустой id блока обязан отвергаться: по пробелам материал не найти");
    }

    private static BuildSupplies stub(int have, List<ScriptCall> steps) {
        return new BuildSupplies() {
            @Override
            public int inInventory(String block) {
                return have;
            }

            @Override
            public List<ScriptCall> withdraw(String block, int maxCount) {
                assertTrue(maxCount >= 0, "Запрос withdraw обязан быть неотрицательным");
                return steps;
            }
        };
    }
}
