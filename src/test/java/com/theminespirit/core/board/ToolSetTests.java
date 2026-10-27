package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.Ingredient;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class ToolSetTests {

    private static final MaterialSource RECIPES = new MaterialSource() {
        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return switch (item) {
                case "minecraft:wooden_pickaxe", "minecraft:wooden_axe", "minecraft:wooden_shovel" ->
                    List.of(new Ingredient("minecraft:oak_planks", 3), new Ingredient("minecraft:stick", 2));
                case "minecraft:stone_pickaxe",
                        "minecraft:stone_axe",
                        "minecraft:stone_shovel",
                        "minecraft:stone_sword" ->
                    List.of(new Ingredient("minecraft:cobblestone", 3), new Ingredient("minecraft:stick", 2));
                default -> List.of();
            };
        }

        @Override
        public Map<String, Integer> schematicBlocks(String file) {
            return Map.of();
        }
    };

    @Test
    void gate_StoneSet_NeedsCobblestone() {
        Map<String, Integer> need = needFor(BoardCase.CRAFT_STONE_PICKAXE, 1);

        assertEquals(
                Map.of("cobblestone", 3, "stick", 2),
                need,
                "Рецепт каменной кирки — 3 булыжника и 2 палки: гейт считает булыжник из рецепта, " + "а не из кода");
    }

    @Test
    void gate_StoneSet_ReportsCobblestoneShortage() {
        Map<String, Integer> need = needFor(BoardCase.CRAFT_STONE_PICKAXE, 1);

        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(need), new StockView(Map.of("stick", 64)));

        assertEquals(
                Map.of("cobblestone", 3),
                missing,
                "Нехватка булыжника под каменный набор попадает в отчёт гейта: по нему доска "
                        + "ставит мелкую задачу «добыть булыжник» деревянной киркой");
    }

    @Test
    void gate_ToolSetCount_MultipliesRecipe() {
        Map<String, Integer> need = needFor(BoardCase.CRAFT_STONE_PICKAXE, 3);

        assertEquals(
                9,
                need.get("cobblestone"),
                "Три комплекта — три каменные кирки: булыжника нужно втрое больше, число комплектов "
                        + "идёт из горячей таблицы board");
    }

    @Property
    void itemId_AnyTool_IsPrefixed(@ForAll("toolCases") BoardCase boardCase) {
        assertTrue(
                ToolSet.itemId(boardCase).startsWith("minecraft:"),
                "Предмет инструмента несёт полный id: по нему craft ищет рецепт сервера");
    }

    @Test
    void toolsConfig_RepeatedKind_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolsConfig(List.of("pickaxe", "pickaxe"), "stone", List.of("iron")),
                "Повтор вида в наборе обязан отвергаться: два одинаковых задания на один инструмент");
    }

    @Test
    void toolsConfig_UnknownKindOrTier_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolsConfig(List.of("hoe"), "stone", List.of("iron")),
                "Неизвестный вид инструмента обязан отвергаться: кейса крафта у него нет");
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolsConfig(List.of("pickaxe"), "wooden", List.of("iron")),
                "Целевой уровень ниже камня обязан отвергаться: набор начинается с камня");
    }

    private static Map<String, Integer> needFor(BoardCase boardCase, int toolCount) {
        TicketTarget target = TicketTarget.at(new BlockCoord(0, 64, 0)).withToolCount(toolCount);
        return CaseMaterials.need(TicketPlans.plan(boardCase, target), RECIPES);
    }

    @Provide
    Arbitrary<BoardCase> toolCases() {
        return Arbitraries.of(BoardCase.values()).filter(ToolSet::isTool);
    }
}
