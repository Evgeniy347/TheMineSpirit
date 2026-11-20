package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class InventoryNormProperties {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = BaseKit.load();
    private static final List<String> NEEDS = List.of("bread", "torch", "iron_ingot", "cobblestone");

    @Provide
    Arbitrary<List<Stack>> neededInventories() {
        return Arbitraries.of(NEEDS)
                .flatMap(item -> Arbitraries.integers().between(1, 64).map(count -> new Stack(item, 0, count, 64)))
                .list()
                .ofMaxSize(6);
    }

    @Property
    void extra_EverythingNeeded_IsEmpty(@ForAll("neededInventories") List<Stack> inventory) {
        List<Slice> extra = InventoryNorm.extra(inventory, KIT, NEEDS, VALUES);

        assertEquals(
                List.of(),
                extra,
                "Предметы, заявленные задачей как нужные, не бывают лишними: иначе дух выбросит материал "
                        + "будущего крафта. Инвентарь: " + inventory
                        + ". При смене extra проверь полный список потребностей задачи");
    }

    @Property
    void fill_AnyInventory_ExtraNotAboveStored(@ForAll("neededInventories") List<Stack> inventory) {
        InventoryFill fill = InventoryNorm.fill(inventory, KIT, List.of("bread"), VALUES);

        assertTrue(
                fill.extraItems() <= fill.storedItems(),
                "Лишнего не больше, чем лежит: доля лишнего иначе превысит единицу и метрика заполнения врёт. "
                        + "Инвентарь: " + inventory + ". При смене fill проверь пустой и полный инвентарь");
        assertTrue(
                fill.occupiedSlots() <= fill.capacity(),
                "Занятых ячеек не больше вместимости: иначе доля занятости превысит единицу и план "
                        + "будет врать о месте. Инвентарь: " + inventory
                        + ". При смене fill проверь границы слотов 0 и 36");
    }

    @Property
    void fill_NonNullStacks_CountAsOccupied(@ForAll("neededInventories") List<Stack> inventory) {
        List<Stack> padded = new ArrayList<>(inventory);
        while (padded.size() < 36) {
            padded.add(null);
        }

        InventoryFill fill = InventoryNorm.fill(padded, KIT, List.of(), VALUES);

        assertEquals(
                inventory.size(),
                fill.occupiedSlots(),
                "Заняты ровно непустые стопки: иначе заполнение инвентаря считается неверно и план сдаёт "
                        + "лишнее. При смене fill проверь инвентарь из null и непустых слотов");
    }
}
