package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementWoodTest {
    @Test
    void vote_FewOakLogsInJungle_Jungle() {
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of("minecraft:oak_log", 2), Map.of("jungle_log", 6), "jungle"),
                "пара дубовых брёвен в запасе не перевешивает джунгли вокруг: иначе gather:oak_fence (INC-39)");
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of(), Map.of(), "jungle"),
                "без брёвен в запасе и деревьев рядом порода — по биому (INC-37)");
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of("oak_log", 3), Map.of("jungle_log", 20), "oak"),
                "деревья вокруг дома перевешивают пару брёвен чужой породы в запасе (INC-39)");
    }

    @Test
    void vote_MiddleOakStockJungleAround_Jungle() {
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of("minecraft:oak_log", 30), Map.of("jungle_log", 15), "jungle"),
                "дуб из прошлых прогонов в сундуках (30) не перевешивает джунгли вокруг: выборка через клетку видит "
                        + "четверть стволов, а ограда вставала дубовой (INC-41)");
    }

    @Test
    void vote_StockMajority_StockSpecies() {
        assertEquals(
                "oak",
                SettlementWood.vote(Map.of("oak_log", 20), Map.of(), "spruce"),
                "свой запас брёвен (от 16) без деревьев рядом важнее биома: биом — один голос");
        assertEquals(
                "oak",
                SettlementWood.vote(Map.of("oak_log", 64), Map.of("jungle_log", 20), "jungle"),
                "большой запас своей породы решает: строить есть из чего");
    }

    @Test
    void vote_SmallOakStockNoTreesJungleBiome_Jungle() {
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of("minecraft:oak_log", 2), Map.of(), "jungle"),
                "2 дубовых бревна без деревьев рядом (вырублены расчисткой) не перебивают биом jungle (INC-42)");
        assertEquals(
                "jungle",
                SettlementWood.vote(Map.of("oak_log", 15), Map.of(), "jungle"),
                "запас меньше 16 брёвен не голосует");
    }
}
