package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtectedTests {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = BaseKit.load();

    @Test
    void giveAway_InventoryWithNullSlot_ZeroForNullOthersByQuota() {
        List<Stack> inventory = Arrays.asList(
                new Stack("bread", 0, 16, 64), null, new Stack("cobblestone", 0, 64, 64));
        Protected protection = Protected.of(List.of(), inventory, KIT, VALUES);

        assertEquals(0, protection.giveAway(0),
                "Хлеб 16 штук при квоте 16 не отдаётся: слот целиком внутри набора");
        assertEquals(0, protection.giveAway(1),
                "Пустой слот отдавать нечего: защита не падает с NPE на null, а считает слот защищённым "
                        + "пустотой");
        assertEquals(32, protection.giveAway(2),
                "Булыжник 64 штуки при keep 32 отдаёт 32: излишек считается и рядом с пустым слотом");
    }
}
