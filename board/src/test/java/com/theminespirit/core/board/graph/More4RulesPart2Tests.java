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

class More4RulesPart2Tests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

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

    @Test
    void players_ImpossibleCounts_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new More4Rules.Players(-1, 0, 0),
                "Отрицательное число игроков рядом невозможно Иначе тест players_ImpossibleCounts_Rejected перестанет ловить эту поломку. При смене players_ImpossibleCounts_Rejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More4Rules.Players(1, 2, 0),
                "Голодных больше, чем игроков рядом: так не бывает Иначе тест players_ImpossibleCounts_Rejected перестанет ловить эту поломку. При смене players_ImpossibleCounts_Rejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More4Rules.Players(1, 0, 2),
                "Сломанных инструментов больше, чем игроков рядом Иначе тест players_ImpossibleCounts_Rejected перестанет ловить эту поломку. При смене players_ImpossibleCounts_Rejected проверь это утверждение.");
    }

    @Test
    void portal_OpenOrHazard_IsUnsafe() {
        More4Rules.Portal open = new More4Rules.Portal(true, true, false, false);
        More4Rules.Portal lava = new More4Rules.Portal(true, true, true, true);
        More4Rules.Portal safe = new More4Rules.Portal(true, true, true, false);
        assertTrue(
                open.unsafe(),
                "Портал без ограды небезопасен Иначе тест portal_OpenOrHazard_IsUnsafe перестанет ловить эту поломку. При смене portal_OpenOrHazard_IsUnsafe проверь это утверждение.");
        assertTrue(
                lava.unsafe(),
                "Портал у лавы или огня небезопасен Иначе тест portal_OpenOrHazard_IsUnsafe перестанет ловить эту поломку. При смене portal_OpenOrHazard_IsUnsafe проверь это утверждение.");
        assertFalse(
                safe.unsafe(),
                "Огороженный портал без опасности — безопасен Иначе тест portal_OpenOrHazard_IsUnsafe перестанет ловить эту поломку. При смене portal_OpenOrHazard_IsUnsafe проверь это утверждение.");
        assertTrue(
                More4Rules.events(signals(new More4Rules.Players(0, 0, 0), open))
                        .containsAll(Set.of(More4Rules.PORTAL_FOUND, More4Rules.PORTAL_UNSAFE)),
                "Найденный небезопасный портал даёт и находку, и ограду Иначе тест portal_OpenOrHazard_IsUnsafe перестанет ловить эту поломку. При смене portal_OpenOrHazard_IsUnsafe проверь это утверждение.");
    }

    @Test
    void lots_FreeAndTight_AreOpposite() {
        assertTrue(
                More4Rules.events(signals(new More4Rules.Lots(true, 8))).contains(More4Rules.SPACE_FOUND),
                "Восемь свободных мест — есть куда расширять план Иначе тест lots_FreeAndTight_AreOpposite перестанет ловить эту поломку. При смене lots_FreeAndTight_AreOpposite проверь это утверждение.");
        assertTrue(
                More4Rules.events(signals(new More4Rules.Lots(true, 7))).contains(More4Rules.SPACE_TIGHT),
                "Меньше восьми мест — искать шире Иначе тест lots_FreeAndTight_AreOpposite перестанет ловить эту поломку. При смене lots_FreeAndTight_AreOpposite проверь это утверждение.");
        assertFalse(
                More4Rules.events(signals(new More4Rules.Lots(false, 0))).contains(More4Rules.SPACE_TIGHT),
                "Неизвестное число мест не считается теснотой Иначе тест lots_FreeAndTight_AreOpposite перестанет ловить эту поломку. При смене lots_FreeAndTight_AreOpposite проверь это утверждение.");
    }

    @Test
    void pets_TamedOnly_AskForGuard() {
        assertTrue(
                More4Rules.events(signals(new More4Rules.Pets(1, 4))).contains(More4Rules.TAMED_PET),
                "Приручённый волк или кошка идут в охрану Иначе тест pets_TamedOnly_AskForGuard перестанет ловить эту поломку. При смене pets_TamedOnly_AskForGuard проверь это утверждение.");
        assertFalse(
                More4Rules.events(signals(new More4Rules.Pets(0, 4))).contains(More4Rules.TAMED_PET),
                "Дикие животные не питомцы Иначе тест pets_TamedOnly_AskForGuard перестанет ловить эту поломку. При смене pets_TamedOnly_AskForGuard проверь это утверждение.");
    }

    @Test
    void rare_CarriedOrMixed_IsAtRisk() {
        assertEquals(
                3,
                More4Rules.rare(GRAPH, Map.of("diamond", 2, "emerald", 1, "oak_log", 5)),
                "Редкие считаются по группе #rare, обычные — нет Иначе тест rare_CarriedOrMixed_IsAtRisk перестанет ловить эту поломку. При смене rare_CarriedOrMixed_IsAtRisk проверь это утверждение.");
        List<GraphFacts.Chest> chests = List.of(
                chest(Map.of("diamond", 1, "oak_log", 4)), chest(Map.of("diamond", 2)), chest(Map.of("oak_log", 4)));
        assertEquals(
                1,
                More4Rules.rareMixed(GRAPH, chests),
                "Редкое рядом с обычным требует отдельного сундука Иначе тест rare_CarriedOrMixed_IsAtRisk перестанет ловить эту поломку. При смене rare_CarriedOrMixed_IsAtRisk проверь это утверждение.");
    }

    @Test
    void rare_SeparateChest_DoesNotMix() {
        assertFalse(
                More4Rules.events(signals(new More4Rules.Rare(0, 0))).contains(More4Rules.RARE_FOUND),
                "Редкое в своём сундуке не тревожит Иначе тест rare_SeparateChest_DoesNotMix перестанет ловить эту поломку. При смене rare_SeparateChest_DoesNotMix проверь это утверждение.");
        assertTrue(
                More4Rules.events(signals(new More4Rules.Rare(1, 0))).contains(More4Rules.RARE_FOUND),
                "Редкое в руках духа — повод убрать отдельно Иначе тест rare_SeparateChest_DoesNotMix перестанет ловить эту поломку. При смене rare_SeparateChest_DoesNotMix проверь это утверждение.");
    }

    @Test
    void wrongHands_OnlyOwnedMismatch_Counted() {
        List<More4Rules.Hand> hands = List.of(
                new More4Rules.Hand("pickaxe", "axe", true),
                new More4Rules.Hand("pickaxe", "pickaxe", true),
                new More4Rules.Hand("pickaxe", null, false),
                new More4Rules.Hand(null, "axe", true));
        assertEquals(
                1,
                More4Rules.wrongHands(hands),
                "Сменить нужно только когда нужный инструмент есть, но не в руке Иначе тест wrongHands_OnlyOwnedMismatch_Counted перестанет ловить эту поломку. При смене wrongHands_OnlyOwnedMismatch_Counted проверь это утверждение.");
    }

    @Test
    void requiredKind_WorkActivities_MapToTool() {
        assertEquals(
                "pickaxe",
                More4Rules.requiredKind("mining"),
                "В шахте нужна кирка Иначе тест requiredKind_WorkActivities_MapToTool перестанет ловить эту поломку. При смене requiredKind_WorkActivities_MapToTool проверь это утверждение.");
        assertEquals(
                "axe",
                More4Rules.requiredKind("woodcutting"),
                "На рубке нужен топор Иначе тест requiredKind_WorkActivities_MapToTool перестанет ловить эту поломку. При смене requiredKind_WorkActivities_MapToTool проверь это утверждение.");
        assertEquals(
                "hoe",
                More4Rules.requiredKind("farming"),
                "На ферме нужна мотыга Иначе тест requiredKind_WorkActivities_MapToTool перестанет ловить эту поломку. При смене requiredKind_WorkActivities_MapToTool проверь это утверждение.");
        assertNull(
                More4Rules.requiredKind("building"),
                "Стройке отдельный инструмент не обязателен Иначе тест requiredKind_WorkActivities_MapToTool перестанет ловить эту поломку. При смене requiredKind_WorkActivities_MapToTool проверь это утверждение.");
        assertNull(
                More4Rules.requiredKind(null),
                "Без занятия требование не выдумывают Иначе тест requiredKind_WorkActivities_MapToTool перестанет ловить эту поломку. При смене requiredKind_WorkActivities_MapToTool проверь это утверждение.");
    }

    @Test
    void settlement_InjuredOrHungry_Cautions() {
        assertTrue(
                More4Rules.events(signals(new More4Rules.Settlement(1, 0))).contains(More4Rules.CAUTION),
                "Раненый дух — осторожный режим для всех Иначе тест settlement_InjuredOrHungry_Cautions перестанет ловить эту поломку. При смене settlement_InjuredOrHungry_Cautions проверь это утверждение.");
        assertTrue(
                More4Rules.events(signals(new More4Rules.Settlement(0, 2))).contains(More4Rules.CAUTION),
                "Голодные духи — осторожный режим для всех Иначе тест settlement_InjuredOrHungry_Cautions перестанет ловить эту поломку. При смене settlement_InjuredOrHungry_Cautions проверь это утверждение.");
        assertFalse(
                More4Rules.events(signals(new More4Rules.Settlement(0, 0))).contains(More4Rules.CAUTION),
                "Здоровое поселение работает обычным режимом Иначе тест settlement_InjuredOrHungry_Cautions перестанет ловить эту поломку. При смене settlement_InjuredOrHungry_Cautions проверь это утверждение.");
    }

    @Test
    void flat_LevelAndLoaded_IsFlat() {
        assertTrue(
                More4Rules.flat(64, new int[] {64, 65, 63, 64}, new boolean[] {true, true, true, true}, 1),
                "Соседи в пределах одного блока и загружены — площадка ровная Иначе тест flat_LevelAndLoaded_IsFlat перестанет ловить эту поломку. При смене flat_LevelAndLoaded_IsFlat проверь это утверждение.");
    }

    @Test
    void flat_UnloadedNeighbour_IsNotFlat() {
        assertFalse(
                More4Rules.flat(64, new int[] {64, 64}, new boolean[] {true, false}, 1),
                "Незагруженный чанк — неизвестность, а не ровное место: нельзя обещать стройку Иначе тест flat_UnloadedNeighbour_IsNotFlat перестанет ловить эту поломку. При смене flat_UnloadedNeighbour_IsNotFlat проверь это утверждение.");
    }

    @Test
    void flat_UnevenNeighbour_IsNotFlat() {
        assertFalse(
                More4Rules.flat(64, new int[] {64, 66}, new boolean[] {true, true}, 1),
                "Перепад больше допустимого — площадка не ровная Иначе тест flat_UnevenNeighbour_IsNotFlat перестанет ловить эту поломку. При смене flat_UnevenNeighbour_IsNotFlat проверь это утверждение.");
    }

    @Test
    void flat_LengthMismatch_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> More4Rules.flat(64, new int[] {64}, new boolean[] {true, true}, 1),
                "Каждому соседу нужна своя высота: иначе ровность считается по чужим данным При смене flat_LengthMismatch_Rejected проверь это утверждение.");
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
                "Отрицательное число духов с неверным инструментом не имеет смысла Иначе тест signals_NegativeWrongHands_Rejected перестанет ловить эту поломку. При смене signals_NegativeWrongHands_Rejected проверь это утверждение.");
    }
}
