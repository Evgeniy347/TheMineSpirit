package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ObtainabilityTests {
    @Test
    void canObtain_CycleWithoutBase_IsFalse() {
        Map<String, List<List<String>>> craft = new HashMap<>();
        craft.put("a", List.of(List.of("b")));
        craft.put("b", List.of(List.of("a")));

        Obtainability obtainability = obtainability(craft, Set.of());

        assertFalse(
                obtainability.canObtain("a"),
                "Кольцо a→b→a без добываемого основания не даёт путь: иначе цикл выдавался бы за добычу");
    }

    @Test
    void canObtain_GatherableAlternative_IsTrue() {
        Map<String, List<List<String>>> craft = new HashMap<>();
        craft.put("a", List.of(List.of("b", "base")));

        Obtainability obtainability = obtainability(craft, Set.of("base"));

        assertTrue(
                obtainability.canObtain("a"),
                "У рецепта есть добываемый вариант — предмет достижим: иначе шахта не получила бы задания");
    }

    @Test
    void canObtain_SmeltingPath_IsTrue() {
        Map<String, List<String>> smelt = Map.of("ingot", List.of("ore"));

        Obtainability obtainability = new Obtainability(
                item -> Optional.empty(),
                item -> smelt.getOrDefault(item, List.of()).stream()
                        .map(input -> RecipeSpec.smelting("smelt_" + item, item, 1, IngredientSpec.of(input), 200))
                        .toList(),
                "ore"::equals);

        assertTrue(
                obtainability.canObtain("ingot"),
                "Плавка из добываемой руды — путь: иначе железо считалось бы недостижимым");
    }

    @Test
    void canObtain_WideGraph_AsksEachItemOnce() {
        int last = 40;
        Map<String, List<List<String>>> craft = new HashMap<>();
        Set<String> gatherable = new HashSet<>();
        for (int index = 0; index < last; index++) {
            String next = String.valueOf(index + 1);
            String after = String.valueOf(index + 2);
            craft.put(String.valueOf(index), List.of(List.of(next), List.of(after)));
        }
        for (int index = last; index <= last + 1; index++) {
            gatherable.add(String.valueOf(index));
        }
        AtomicInteger asks = new AtomicInteger();
        Obtainability obtainability = new Obtainability(
                item -> {
                    asks.incrementAndGet();
                    return recipe(item, craft);
                },
                item -> List.of(),
                gatherable::contains);

        assertTrue(obtainability.canObtain("0"), "Широкая цепочка сходится к добываемому основанию: путь есть");
        assertTrue(
                asks.get() <= last + 5,
                "Проверка спрашивает каждый предмет один раз (спрошено " + asks.get() + " при " + last
                        + "): повторный обход графа снова сделал бы проверку экспоненциальной");
    }

    private static Obtainability obtainability(Map<String, List<List<String>>> craft, Set<String> gatherable) {
        return new Obtainability(item -> recipe(item, craft), item -> List.of(), gatherable::contains);
    }

    private static Optional<RecipeSpec> recipe(String item, Map<String, List<List<String>>> craft) {
        List<List<String>> slots = craft.get(item);
        if (slots == null) {
            return Optional.empty();
        }
        List<IngredientSpec> ingredients = new ArrayList<>();
        for (List<String> alternatives : slots) {
            ingredients.add(new IngredientSpec(alternatives));
        }
        return Optional.of(RecipeSpec.shapeless("craft_" + item, item, 1, ingredients));
    }
}
