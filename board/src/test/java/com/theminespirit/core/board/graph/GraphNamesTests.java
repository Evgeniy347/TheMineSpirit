package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphNamesTests {
    @Test
    void graphItem_Grass_BecomesDirt() {
        assertEquals(
                "dirt",
                GraphNames.graphItem("minecraft:grass_block"),
                "Дёрн — та же земля и в графе: иначе склад видит 1 575 травы, а нужду в земле считает непокрытой"
                        + " При смене graphItem_Grass_BecomesDirt проверь это утверждение.");
        assertEquals(
                "dirt",
                GraphNames.graphItem("grass_block"),
                "Короткое имя сводится так же"
                        + " Иначе тест graphItem_Grass_BecomesDirt перестанет ловить эту поломку. При смене graphItem_Grass_BecomesDirt проверь это утверждение.");
    }

    @Test
    void stock_GrassInStock_CountedAsDirt() {
        assertEquals(
                Map.of("dirt", 96),
                GraphNames.stock(Map.of("minecraft:dirt", 32, "minecraft:grass_block", 64), Set.of()),
                "Запас графа складывает землю с дёрном: это один предмет для нужды поселения"
                        + " Иначе тест stock_GrassInStock_CountedAsDirt перестанет ловить эту поломку. При смене stock_GrassInStock_CountedAsDirt проверь это утверждение.");
    }

    @Test
    void need_GrassInNeed_CountedAsDirt() {
        assertEquals(
                Map.of("dirt", 10),
                GraphNames.need(Map.of("minecraft:grass_block", 10)),
                "Нужда в дёрне — нужда в земле: засыпка принимает любой из двух блоков"
                        + " Иначе тест need_GrassInNeed_CountedAsDirt перестанет ловить эту поломку. При смене need_GrassInNeed_CountedAsDirt проверь это утверждение.");
    }
}
