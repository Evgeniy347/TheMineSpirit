package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class More4RulesTests {
    @Test
    void events_AllSignals_AddExpected() {
        Set<String> events = More4Rules.events(new More4Rules.Signals(
                new More4Rules.Players(2, 1, 1),
                new More4Rules.Portal(true, true, false, false),
                1,
                new More4Rules.Lots(true, More4Rules.SPACE_MIN),
                new More4Rules.Pets(1, 2),
                new More4Rules.Rare(1, 1),
                new More4Rules.Settlement(1, 1)));

        assertTrue(
                events.containsAll(Set.of(
                        More4Rules.PLAYER_NEAR,
                        More4Rules.PLAYER_HUNGRY,
                        More4Rules.PLAYER_TOOL_BROKEN,
                        More4Rules.WRONG_TOOL_HAND,
                        More4Rules.PORTAL_FOUND,
                        More4Rules.PORTAL_UNSAFE,
                        More4Rules.SPACE_FOUND,
                        More4Rules.TAMED_PET,
                        More4Rules.RARE_FOUND,
                        More4Rules.RARE_MIXED,
                        More4Rules.CAUTION)),
                "Все входы области graphmore4 дают свои события:  Иначе тест events_AllSignals_AddExpected перестанет ловить эту поломку. При смене events_AllSignals_AddExpected проверь это утверждение."
                        + events);
        assertFalse(
                events.contains(More4Rules.SPACE_TIGHT),
                "При достатке места тревога о тесноте не нужна Иначе тест events_AllSignals_AddExpected перестанет ловить эту поломку. При смене events_AllSignals_AddExpected проверь это утверждение.");
    }

    @Test
    void events_EmptySignals_AddNothing() {
        assertEquals(
                Set.of(),
                More4Rules.events(More4Rules.empty()),
                "Пустой вход не ставит работ Иначе тест events_EmptySignals_AddNothing перестанет ловить эту поломку. При смене events_EmptySignals_AddNothing проверь это утверждение.");
    }

    @Test
    void players_HungryAndBrokenTool_AreCountedSeparately() {
        More4Rules.Signals hungry =
                signals(new More4Rules.Players(3, 1, 0), new More4Rules.Portal(false, false, false, false));
        More4Rules.Signals broken =
                signals(new More4Rules.Players(3, 0, 2), new More4Rules.Portal(false, false, false, false));

        assertTrue(
                More4Rules.events(hungry).contains(More4Rules.PLAYER_HUNGRY),
                "Голодный игрок — принести еду Иначе тест players_HungryAndBrokenTool_AreCountedSeparately перестанет ловить эту поломку. При смене players_HungryAndBrokenTool_AreCountedSeparately проверь это утверждение.");
        assertFalse(
                More4Rules.events(hungry).contains(More4Rules.PLAYER_TOOL_BROKEN),
                "Целый инструмент не чинят Иначе тест players_HungryAndBrokenTool_AreCountedSeparately перестанет ловить эту поломку. При смене players_HungryAndBrokenTool_AreCountedSeparately проверь это утверждение.");
        assertTrue(
                More4Rules.events(broken).contains(More4Rules.PLAYER_TOOL_BROKEN),
                "Сломанный — предложить запасной Иначе тест players_HungryAndBrokenTool_AreCountedSeparately перестанет ловить эту поломку. При смене players_HungryAndBrokenTool_AreCountedSeparately проверь это утверждение.");
        assertFalse(
                More4Rules.events(broken).contains(More4Rules.PLAYER_HUNGRY),
                "Сытый игрок еды не просит Иначе тест players_HungryAndBrokenTool_AreCountedSeparately перестанет ловить эту поломку. При смене players_HungryAndBrokenTool_AreCountedSeparately проверь это утверждение.");
    }

    private static More4Rules.Signals signals(More4Rules.Players players, More4Rules.Portal portal) {
        return new More4Rules.Signals(
                players,
                portal,
                0,
                new More4Rules.Lots(false, 0),
                new More4Rules.Pets(0, 0),
                new More4Rules.Rare(0, 0),
                new More4Rules.Settlement(0, 0));
    }
}
