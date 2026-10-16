package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvergreenRulesTests {

    private static final UUID SPIRIT_A = UUID.randomUUID();
    private static final UUID SPIRIT_B = UUID.randomUUID();
    private static final EatNeed HUNGRY = new EatNeed(10, 20, 20, 20);
    private static final EatNeed FULL = new EatNeed(20, 20, 20, 20);
    private static final SpiritView FREE = SpiritView.free();

    @Test
    void permanent_FromLiterals_MatchesSet() {
        assertEquals(Set.of(BoardCase.EAT_TO_FULL, BoardCase.RESTOCK_FOOD, BoardCase.REPAIR_FENCE,
                        BoardCase.REPAIR_BUILDING, BoardCase.FILL_CRATER, BoardCase.ENCHANT_SHELVES),
                EvergreenRules.permanentCases(),
                "Постоянный набор обязан совпадать с литералами: еда (EAT_TO_FULL, RESTOCK_FOOD), "
                        + "четыре постоянные проверки (REPAIR_FENCE, REPAIR_BUILDING, FILL_CRATER, "
                        + "ENCHANT_SHELVES). Лишний кейс в наборе висел бы на доске без источника, "
                        + "забытый — молча исчез бы");
    }

    @Test
    void permanent_EveryCase_StaysOnBoard() {
        for (BoardCase boardCase : EvergreenRules.permanentCases()) {
            assertTrue(EvergreenRules.permanent(boardCase),
                    "Кейс " + boardCase + " постоянный: его причина always, снятие по актуальности "
                            + "его не трогает");
        }
    }

    @Test
    void supplies_FromClasspath_MatchLiterals() {
        assertEquals(Map.of(
                        "minecraft:bookshelf", 32,
                        "minecraft:book", 32,
                        "minecraft:paper", 128),
                EvergreenRules.supplyThresholds(),
                "Пороги постоянных запасов обязаны совпадать с литералами: полки — полстака (32), "
                        + "книги — полстака (32), бумага — два стака (128). Поменянное число в файле "
                        + "без правки теста молча изменило бы склад");
        assertEquals(32, EvergreenRules.supplyThreshold("minecraft:bookshelf"),
                "Предел в тесте, факт из production: полок держат полстака");
        assertEquals(128, EvergreenRules.supplyThreshold("minecraft:paper"),
                "Предел в тесте, факт из production: бумаги держат два стака");
    }

    @Test
    void supplyShortfall_BelowThreshold_Computes() {
        assertEquals(16, EvergreenRules.supplyShortfall("minecraft:bookshelf", 16),
                "Полок 16 при пороге 32 — не хватает 16: задание скрафтит до порога");
        assertEquals(0, EvergreenRules.supplyShortfall("minecraft:bookshelf", 32),
                "Полок ровно порог — нехватки нет: задание не ставится");
        assertEquals(0, EvergreenRules.supplyShortfall("minecraft:bookshelf", 40),
                "Полок больше порога — нехватки нет: перебор не карается");
    }

    @Test
    void supplyShortfall_UnknownItem_Zero() {
        assertEquals(0, EvergreenRules.supplyShortfall("minecraft:unknown", 0),
                "Предмет без порога не требует запаса: постоянное задание на него не ставится, "
                        + "иначе доска гоняла бы духа за несуществующим порогом");
    }

    @Test
    void issuedTo_AssignedApplicableSpirit_Issued() {
        BoardTicket assigned = ticket("eat_a", BoardCase.EAT_TO_FULL, Optional.of(SPIRIT_A));

        assertTrue(EvergreenRules.issuedTo(assigned, SPIRIT_A, HUNGRY, FREE),
                "Адресное задание голодному духу-владельцу выдаётся: он применим и это его задание");
    }

    @Test
    void issuedTo_AssignedOtherSpirit_NotIssued() {
        BoardTicket assigned = ticket("eat_a", BoardCase.EAT_TO_FULL, Optional.of(SPIRIT_A));

        assertFalse(EvergreenRules.issuedTo(assigned, SPIRIT_B, HUNGRY, FREE),
                "Адресное задание чужому духу не выдаётся: владелец назван, подменять его нельзя");
    }

    @Test
    void issuedTo_UnassignedSpirit_Available() {
        BoardTicket open = ticket("eat_any", BoardCase.EAT_TO_FULL, Optional.empty());

        assertTrue(EvergreenRules.issuedTo(open, SPIRIT_B, HUNGRY, FREE),
                "Задание без владельца доступно любому применимому духу: другой свободный дух "
                        + "берёт его, когда назначенный не применим");
    }

    @Test
    void issuedTo_AssignedFullSpirit_NotIssued() {
        BoardTicket assigned = ticket("eat_a", BoardCase.EAT_TO_FULL, Optional.of(SPIRIT_A));

        assertFalse(EvergreenRules.issuedTo(assigned, SPIRIT_A, FULL, FREE),
                "Сытого владельца задание не получает: задание остаётся на доске, но этот дух "
                        + "его не берёт — ждёт применимого или снятия адреса");
    }

    @Test
    void issuedTo_NullArgs_AreRejected() {
        BoardTicket assigned = ticket("eat_a", BoardCase.EAT_TO_FULL, Optional.of(SPIRIT_A));

        assertThrows(NullPointerException.class,
                () -> EvergreenRules.issuedTo(null, SPIRIT_A, HUNGRY, FREE),
                "Задание null обязано отвергаться: выдавать нечего");
        assertThrows(NullPointerException.class,
                () -> EvergreenRules.issuedTo(assigned, null, HUNGRY, FREE),
                "Дух null обязан отвергаться: выдача идёт конкретному духу");
        assertThrows(NullPointerException.class, () -> EvergreenRules.permanent(null),
                "Кейс null обязан отвергаться: проверка постоянного набора идёт по имени кейса");
        assertThrows(NullPointerException.class, () -> EvergreenRules.supplyThreshold(null),
                "Предмет null обязан отвергаться: порог ищут по имени предмета");
        assertThrows(IllegalArgumentException.class,
                () -> EvergreenRules.supplyShortfall("minecraft:bookshelf", -1),
                "Отрицательное наличие обязано падать: склад не бывает в минусе");
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, Optional<UUID> assignee) {
        return new BoardTicket(id, boardCase, assignee, BoardRules.priorityOf(boardCase), 0, 1,
                BoardTicket.ALWAYS_CAUSE, List.of(), Map.of());
    }
}
