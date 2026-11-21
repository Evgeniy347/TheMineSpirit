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
            Map.entry("minecraft:cooked_beef", 8.0),
            Map.entry("minecraft:cooked_chicken", 6.0),
            Map.entry("minecraft:baked_potato", 5.0)));

    @Test
    void isRaw_BeefAndPotato_True() {
        assertTrue(
                CookableFoods.isRaw("minecraft:beef"),
                "Сырая говядина — сырьё готовки: иначе еда на костре не планируется"
                        + " При смене isRaw_BeefAndPotato_True проверь это утверждение.");
        assertTrue(
                CookableFoods.isRaw("minecraft:potato"),
                "Картофель — сырьё готовки: печёный картофель получают из него"
                        + " Иначе тест isRaw_BeefAndPotato_True перестанет ловить эту поломку. При смене isRaw_BeefAndPotato_True проверь это утверждение.");
        assertFalse(
                CookableFoods.isRaw("minecraft:cooked_beef"),
                "Готовое не считается сырьём: иначе дух переплавляет уже готовое и теряет сытость"
                        + " При смене isRaw_BeefAndPotato_True проверь это утверждение.");
    }

    @Test
    void cooked_RawBeef_ReturnsCookedBeef() {
        assertEquals(
                Optional.of("minecraft:cooked_beef"),
                CookableFoods.cooked("minecraft:beef"),
                "Сырая говядина жарится в стейк: продукт берётся из пары сырьё → готовое"
                        + " Иначе тест cooked_RawBeef_ReturnsCookedBeef перестанет ловить эту поломку. При смене cooked_RawBeef_ReturnsCookedBeef проверь это утверждение.");
    }

    @Test
    void best_RawFoodsAvailable_ChoosesFattest() {
        Optional<String> cooked = CookableFoods.best(Map.of("minecraft:beef", 1, "minecraft:chicken", 5), TABLE);

        assertEquals(
                Optional.of("minecraft:cooked_beef"),
                cooked,
                "Из доступного сырья берут самое сытное готовое: приоритет еде, которая лучше кормит поселение"
                        + " Иначе тест best_RawFoodsAvailable_ChoosesFattest перестанет ловить эту поломку. При смене best_RawFoodsAvailable_ChoosesFattest проверь это утверждение.");
    }

    @Test
    void isCookableFish_CodAndSalmon_True() {
        assertTrue(
                CookableFoods.isCookableFish("minecraft:cod"),
                "Треска жарится: иначе улов не превращается в еду"
                        + " При смене isCookableFish_CodAndSalmon_True проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
        assertTrue(
                CookableFoods.isCookableFish("minecraft:salmon"),
                "Лосось жарится: иначе улов не превращается в еду"
                        + " При смене isCookableFish_CodAndSalmon_True проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
        assertFalse(
                CookableFoods.isCookableFish("minecraft:beef"),
                "Говядина — не рыба: правило про улов сюда не тянется"
                        + " Иначе тест isCookableFish_CodAndSalmon_True перестанет ловить эту поломку. При смене isCookableFish_CodAndSalmon_True проверь это утверждение.");
    }

    @Test
    void isCookableFish_PufferAndTropical_False() {
        assertTrue(
                CookableFoods.isFish("minecraft:pufferfish"),
                "Иглобрюх — рыба: его кладут на склад, а не выбрасывают"
                        + " Иначе тест isCookableFish_PufferAndTropical_False перестанет ловить эту поломку. При смене isCookableFish_PufferAndTropical_False проверь это утверждение.");
        assertFalse(
                CookableFoods.isCookableFish("minecraft:pufferfish"),
                "Иглобрюх не жарится: жареный иглобрюх — яд в игре, и портить его плавкой нельзя"
                        + " Иначе тест isCookableFish_PufferAndTropical_False перестанет ловить эту поломку. При смене isCookableFish_PufferAndTropical_False проверь это утверждение.");
        assertFalse(
                CookableFoods.isCookableFish("minecraft:tropical_fish"),
                "Тропическая рыба не жарится: её держат для коллекции, а не для еды"
                        + " Иначе тест isCookableFish_PufferAndTropical_False перестанет ловить эту поломку. При смене isCookableFish_PufferAndTropical_False проверь это утверждение.");
    }

    @Test
    void best_FishAmongFoods_ChoosesFishWhenFattest() {
        NutritionTable fishTable =
                new NutritionTable(Map.of("minecraft:cooked_cod", 5.0, "minecraft:cooked_beef", 8.0));

        assertEquals(
                Optional.of("minecraft:cooked_cod"),
                CookableFoods.best(Map.of("minecraft:cod", 3), fishTable),
                "Треска — сырьё готовки: улов обязан попадать в набор жарки"
                        + " Иначе тест best_FishAmongFoods_ChoosesFishWhenFattest перестанет ловить эту поломку. При смене best_FishAmongFoods_ChoosesFishWhenFattest проверь это утверждение.");
    }

    @Test
    void best_OnlyPufferfish_Empty() {
        assertEquals(
                Optional.empty(),
                CookableFoods.best(Map.of("minecraft:pufferfish", 3), TABLE),
                "Иглобрюх не сырьё готовки: без пары сырьё → готовое намерение жарить не строится"
                        + " Иначе тест best_OnlyPufferfish_Empty перестанет ловить эту поломку. При смене best_OnlyPufferfish_Empty проверь это утверждение.");
    }

    @Test
    void best_NoKnownRaw_Empty() {
        assertEquals(
                Optional.empty(),
                CookableFoods.best(Map.of("minecraft:stick", 3), TABLE),
                "Палки — не сырьё готовки: без пары сырьё → готовое намерение не строится"
                        + " Иначе тест best_NoKnownRaw_Empty перестанет ловить эту поломку. При смене best_NoKnownRaw_Empty проверь это утверждение.");
    }
}
