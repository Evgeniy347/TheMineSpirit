package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class IngredientAssignerProperties {
    private static final int ITEMS = 4;
    private static final int MAX_SLOTS = 6;

    @Property
    void assign_SmallCases_EmptyExactlyWhenBruteForceFindsNothing(
            @ForAll @Size(max = MAX_SLOTS) List<@IntRange(min = 1, max = 15) Integer> masks,
            @ForAll @Size(ITEMS) List<@IntRange(min = 0, max = 3) Integer> stock) {
        List<IngredientSpec> slots = slots(masks);
        Map<String, Integer> available = available(stock);

        Optional<List<String>> assignment = IngredientAssigner.assign(slots, available);

        assertEquals(bruteForceExists(slots, new HashMap<>(available), 0), assignment.isPresent(),
                "Ассигнатор обязан находить назначение ровно тогда, когда оно существует: иначе выполнимый рецепт "
                        + "объявляется невыполнимым (дух не крафтит при наличии материалов) или наоборот. Места: "
                        + masks + ", наличие: " + stock);
    }

    @Property
    void assign_SmallCases_AssignmentNeverExceedsAvailable(
            @ForAll @Size(max = MAX_SLOTS) List<@IntRange(min = 1, max = 15) Integer> masks,
            @ForAll @Size(ITEMS) List<@IntRange(min = 0, max = 3) Integer> stock) {
        List<IngredientSpec> slots = slots(masks);
        Map<String, Integer> available = available(stock);

        Optional<List<String>> assignment = IngredientAssigner.assign(slots, available);

        if (assignment.isPresent()) {
            List<String> chosen = assignment.get();
            assertEquals(slots.size(), chosen.size(), "На каждое место рецепта обязан быть назначен предмет");
            Map<String, Integer> used = new HashMap<>();
            for (int i = 0; i < chosen.size(); i++) {
                assertTrue(slots.get(i).alternatives().contains(chosen.get(i)),
                        "Предмет '" + chosen.get(i) + "' назначен на место " + i + ", которое его не допускает");
                used.merge(chosen.get(i), 1, Integer::sum);
            }
            used.forEach((item, count) -> assertTrue(count <= available.getOrDefault(item, 0),
                    "Назначение расходует " + item + " " + count + " раз при наличии "
                            + available.getOrDefault(item, 0) + ": дух не может отдать то, чего нет"));
        }
    }

    private static boolean bruteForceExists(List<IngredientSpec> slots, Map<String, Integer> left, int index) {
        if (index == slots.size()) {
            return true;
        }
        for (String item : slots.get(index).alternatives()) {
            int have = left.getOrDefault(item, 0);
            if (have > 0) {
                left.put(item, have - 1);
                boolean found = bruteForceExists(slots, left, index + 1);
                left.put(item, have);
                if (found) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<IngredientSpec> slots(List<Integer> masks) {
        List<IngredientSpec> slots = new ArrayList<>();
        for (int mask : masks) {
            List<String> alternatives = new ArrayList<>();
            for (int item = 0; item < ITEMS; item++) {
                if ((mask >> item & 1) == 1) {
                    alternatives.add("item_" + item);
                }
            }
            slots.add(new IngredientSpec(alternatives));
        }
        return slots;
    }

    private static Map<String, Integer> available(List<Integer> stock) {
        Map<String, Integer> available = new HashMap<>();
        for (int item = 0; item < stock.size(); item++) {
            available.put("item_" + item, stock.get(item));
        }
        return available;
    }
}
