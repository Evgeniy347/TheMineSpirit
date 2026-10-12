package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ChestSorterTests {
    private static final ValueTable VALUES = ValueTable.load();

    @Test
    void sort_PartialCobblestone_MergesIntoOneStack() {
        List<Stack> slots = List.of(
                new Stack("cobblestone", 0, 3, 64),
                new Stack("cobblestone", 0, 5, 64),
                new Stack("cobblestone", 0, 7, 64));

        assertEquals(List.of(new Stack("cobblestone", 0, 15, 64)), ChestSorter.sort(slots, VALUES),
                "Частичные стопки одного предмета обязаны слиться: сортёр сундука убирает мусор из слотов, "
                        + "иначе сундук забивается обрывками, а withdraw берёт их из разных мест");
    }

    @Test
    void sort_StacksOverMax_MovesRemainderToOwnSlot() {
        List<Stack> slots = List.of(
                new Stack("cobblestone", 0, 64, 64),
                new Stack("cobblestone", 0, 64, 64),
                new Stack("cobblestone", 0, 3, 64));

        assertEquals(List.of(new Stack("cobblestone", 0, 64, 64), new Stack("cobblestone", 0, 64, 64),
                new Stack("cobblestone", 0, 3, 64)), ChestSorter.sort(slots, VALUES),
                "Стопка не может быть больше максимума: остаток 3 переходит в отдельный слот, "
                        + "а не теряется и не размазывается по сундуку");
    }

    @Test
    void sort_DifferentVariants_AreNotMerged() {
        List<Stack> slots = List.of(
                new Stack("iron_pickaxe", 1, 1, 1),
                new Stack("iron_pickaxe", 0, 1, 1));

        assertEquals(List.of(new Stack("iron_pickaxe", 0, 1, 1), new Stack("iron_pickaxe", 1, 1, 1)),
                ChestSorter.sort(slots, VALUES),
                "Кирка с другими компонентами (зачарование, повреждение, имя) — другой вариант: "
                        + "слияние зачарованной и обычной кирки потеряло бы зачарование, поэтому сортёр "
                        + "рядом ставит два разных предмета, а не один");
    }

    @Test
    void sort_Categories_FollowValueTableOrder() {
        List<Stack> slots = List.of(
                new Stack("bucket", 0, 1, 1),
                new Stack("cobblestone", 0, 1, 64),
                new Stack("bread", 0, 1, 64),
                new Stack("iron_ingot", 0, 1, 64),
                new Stack("diamond_chestplate", 0, 1, 1),
                new Stack("iron_pickaxe", 0, 1, 1));

        assertEquals(List.of(
                new Stack("iron_pickaxe", 0, 1, 1),
                new Stack("diamond_chestplate", 0, 1, 1),
                new Stack("bread", 0, 1, 64),
                new Stack("iron_ingot", 0, 1, 64),
                new Stack("cobblestone", 0, 1, 64),
                new Stack("bucket", 0, 1, 1)), ChestSorter.sort(slots, VALUES),
                "Порядок групп задаёт файл ценности: инструменты, броня, еда, материалы, блоки, прочее. "
                        + "Разъехавшийся порядок заставил бы духа каждый раз перекладывать сундук заново");
    }

    @Test
    void sort_SameGroupItems_AreOrderedByIdThenVariant() {
        List<Stack> slots = List.of(
                new Stack("oak_planks", 2, 4, 64),
                new Stack("oak_planks", 0, 7, 64),
                new Stack("birch_planks", 0, 3, 64));

        assertEquals(List.of(
                new Stack("birch_planks", 0, 3, 64),
                new Stack("oak_planks", 0, 7, 64),
                new Stack("oak_planks", 2, 4, 64)), ChestSorter.sort(slots, VALUES),
                "Внутри группы стопки идут по id, затем по варианту: одинаковый порядок делает "
                        + "раскладку предсказуемой, один и тот же сундук всегда раскладывается так же");
    }

    @Test
    void sort_EmptySlots_StayOutOfLayout() {
        List<Stack> slots = new ArrayList<>();
        slots.add(new Stack("cobblestone", 0, 1, 64));
        slots.add(null);
        slots.add(null);
        slots.add(new Stack("dirt", 0, 1, 64));

        assertEquals(2, ChestSorter.sort(slots, VALUES).size(),
                "Пустые слоты не попадают в раскладку: занятые идут первыми, свободные остаются в конце контейнера");
    }

    @Test
    void sort_EmptyContainer_GivesEmptyLayout() {
        assertEquals(List.of(), ChestSorter.sort(List.of(), VALUES),
                "Пустой контейнер сортируется в пустую раскладку: обращение к сундуку не должно "
                        + "оставлять в нём лишних слотов");
    }

    @Test
    void sort_SortedLayout_StaysSameOnSecondPass() {
        List<Stack> slots = List.of(
                new Stack("cobblestone", 0, 30, 64),
                new Stack("cobblestone", 0, 40, 64),
                new Stack("cobblestone", 0, 1, 64),
                new Stack("bread", 0, 7, 64),
                new Stack("iron_ingot", 0, 12, 64),
                new Stack("iron_ingot", 0, 5, 64),
                new Stack("iron_pickaxe", 3, 1, 1),
                new Stack("oak_log", 0, 11, 64),
                new Stack("torch", 0, 4, 64));

        List<Stack> once = ChestSorter.sort(slots, VALUES);
        List<Stack> twice = ChestSorter.sort(once, VALUES);

        assertEquals(once, twice,
                "Повторная сортировка не меняет ничего: sort_chest обязан быть идемпотентным, иначе "
                        + "любое обращение к сундуку перекладывало бы его заново");
    }

    @Test
    void sort_ShuffledInput_GivesSameLayout() {
        List<Stack> slots = List.of(
                new Stack("cobblestone", 0, 13, 64),
                new Stack("bread", 0, 7, 64),
                new Stack("iron_ingot", 0, 12, 64),
                new Stack("iron_pickaxe", 3, 1, 1),
                new Stack("oak_log", 0, 11, 64),
                new Stack("apple", 0, 2, 64));

        List<Stack> expected = ChestSorter.sort(slots, VALUES);
        List<Stack> shuffled = new ArrayList<>(slots);
        Collections.reverse(shuffled);

        assertEquals(expected, ChestSorter.sort(shuffled, VALUES),
                "Раскладка не зависит от того, в каком порядке предметы лежали: дух должен получить "
                        + "одну и ту же картинку сундука, иначе сортировка никогда не завершится");
    }

    @Test
    void sort_NinetySlots_AsksCategoryOncePerSlot() {
        List<Stack> slots = new ArrayList<>();
        String[] items = {"cobblestone", "oak_log", "iron_ingot", "bread", "iron_pickaxe", "diamond_chestplate",
                "bucket", "torch", "crafting_table", "stick"};
        for (int index = 0; index < 90; index++) {
            slots.add(new Stack(items[index % items.length], 0, 7, 64));
        }
        long before = VALUES.categoryLookups();

        ChestSorter.sort(slots, VALUES);

        long asked = VALUES.categoryLookups() - before;
        assertTrue(asked <= slots.size(),
                "Сортировка 90 слотов спрашивает категорию не больше 90 раз, а спросила " + asked + ": "
                        + "вызов categoryOf внутри компаратора даёт O(n log n) обращений к шаблонам "
                        + "на серверном тике при каждом обращении к сундуку. Подлог для проверки: "
                        + "верните ranks.get(values.categoryOf(...)) в компаратор — тест покраснеет");
    }

    @Test
    void sort_NinetySlots_UsesMinimalSlotCount() {
        List<Stack> slots = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            slots.add(new Stack("cobblestone", 0, 7, 64));
        }
        List<Stack> layout = ChestSorter.sort(slots, VALUES);

        assertEquals(4, layout.size(),
                "210 булыжника занимают 4 слота (64+64+64+18), а не 30: минимум занятых слотов — "
                        + "признак того, что сортёр слил стопки, а не переставил обрывки");
        assertEquals(210, layout.stream().mapToInt(Stack::count).sum(),
                "После сортировки ни один предмет не потерян: сумма количеств раскладки равна сумме исходной "
                        + "(210 булыжника)");
    }

    @Test
    void sort_NullSlots_IgnoredInSum() {
        List<Stack> slots = new ArrayList<>();
        slots.add(new Stack("cobblestone", 0, 5, 64));
        slots.add(null);

        List<Stack> layout = ChestSorter.sort(slots, VALUES);

        assertEquals(5, layout.stream().mapToInt(Stack::count).sum(),
                "Пустой слот не добавляет предметов: сумма раскладки считается только по занятым слотам");
        assertEquals(1, layout.size(),
                "Одна стопка даёт один слот: пустой слот не превращается в пустую стопку раскладки");
    }
}
