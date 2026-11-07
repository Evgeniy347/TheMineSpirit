package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DescentKitTests {
    private static final List<OutsideRules.Need> KIT = OutsideRules.defaults().mineKit();

    private static Stack stack(String item, int count) {
        return new Stack(item, 0, count, 64);
    }

    @Test
    void missing_FullKit_Empty() {
        List<Stack> inventory =
                List.of(stack("minecraft:iron_pickaxe", 1), stack("minecraft:torch", 8), stack("minecraft:bread", 3));

        assertEquals(
                List.of(),
                DescentKit.missing(KIT, inventory, ValueTable.load()),
                "Полный набор спуска нехватки не даёт: дух готов идти в шахту");
    }

    @Test
    void missing_NoTorches_ReportsNeed() {
        List<Stack> inventory = List.of(stack("minecraft:iron_pickaxe", 1), stack("minecraft:bread", 3));

        List<String> missing = DescentKit.missing(KIT, inventory, ValueTable.load());

        assertEquals(1, missing.size(), "Нехватка ровно одна — факелов, фактически: " + missing);
        assertTrue(
                missing.get(0).contains("torch") && missing.get(0).contains("0 из 8"),
                "Отчёт обязан назвать нехвату и числа, фактически: " + missing.get(0));
    }

    @Test
    void missing_PartialTorches_ReportsHaveOfNeed() {
        List<Stack> inventory =
                List.of(stack("minecraft:iron_pickaxe", 1), stack("minecraft:torch", 3), stack("minecraft:bread", 3));

        List<String> missing = DescentKit.missing(KIT, inventory, ValueTable.load());

        assertEquals(1, missing.size(), "Кирка и еда есть — не хватает только факелов, фактически: " + missing);
        assertTrue(
                missing.get(0).contains("3 из 8"),
                "Отчёт называет сколько есть и сколько нужно, фактически: " + missing.get(0));
    }

    @Test
    void missing_NoPickaxeNoFood_ReportsBoth() {
        List<Stack> inventory = List.of(stack("minecraft:torch", 8));

        List<String> missing = DescentKit.missing(KIT, inventory, ValueTable.load());

        assertEquals(2, missing.size(), "Нет кирки и еды — две нехватки, фактически: " + missing);
        assertTrue(
                missing.stream().anyMatch(line -> line.contains("pickaxe")),
                "Отчёт обязан назвать кирку, фактически: " + missing);
        assertTrue(
                missing.stream().anyMatch(line -> line.contains("food")),
                "Отчёт обязан назвать еду, фактически: " + missing);
    }

    @Test
    void supply_LeaderHasPickaxe_TakesTorchesAndFoodFromWarehouse() {
        List<Stack> inventory = List.of(stack("minecraft:iron_pickaxe", 1));
        Map<String, Integer> stock = Map.of(
                "minecraft:iron_pickaxe", 1,
                "minecraft:torch", 8,
                "minecraft:bread", 3);

        List<ScriptCall> steps = DescentKit.supply(KIT, inventory, stock, ValueTable.load());

        assertEquals(
                List.of("minecraft:torch", "minecraft:bread"),
                steps.stream().map(call -> (String) call.params().get("item")).toList(),
                "В поход добирается только то, чего у лидера нет: кирку при себе не перекладывают, "
                        + "факелы и еду берут со склада перед спуском");
        assertTrue(
                steps.stream()
                        .allMatch(call -> "withdraw".equals(call.script())
                                && "warehouse".equals(call.params().get("container"))),
                "Набор идёт шагом withdraw со склада поселения, а не местным крафтом мимо графа: " + steps);
        assertEquals(
                Map.of("minecraft:bread", 3, "minecraft:torch", 8),
                steps.stream()
                        .collect(Collectors.toMap(call -> (String) call.params().get("item"), call ->
                                (Integer) call.params().get("count"))),
                "Берут ровно нехватку набора: 8 факелов и 3 еды");
    }

    @Test
    void supply_FullKit_NoSteps() {
        List<Stack> inventory =
                List.of(stack("minecraft:iron_pickaxe", 1), stack("minecraft:torch", 8), stack("minecraft:bread", 3));

        assertEquals(
                List.of(),
                DescentKit.supply(
                        KIT, inventory, Map.of("minecraft:torch", 8, "minecraft:bread", 3), ValueTable.load()),
                "Полный набор у лидера — до старта ничего не забирают: лишний заход к складу задерживает поход");
    }

    @Test
    void missing_FoodCategory_CountsAnyFood() {
        List<Stack> inventory = List.of(
                stack("minecraft:iron_pickaxe", 1),
                stack("minecraft:torch", 8),
                stack("minecraft:cooked_beef", 2),
                stack("minecraft:bread", 1));

        assertEquals(
                List.of(),
                DescentKit.missing(KIT, inventory, ValueTable.load()),
                "Разная еда складывается по категории: три штуки любых продуктов закрывают нужду");
    }
}
