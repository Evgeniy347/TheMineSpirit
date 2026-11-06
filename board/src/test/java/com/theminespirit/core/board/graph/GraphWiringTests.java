package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphWiringTests {
    @Test
    void stock_MixedSpecies_EachSpeciesOwnNode() {
        Map<String, Integer> stock =
                GraphNames.stock(Map.of("minecraft:spruce_log", 10, "oak_log", 7, "spruce_planks", 3), Set.of());

        assertEquals(
                Map.of("spruce_log", 10, "oak_log", 7, "spruce_planks", 3),
                stock,
                "Каждая порода — свой узел графа: ель не закрывает нехватку дуба и наоборот, «любая порода» — группа "
                        + "#logs/#planks графа");
    }

    @Test
    void stock_CoalsBedsFood_GraphNodes() {
        Map<String, Integer> stock = GraphNames.stock(
                Map.of("coal", 2, "charcoal", 3, "red_bed", 1, "white_bed", 1, "bread", 4, "cooked_beef", 2),
                Set.of("minecraft:bread", "minecraft:cooked_beef"));

        assertEquals(
                List.of(2, 3),
                List.of(stock.get("coal"), stock.get("charcoal")),
                "Каменный и древесный уголь — разные узлы, «любое топливо» — группа #coals графа: " + stock);
        assertEquals(2, stock.get("bed"), "Кровати любого цвета — один узел bed: " + stock);
        assertEquals(6, stock.get(GraphNames.FOOD), "Еда — сумма съедобного по таблице сытности: " + stock);
    }

    @Test
    void stock_MangrovePropagule_CountsAsGraphSapling() {
        Map<String, Integer> stock = GraphNames.stock(Map.of("minecraft:mangrove_propagule", 3), Set.of());

        assertEquals(
                3,
                stock.get("mangrove_sapling"),
                "Саженец мангрового дерева в игре — mangrove_propagule: без перевода в узел графа "
                        + "gather_mangrove_sapling никогда не закроет нехватку, а склад не увидит саженцы: " + stock);
    }

    @Test
    void item_GroupInMangroveSettlement_ResolvesToPropagule() {
        assertEquals(
                "mangrove_propagule",
                GraphNames.item(GraphWorld.GRAPH, "#saplings", "mangrove"),
                "Узел графа mangrove_sapling обязан превращаться в настоящий предмет mangrove_propagule: "
                        + "иначе задание доски просит несуществующий предмет");
    }

    @Test
    void item_GroupInBirchSettlement_ResolvesToBirchMember() {
        assertEquals(
                List.of("birch_log", "jungle_planks", "spruce_log"),
                List.of(
                        GraphNames.item(GraphWorld.GRAPH, "#logs", "birch"),
                        GraphNames.item(GraphWorld.GRAPH, "#planks", "jungle"),
                        GraphNames.item(GraphWorld.GRAPH, "spruce_log", "birch")),
                "Группа «любая порода» в задании доски — порода поселения; предмет определённой породы — как есть");
    }

    @Test
    void kits_MixedTools_BestTierPerKind() {
        Map<String, Integer> kit = GraphKits.of(
                List.of("minecraft:wooden_pickaxe", "minecraft:stone_pickaxe", "golden_axe", "minecraft:stick"),
                GraphWorld.GRAPH);

        assertEquals(
                Map.of("pickaxe", 1, "axe", 0),
                kit,
                "Набор духа — лучший уровень каждого вида; золото считается деревом, палка — не инструмент");
    }

    @Test
    void notes_SameKeyWithinMinute_Suppressed() {
        GraphNotes notes = new GraphNotes();

        boolean first = notes.due("notice:warehouse", 100);
        boolean again = notes.due("notice:warehouse", 100 + GraphNotes.EVERY_TICKS - 1);
        boolean later = notes.due("notice:warehouse", 100 + GraphNotes.EVERY_TICKS);

        assertTrue(first && !again && later, "Просьба стратегу — не чаще раза в минуту: " + first + again + later);
    }

    @Test
    void notes_StrategistNotice_RepeatsRarelyOrOnChange() {
        GraphNotes notes = new GraphNotes();
        String key = "notice:orchard_jungle";
        String text = "нужна постройка orchard_jungle (есть jungle_sapling), её нет в плане";

        boolean first = notes.due(key, text, 100);
        boolean afterMinute = notes.due(key, text, 100 + GraphNotes.EVERY_TICKS);
        boolean beforePeriod = notes.due(key, text, 100 + GraphNotes.NOTICE_EVERY_TICKS - 1);
        boolean atPeriod = notes.due(key, text, 100 + GraphNotes.NOTICE_EVERY_TICKS);
        boolean changed = notes.due(key, text + " (появился дуб)", 100 + GraphNotes.NOTICE_EVERY_TICKS + 1);

        assertTrue(
                first && !afterMinute && !beforePeriod && atPeriod && changed,
                "Одна и та же просьба стратегу не повторяется каждую минуту (наблюдение 8: «нужна постройка "
                        + "orchard_jungle», «нет источника diamond» — раз в минуту): повтор — раз в "
                        + GraphNotes.NOTICE_EVERY_TICKS + " тиков или сразу, если текст изменился; факт: "
                        + first + afterMinute + beforePeriod + atPeriod + changed);
    }
}
