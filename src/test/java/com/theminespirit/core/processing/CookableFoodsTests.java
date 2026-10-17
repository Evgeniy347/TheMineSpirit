package com.theminespirit.core.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.NutritionTable;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CookableFoodsTests {
    private static final NutritionTable TABLE = new NutritionTable(Map.ofEntries(
            Map.entry("minecraft:cooked_beef", 8.0), Map.entry("minecraft:cooked_chicken", 6.0),
            Map.entry("minecraft:baked_potato", 5.0)));

    @Test
    void isRaw_BeefAndPotato_True() {
        assertTrue(CookableFoods.isRaw("minecraft:beef"),
                "Сырая говядина — сырьё готовки: иначе еда на костре не планируется");
        assertTrue(CookableFoods.isRaw("minecraft:potato"),
                "Картофель — сырьё готовки: печёный картофель получают из него");
        assertFalse(CookableFoods.isRaw("minecraft:cooked_beef"),
                "Готовое не считается сырьём: иначе дух переплавляет уже готовое и теряет сытость");
    }

    @Test
    void cooked_RawBeef_ReturnsCookedBeef() {
        assertEquals(Optional.of("minecraft:cooked_beef"), CookableFoods.cooked("minecraft:beef"),
                "Сырая говядина жарится в стейк: продукт берётся из пары сырьё → готовое");
    }

    @Test
    void best_RawFoodsAvailable_ChoosesFattest() {
        Optional<String> cooked = CookableFoods.best(Map.of("minecraft:beef", 1, "minecraft:chicken", 5), TABLE);

        assertEquals(Optional.of("minecraft:cooked_beef"), cooked,
                "Из доступного сырья берут самое сытное готовое: приоритет еде, которая лучше кормит поселение");
    }

    @Test
    void best_NoKnownRaw_Empty() {
        assertEquals(Optional.empty(), CookableFoods.best(Map.of("minecraft:stick", 3), TABLE),
                "Палки — не сырьё готовки: без пары сырьё → готовое намерение не строится");
    }
}
