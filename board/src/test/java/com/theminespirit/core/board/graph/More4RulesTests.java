package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class More4RulesTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

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
                "Все входы области graphmore4 дают свои события: " + events);
        assertFalse(events.contains(More4Rules.SPACE_TIGHT), "При достатке места тревога о тесноте не нужна");
    }

    @Test
    void events_EmptySignals_AddNothing() {
        assertEquals(Set.of(), More4Rules.events(More4Rules.empty()), "Пустой вход не ставит работ");
    }

    @Test
    void players_HungryAndBrokenTool_AreCountedSeparately() {
        More4Rules.Signals hungry =
                signals(new More4Rules.Players(3, 1, 0), new More4Rules.Portal(false, false, false, false));
        More4Rules.Signals broken =
                signals(new More4Rules.Players(3, 0, 2), new More4Rules.Portal(false, false, false, false));

        assertTrue(More4Rules.events(hungry).contains(More4Rules.PLAYER_HUNGRY), "Голодный игрок — принести еду");
        assertFalse(More4Rules.events(hungry).contains(More4Rules.PLAYER_TOOL_BROKEN), "Целый инструмент не чинят");
        assertTrue(
                More4Rules.events(broken).contains(More4Rules.PLAYER_TOOL_BROKEN), "Сломанный — предложить запасной");
        assertFalse(More4Rules.events(broken).contains(More4Rules.PLAYER_HUNGRY), "Сытый игрок еды не просит");
    }

    @Test
    void players_ImpossibleCounts_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new More4Rules.Players(-1, 0, 0),
                "Отрицательное число игроков рядом невозможно");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More4Rules.Players(1, 2, 0),
                "Голодных больше, чем игроков рядом: так не бывает");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More4Rules.Players(1, 0, 2),
                "Сломанных инструментов больше, чем игроков рядом");
    }

    @Test
    void portal_OpenOrHazard_IsUnsafe() {
        More4Rules.Portal open = new More4Rules.Portal(true, true, false, false);
        More4Rules.Portal lava = new More4Rules.Portal(true, true, true, true);
        More4Rules.Portal safe = new More4Rules.Portal(true, true, true, false);

        assertTrue(open.unsafe(), "Портал без ограды небезопасен");
        assertTrue(lava.unsafe(), "Портал у лавы или огня небезопасен");
        assertFalse(safe.unsafe(), "Огороженный портал без опасности — безопасен");
        assertTrue(
                More4Rules.events(signals(new More4Rules.Players(0, 0, 0), open))
                        .containsAll(Set.of(More4Rules.PORTAL_FOUND, More4Rules.PORTAL_UNSAFE)),
                "Найденный небезопасный портал даёт и находку, и ограду");
    }

    @Test
    void lots_FreeAndTight_AreOpposite() {
        assertTrue(
                More4Rules.events(signals(new More4Rules.Lots(true, 8))).contains(More4Rules.SPACE_FOUND),
                "Восемь свободных мест — есть куда расширять план");
        assertTrue(
                More4Rules.events(signals(new More4Rules.Lots(true, 7))).contains(More4Rules.SPACE_TIGHT),
                "Меньше восьми мест — искать шире");
        assertFalse(
                More4Rules.events(signals(new More4Rules.Lots(false, 0))).contains(More4Rules.SPACE_TIGHT),
                "Неизвестное число мест не считается теснотой");
    }

    @Test
    void pets_TamedOnly_AskForGuard() {
        assertTrue(
                More4Rules.events(signals(new More4Rules.Pets(1, 4))).contains(More4Rules.TAMED_PET),
                "Приручённый волк или кошка идут в охрану");
        assertFalse(
                More4Rules.events(signals(new More4Rules.Pets(0, 4))).contains(More4Rules.TAMED_PET),
                "Дикие животные не питомцы");
    }

    @Test
    void rare_CarriedOrMixed_IsAtRisk() {
        assertEquals(
                3,
                More4Rules.rare(GRAPH, Map.of("diamond", 2, "emerald", 1, "oak_log", 5)),
                "Редкие считаются по группе #rare, обычные — нет");
        List<GraphFacts.Chest> chests = List.of(
                chest(Map.of("diamond", 1, "oak_log", 4)), chest(Map.of("diamond", 2)), chest(Map.of("oak_log", 4)));
        assertEquals(1, More4Rules.rareMixed(GRAPH, chests), "Редкое рядом с обычным требует отдельного сундука");
    }

    @Test
    void rare_SeparateChest_DoesNotMix() {
        assertFalse(
                More4Rules.events(signals(new More4Rules.Rare(0, 0))).contains(More4Rules.RARE_FOUND),
                "Редкое в своём сундуке не тревожит");
        assertTrue(
                More4Rules.events(signals(new More4Rules.Rare(1, 0))).contains(More4Rules.RARE_FOUND),
                "Редкое в руках духа — повод убрать отдельно");
    }

    @Test
    void wrongHands_OnlyOwnedMismatch_Counted() {
        List<More4Rules.Hand> hands = List.of(
                new More4Rules.Hand("pickaxe", "axe", true),
                new More4Rules.Hand("pickaxe", "pickaxe", true),
                new More4Rules.Hand("pickaxe", null, false),
                new More4Rules.Hand(null, "axe", true));

        assertEquals(
                1, More4Rules.wrongHands(hands), "Сменить нужно только когда нужный инструмент есть, но не в руке");
    }

    @Test
    void requiredKind_WorkActivities_MapToTool() {
        assertEquals("pickaxe", More4Rules.requiredKind("mining"), "В шахте нужна кирка");
        assertEquals("axe", More4Rules.requiredKind("woodcutting"), "На рубке нужен топор");
        assertEquals("hoe", More4Rules.requiredKind("farming"), "На ферме нужна мотыга");
        assertNull(More4Rules.requiredKind("building"), "Стройке отдельный инструмент не обязателен");
        assertNull(More4Rules.requiredKind(null), "Без занятия требование не выдумывают");
    }

    @Test
    void settlement_InjuredOrHungry_Cautions() {
        assertTrue(
                More4Rules.events(signals(new More4Rules.Settlement(1, 0))).contains(More4Rules.CAUTION),
                "Раненый дух — осторожный режим для всех");
        assertTrue(
                More4Rules.events(signals(new More4Rules.Settlement(0, 2))).contains(More4Rules.CAUTION),
                "Голодные духи — осторожный режим для всех");
        assertFalse(
                More4Rules.events(signals(new More4Rules.Settlement(0, 0))).contains(More4Rules.CAUTION),
                "Здоровое поселение работает обычным режимом");
    }

    @Test
    void flat_LevelAndLoaded_IsFlat() {
        assertTrue(
                More4Rules.flat(64, new int[] {64, 65, 63, 64}, new boolean[] {true, true, true, true}, 1),
                "Соседи в пределах одного блока и загружены — площадка ровная");
    }

    @Test
    void flat_UnloadedNeighbour_IsNotFlat() {
        assertFalse(
                More4Rules.flat(64, new int[] {64, 64}, new boolean[] {true, false}, 1),
                "Незагруженный чанк — неизвестность, а не ровное место: нельзя обещать стройку");
    }

    @Test
    void flat_UnevenNeighbour_IsNotFlat() {
        assertFalse(
                More4Rules.flat(64, new int[] {64, 66}, new boolean[] {true, true}, 1),
                "Перепад больше допустимого — площадка не ровная");
    }

    @Test
    void flat_LengthMismatch_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> More4Rules.flat(64, new int[] {64}, new boolean[] {true, true}, 1),
                "Каждому соседу нужна своя высота: иначе ровность считается по чужим данным");
    }

    @Test
    void signals_NegativeWrongHands_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new More4Rules.Signals(
                        new More4Rules.Players(0, 0, 0),
                        new More4Rules.Portal(false, false, false, false),
                        -1,
                        new More4Rules.Lots(false, 0),
                        new More4Rules.Pets(0, 0),
                        new More4Rules.Rare(0, 0),
                        new More4Rules.Settlement(0, 0)),
                "Отрицательное число духов с неверным инструментом не имеет смысла");
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

    private static More4Rules.Signals signals(More4Rules.Lots lots) {
        return new More4Rules.Signals(
                new More4Rules.Players(0, 0, 0),
                new More4Rules.Portal(false, false, false, false),
                0,
                lots,
                new More4Rules.Pets(0, 0),
                new More4Rules.Rare(0, 0),
                new More4Rules.Settlement(0, 0));
    }

    private static More4Rules.Signals signals(More4Rules.Pets pets) {
        return new More4Rules.Signals(
                new More4Rules.Players(0, 0, 0),
                new More4Rules.Portal(false, false, false, false),
                0,
                new More4Rules.Lots(false, 0),
                pets,
                new More4Rules.Rare(0, 0),
                new More4Rules.Settlement(0, 0));
    }

    private static More4Rules.Signals signals(More4Rules.Rare rare) {
        return new More4Rules.Signals(
                new More4Rules.Players(0, 0, 0),
                new More4Rules.Portal(false, false, false, false),
                0,
                new More4Rules.Lots(false, 0),
                new More4Rules.Pets(0, 0),
                rare,
                new More4Rules.Settlement(0, 0));
    }

    private static More4Rules.Signals signals(More4Rules.Settlement settlement) {
        return new More4Rules.Signals(
                new More4Rules.Players(0, 0, 0),
                new More4Rules.Portal(false, false, false, false),
                0,
                new More4Rules.Lots(false, 0),
                new More4Rules.Pets(0, 0),
                new More4Rules.Rare(0, 0),
                settlement);
    }

    private static GraphFacts.Chest chest(Map<String, Integer> contents) {
        return new GraphFacts.Chest(new GraphFacts.Position(0, 64, 0), contents, 0, 27, GraphFacts.Reach.UNKNOWN);
    }
}
