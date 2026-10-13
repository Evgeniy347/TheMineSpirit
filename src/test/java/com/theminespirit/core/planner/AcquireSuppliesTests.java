package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class AcquireSuppliesTests {
    @Test
    void inInventory_StubWithStock_ReturnsCounter() {
        AcquireSupplies supplies = stub(Map.of("oak_log", 7), Map.of());

        assertEquals(7, supplies.inInventory("oak_log"),
                "Снабжение обязано отдавать счётчик инвентаря: по нему acquire решает, добывать ли остаток");
    }

    @Test
    void inInventory_UnknownItem_ReturnsZero() {
        AcquireSupplies supplies = stub(Map.of(), Map.of());

        assertEquals(0, supplies.inInventory("diamond"),
                "Предмета нет в инвентаре — счётчик обязан быть 0, а не отрицательным или выбросом");
    }

    @Test
    void withdraw_StubWithChests_ReturnsSteps() {
        AcquireSupplies supplies = stub(Map.of(), Map.of("chestA", Map.of("oak_log", 4)));

        List<ScriptCall> steps = supplies.withdraw("oak_log", 3);

        assertEquals(1, steps.size(),
                "Сундук с предметом обязан дать ровно один шаг withdraw на acquire");
        assertEquals("withdraw", steps.get(0).script(),
                "Шаг снабжения — обычный вызов withdraw: движок не знает про acquire");
        assertEquals("chestA", steps.get(0).params().get("container"),
                "Шаг обязан называть контейнер, из которого берут предмет");
        assertEquals(3, steps.get(0).params().get("count"),
                "Шаг обязан просить не больше остатка цели: count = maxCount");
    }

    @Test
    void withdraw_NoChests_ReturnsEmptyList() {
        AcquireSupplies supplies = stub(Map.of(), Map.of());

        List<ScriptCall> steps = supplies.withdraw("oak_log", 3);

        assertTrue(steps.isEmpty(),
                "Контейнеров нет — пустой список шагов, а не шаг «в никуда»: acquire тогда идёт в мир");
    }

    @Test
    void withdraw_TwoChests_KeepsNearestFirstOrder() {
        AcquireSupplies supplies = stub(Map.of(), orderedChests("near", "far"));

        List<ScriptCall> steps = supplies.withdraw("oak_log", 5);

        assertEquals(List.of("near", "far"),
                steps.stream().map(step -> step.params().get("container")).toList(),
                "Порядок шагов обязан сохраняться: ближний сундук первым, иначе дух ходит за дальним, минуя ближний");
    }

    @Test
    void withdraw_NullItem_IsRejected() {
        AcquireSupplies supplies = stub(Map.of(), Map.of());

        assertThrows(NullPointerException.class, () -> supplies.withdraw(null, 1),
                "withdraw(null) обязан отказывать: без имени предмета переносить нечего");
    }

    @Test
    void inInventory_NullItem_IsRejected() {
        AcquireSupplies supplies = stub(Map.of(), Map.of());

        assertThrows(NullPointerException.class, () -> supplies.inInventory(null),
                "inInventory(null) обязан отказывать: без имени предмета счёт бессмыслен");
    }

    private static Map<String, Map<String, Integer>> orderedChests(String first, String second) {
        Map<String, Map<String, Integer>> chests = new LinkedHashMap<>();
        chests.put(first, Map.of("oak_log", 4));
        chests.put(second, Map.of("oak_log", 4));
        return chests;
    }

    private static AcquireSupplies stub(Map<String, Integer> inventory, Map<String, Map<String, Integer>> chests) {
        return new AcquireSupplies() {
            @Override
            public int inInventory(String item) {
                Objects.requireNonNull(item, "item не может быть null: счёт инвентаря идёт по имени предмета");
                return inventory.getOrDefault(item, 0);
            }

            @Override
            public List<ScriptCall> withdraw(String item, int maxCount) {
                Objects.requireNonNull(item, "item не может быть null: перенос идёт по имени предмета");
                List<ScriptCall> steps = new ArrayList<>();
                for (Map.Entry<String, Map<String, Integer>> chest : chests.entrySet()) {
                    int available = chest.getValue().getOrDefault(item, 0);
                    if (available <= 0) {
                        continue;
                    }
                    steps.add(new ScriptCall("withdraw", Map.of(
                            "container", chest.getKey(),
                            "item", item,
                            "count", Math.min(available, maxCount),
                            "radius", 8)));
                    maxCount -= available;
                    if (maxCount <= 0) {
                        break;
                    }
                }
                return List.copyOf(steps);
            }
        };
    }
}
