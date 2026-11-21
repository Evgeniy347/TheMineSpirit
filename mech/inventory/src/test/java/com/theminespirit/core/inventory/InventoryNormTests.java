package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryNormTests {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = BaseKit.load();

    private static Stack stack(String item, int count) {
        return new Stack(item, 0, count, 64);
    }

    @Test
    void extra_BreadWithinQuota_IsEmpty() {
        assertEquals(
                List.of(),
                InventoryNorm.extra(List.of(stack("bread", 16)), KIT, List.of(), VALUES),
                "Хлеб в пределах квоты еды (16) — норма, а не лишнее: иначе дух унесёт в контейнер свой паёк "
                        + "и останется голодным. При смене extra проверь квоту еды");
    }

    @Test
    void extra_BreadOverQuota_OnlyExcessIsExtra() {
        assertEquals(
                List.of(new Slice(0, 4)),
                InventoryNorm.extra(List.of(stack("bread", 20)), KIT, List.of(), VALUES),
                "Сверх квоты еды лишним считается только избыток (4 из 20): норма остаётся у духа, иначе он "
                        + "останется без еды. При смене extra проверь 20 хлеба");
    }

    @Test
    void extra_TaskNeed_ProtectsItemFromExtra() {
        List<Stack> inventory = List.of(stack("iron_ingot", 10));

        assertEquals(
                List.of(new Slice(0, 10)),
                InventoryNorm.extra(inventory, KIT, List.of(), VALUES),
                "Железо вне набора и вне нужд задачи — лишнее, иначе оно держит место и план не освобождает слоты. "
                        + "При смене extra проверь предмет вне набора");
        assertEquals(
                List.of(),
                InventoryNorm.extra(inventory, KIT, List.of("iron_ingot"), VALUES),
                "Минимум задачи защищает железо от лишнего: иначе дух унесёт материал будущего крафта и "
                        + "сорвёт задачу. При смене extra проверь нужду задачи");
    }

    @Test
    void extra_TorchWithinQuota_IsNorm() {
        assertEquals(
                List.of(),
                InventoryNorm.extra(List.of(stack("torch", 24)), KIT, List.of(), VALUES),
                "Факелы в пределах квоты (24) — норма: иначе шахтёр унесёт освещение в сундук и останется "
                        + "в темноте. При смене extra проверь квоту факелов");
    }

    @Test
    void fill_OneOfThreeSlots_ReportsFractionsAndFreeSlots() {
        List<Stack> inventory = Arrays.asList(stack("bread", 20), null, null);

        InventoryFill fill = InventoryNorm.fill(inventory, KIT, List.of(), VALUES);

        assertEquals(
                1,
                fill.occupiedSlots(),
                "Занята одна ячейка из трёх: неверный счёт сбивает долю занятости и решение о разгрузке, "
                        + "иначе план считает инвентарь пустым. При смене fill проверь смесь null и стопки");
        assertEquals(
                3,
                fill.capacity(),
                "Вместимость — число ячеек инвентаря: иначе доля занятости делится не на то и выходит "
                        + "за единицу. При смене fill проверь размер 3");
        assertEquals(
                4,
                fill.extraItems(),
                "Лишнее — избыток хлеба сверх квоты (4): иначе дух сдаёт паёк и останется голодным "
                        + "на задании. При смене fill проверь квоту еды");
        assertEquals(
                20,
                fill.storedItems(),
                "Лежит 20 предметов — знаменатель доли лишнего: иное число врёт о запасе, иначе доля лишнего "
                        + "съезжает и разгрузка не запускается. При смене fill проверь сумму");
        assertEquals(
                2,
                fill.freeSlots(),
                "Свободных ячеек две: по ним решают, держать ли задачу, иначе дух займёт чужой слот. "
                        + "При смене fill проверь свободные слоты");
        assertEquals(
                1.0 / 3.0,
                fill.occupiedFraction(),
                1e-9,
                "Доля занятых ячеек — 1/3, иначе метрика заполнения врёт и план рано запускает разгрузку "
                        + "посреди работы. При смене fill проверь деление");
        assertEquals(
                0.2,
                fill.extraFraction(),
                1e-9,
                "Доля лишнего — 4/20, иначе решение о разгрузке принимается неверно и дух тащит паёк "
                        + "в сундук вместо работы. При смене fill проверь деление");
    }

    @Test
    void fill_EmptyInventory_IsZero() {
        InventoryFill fill = InventoryNorm.fill(Arrays.asList(null, null, null), KIT, List.of(), VALUES);

        assertEquals(
                0,
                fill.occupiedSlots(),
                "Пустой инвентарь не занимает ячеек, иначе доля занятости стартует не с нуля и план "
                        + "видит место там, где его нет. При смене fill проверь список из null");
        assertEquals(
                0.0,
                fill.occupiedFraction(),
                1e-9,
                "Доля занятых ячеек у пустого — 0, иначе план считает инвентарь полным и гоняет "
                        + "разгрузку без причины. При смене fill проверь пустой инвентарь");
        assertEquals(
                0.0,
                fill.extraFraction(),
                1e-9,
                "Доля лишнего у пустого — 0, иначе разгрузка запускается без причины и дух идёт "
                        + "к сундуку впустую. При смене fill проверь пустой инвентарь");
        assertEquals(
                3,
                fill.freeSlots(),
                "Все ячейки свободны, иначе план ошибочно считает место занятым и отказывает в задаче. "
                        + "При смене fill проверь пустой инвентарь");
    }

    @Test
    void fill_ExtraOverKit_IsPartOfStored() {
        List<Stack> inventory = List.of(stack("bread", 20), stack("iron_ingot", 10));

        InventoryFill fill = InventoryNorm.fill(inventory, KIT, List.of("iron_ingot"), VALUES);

        assertTrue(
                fill.extraItems() <= fill.storedItems(),
                "Лишнее — часть лежащего: иначе доля лишнего превысит единицу и разгрузка врёт о "
                        + "занятости. При смене fill проверь смесь лишнего и защищённого");
        assertEquals(
                4,
                fill.extraItems(),
                "Железо защищено нуждой задачи, лишний только избыток хлеба, иначе дух уносит материал крафта. "
                        + "При смене extra проверь нужду iron_ingot");
    }
}
