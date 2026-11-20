package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.policy.api.PolicyId;
import org.junit.jupiter.api.Test;

class DemolitionVerdictTests {
    @Test
    void permissible_ProtectedBlock_Tolerated() {
        assertTrue(
                DemolitionVerdict.permissible(
                        new BlockProtection.Refused(PolicyId.PROTECTED_BLOCKS, "план поселения"), false),
                "Клетка, которую защита не даёт ломать, — допустимый остаток: иначе тикет сноса падает вечно. "
                        + "При смене permissible проверь, что PROTECTED_BLOCKS не считается провалом");
    }

    @Test
    void permissible_ProtectedZone_Tolerated() {
        assertTrue(
                DemolitionVerdict.permissible(
                        new BlockProtection.Refused(PolicyId.PROTECTED_ZONE, "защищённая зона"), false),
                "Защищённая зона — та же защита, а не повод повторять снос, иначе тикет уходит в бесконечный "
                        + "перезапуск. При смене permissible проверь PROTECTED_ZONE отдельной строкой");
    }

    @Test
    void permissible_UnsafeOrDrop_NotTolerated() {
        assertFalse(
                unsafe(),
                "Отказ «опасно/недоступно» — временное состояние: тикет должен остаться провалом, а не закрыться. "
                        + "При смене permissible проверь DROP: он не должен маскироваться под остаток");
        assertFalse(
                DemolitionVerdict.permissible(new BlockProtection.Allowed(), false),
                "Разрешённая к ломке клетка, которая стоит, — настоящий провал разбора, иначе снос молча "
                        + "простаивает. При смене проверки проверь Allowed без метки остатка");
    }

    @Test
    void permissible_KnownLeftover_AlwaysTolerated() {
        assertTrue(
                DemolitionVerdict.permissible(new BlockProtection.Allowed(), true),
                "Забор плана, план-клетка и постройка игрока признаны остатком заранее — их не учитывают в провале. "
                        + "При смене permissible проверь Allowed с меткой остатка раньше всех остальных веток");
    }

    @Test
    void permissibleEither_OneProtected_Tolerated() {
        assertTrue(
                DemolitionVerdict.permissibleEither(
                        new BlockProtection.Allowed(), new BlockProtection.Refused(PolicyId.PROTECTED_BLOCKS, "план")),
                "Достаточно отказа одной из проверок (снос или прокоп): путь не может взять клетку ни тем, ни другим. "
                        + "При смене permissibleEither проверь пары Allowed+Refused и Refused+Allowed");
        assertFalse(
                DemolitionVerdict.permissibleEither(
                        new BlockProtection.Allowed(), new BlockProtection.Refused(PolicyId.DROP, "опасно")),
                "Опасность/инструмент в любой проверке — не повод закрыть снос без факта мира, иначе провал "
                        + "останется незамеченным. При смене permissibleEither проверь DROP во второй проверке");
    }

    private static boolean unsafe() {
        return DemolitionVerdict.permissible(new BlockProtection.Refused(PolicyId.DROP, "под ногами"), false);
    }
}
