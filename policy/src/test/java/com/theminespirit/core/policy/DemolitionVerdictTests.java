package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DemolitionVerdictTests {
    @Test
    void permissible_ProtectedBlock_Tolerated() {
        assertTrue(
                DemolitionVerdict.permissible(
                        new BlockProtection.Refused(PolicyId.PROTECTED_BLOCKS, "план поселения"), false),
                "Клетка, которую защита не даёт ломать, — допустимый остаток: иначе тикет сноса падает вечно");
    }

    @Test
    void permissible_ProtectedZone_Tolerated() {
        assertTrue(
                DemolitionVerdict.permissible(
                        new BlockProtection.Refused(PolicyId.PROTECTED_ZONE, "защищённая зона"), false),
                "Защищённая зона — та же защита, а не повод повторять снос");
    }

    @Test
    void permissible_UnsafeOrDrop_NotTolerated() {
        assertFalse(
                unsafe(),
                "Отказ «опасно/недоступно» — временное состояние: тикет должен остаться провалом, а не закрыться");
        assertFalse(
                DemolitionVerdict.permissible(new BlockProtection.Allowed(), false),
                "Разрешённая к ломке клетка, которая стоит, — настоящий провал разбора");
    }

    @Test
    void permissible_KnownLeftover_AlwaysTolerated() {
        assertTrue(
                DemolitionVerdict.permissible(new BlockProtection.Allowed(), true),
                "Забор плана, план-клетка и постройка игрока признаны остатком заранее — их не учитывают в провале");
    }

    private static boolean unsafe() {
        return DemolitionVerdict.permissible(new BlockProtection.Refused(PolicyId.DROP, "под ногами"), false);
    }
}
