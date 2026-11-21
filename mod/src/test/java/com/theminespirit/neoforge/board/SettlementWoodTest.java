package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementWoodTest {
    @Test
    void vote_FewOakLogsInJungle_Jungle() {
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of("minecraft:oak_log", 2), Map.of("jungle_log", 6), "jungle"),
                "пара дубовых брёвен в запасе не перевешивает джунгли вокруг: иначе gather:oak_fence (INC-39)"
                        + " При смене vote_FewOakLogsInJungle_Jungle проверь это утверждение.");
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of(), Map.of(), "jungle"),
                "без брёвен в запасе и деревьев рядом порода — по биому (INC-37)"
                        + " Иначе тест vote_FewOakLogsInJungle_Jungle перестанет ловить эту поломку. При смене vote_FewOakLogsInJungle_Jungle проверь это утверждение.");
    }

    @Test
    void vote_SmallStockOakTreesJungleBiome_Biome() {
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of("jungle_log", 9), Map.of("oak_log", 6), "jungle"),
                "запас меньше 16 — решает биом, 6 дубов рядом не голосуют: иначе план закреплял дуб в джунглях "
                        + "(INC-62, правило владельца)"
                        + " При смене vote_SmallStockOakTreesJungleBiome_Biome проверь это утверждение.");
        assertEquals(
                "oak",
                SettlementWood.vote(Map.of("oak_log", 3), Map.of("jungle_log", 20), "oak"),
                "запас меньше 16 — решает биом и при деревьях другой породы рядом (INC-62)"
                        + " Иначе тест vote_SmallStockOakTreesJungleBiome_Biome перестанет ловить эту поломку. При смене vote_SmallStockOakTreesJungleBiome_Biome проверь это утверждение.");
    }

    @Test
    void vote_MiddleOakStockJungleAround_Jungle() {
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of("minecraft:oak_log", 30), Map.of("jungle_log", 15), "jungle"),
                "дуб из прошлых прогонов в сундуках (30) не перевешивает джунгли вокруг: выборка через клетку видит "
                        + "четверть стволов, а ограда вставала дубовой (INC-41)"
                        + " Иначе тест vote_MiddleOakStockJungleAround_Jungle перестанет ловить эту поломку. При смене vote_MiddleOakStockJungleAround_Jungle проверь это утверждение.");
    }

    @Test
    void vote_StockMajority_StockSpecies() {
        assertEquals(
                "oak",
                SettlementWood.vote(Map.of("oak_log", 20), Map.of(), "spruce"),
                "свой запас брёвен (от 16) без деревьев рядом важнее биома: биом — один голос"
                        + " Иначе тест vote_StockMajority_StockSpecies перестанет ловить эту поломку. При смене vote_StockMajority_StockSpecies проверь это утверждение.");
        assertEquals(
                "oak",
                SettlementWood.vote(Map.of("oak_log", 64), Map.of("jungle_log", 20), "jungle"),
                "большой запас своей породы решает: строить есть из чего"
                        + " Иначе тест vote_StockMajority_StockSpecies перестанет ловить эту поломку. При смене vote_StockMajority_StockSpecies проверь это утверждение.");
    }

    @Test
    void vote_SmallOakStockNoTreesJungleBiome_Jungle() {
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of("minecraft:oak_log", 2), Map.of(), "jungle"),
                "2 дубовых бревна без деревьев рядом (вырублены расчисткой) не перебивают биом jungle (INC-42)"
                        + " Иначе тест vote_SmallOakStockNoTreesJungleBiome_Jungle перестанет ловить эту поломку. При смене vote_SmallOakStockNoTreesJungleBiome_Jungle проверь это утверждение.");
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of("oak_log", 15), Map.of(), "jungle"),
                "запас меньше 16 брёвен не голосует"
                        + " Иначе тест vote_SmallOakStockNoTreesJungleBiome_Jungle перестанет ловить эту поломку. При смене vote_SmallOakStockNoTreesJungleBiome_Jungle проверь это утверждение.");
    }

    @Test
    void biomeRules_StoredOakSmallJungleStock_RecomputedToBiome() {
        assertEquals(
                Optional.of("jungle"),
                SettlementWood.biomeRules(Map.of("jungle_log", 9), "jungle"),
                "план с дубом при запасе 9 jungle_log в джунглях нарушает правило — порода пересчитывается по биому "
                        + "(INC-62)"
                        + " Иначе тест biomeRules_StoredOakSmallJungleStock_RecomputedToBiome перестанет ловить эту поломку. При смене biomeRules_StoredOakSmallJungleStock_RecomputedToBiome проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                SettlementWood.biomeRules(Map.of("oak_log", 20), "jungle"),
                "запас от 16 — правило биома не действует, закреплённая порода плана не меняется"
                        + " Иначе тест biomeRules_StoredOakSmallJungleStock_RecomputedToBiome перестанет ловить эту поломку. При смене biomeRules_StoredOakSmallJungleStock_RecomputedToBiome проверь это утверждение.");
    }
}
