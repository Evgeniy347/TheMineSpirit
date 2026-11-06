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
        List<Stack> inventory = Arrays.asList(new Stack("bread", 0, 16, 64), null, new Stack("torch", 0, 24, 64));
        Protected protection = Protected.of(List.of(), inventory, KIT, VALUES);

        assertEquals(
                0,
                protection.giveAway(0),
                "Хлеб 16 штук при квоте 16 не отдаётся: слот целиком внутри постоянной части");
        assertEquals(
                0,
                protection.giveAway(1),
                "Пустой слот отдавать нечего: защита не падает с NPE на null, а считает слот защищённым " + "пустотой");
        assertEquals(
                0,
                protection.giveAway(2),
                "24 факела при квоте 24 не отдаются: постоянная квота защищает весь запас факелов");
    }

    @Test
    void giveAway_ConstantItemOutsideQuota_GivesAwayExcess() {
        List<Stack> inventory = List.of(new Stack("torch", 0, 40, 64));
        Protected protection = Protected.of(List.of(), inventory, KIT, VALUES);

        assertEquals(
                16,
                protection.giveAway(0),
                "40 факелов при квоте 24 отдают 16: излишек постоянной части сверх квоты уходит " + "при переполнении");
    }

    @Test
    void giveAway_ItemOutsideKit_GivesAwayWhole() {
        List<Stack> inventory = List.of(new Stack("oak_planks", 0, 64, 64));
        Protected protection = Protected.of(List.of(), inventory, KIT, VALUES);

        assertEquals(
                64,
                protection.giveAway(0),
                "Доски вне набора отдаются целиком: политика переполнения обязана их выбросить, "
                        + "чтобы освободить место добыче");
    }
}
