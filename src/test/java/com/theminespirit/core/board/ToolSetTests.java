package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.Ingredient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class ToolSetTests {

    private static final List<BoardCase> EXPECTED_CHAIN = List.of(
            BoardCase.CRAFT_WOODEN_PICKAXE,
            BoardCase.CRAFT_WOODEN_AXE,
            BoardCase.CRAFT_WOODEN_SHOVEL,
            BoardCase.CRAFT_STONE_PICKAXE,
            BoardCase.CRAFT_STONE_AXE,
            BoardCase.CRAFT_STONE_SHOVEL,
            BoardCase.CRAFT_STONE_SWORD);

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
    void chain_DefaultOrder_WoodenThenStone() {
        assertEquals(
                EXPECTED_CHAIN,
                ToolSet.chain(),
                "Цепочка набора — деревянные кирка, топор, лопата, затем каменные кирка, топор, "
                        + "лопата, меч: порядок задаёт, какой кейс откроется следующим");
    }

    @Test
    void chain_EveryWooden_EarlierThanStone() {
        for (BoardCase wooden : ToolSet.wooden()) {
            for (BoardCase stone : ToolSet.stone()) {
                assertTrue(
                        ToolSet.chain().indexOf(wooden) < ToolSet.chain().indexOf(stone),
                        "Деревянный " + wooden + " обязан идти до каменного " + stone
                                + ": каменные инструменты — вторая ступень набора");
            }
        }
    }

    @Test
    void covered_StonePickaxe_CoversWooden() {
        assertTrue(
                ToolSet.covered(BoardCase.CRAFT_WOODEN_PICKAXE, Set.of("stone_pickaxe")),
                "Каменная кирка закрывает шаг деревянной: дух с лучшим инструментом худший не крафтит");
    }

    @Test
    void covered_WoodenPickaxe_DoesNotCoverStone() {
        assertFalse(
                ToolSet.covered(BoardCase.CRAFT_STONE_PICKAXE, Set.of("wooden_pickaxe")),
                "Деревянная кирка не закрывает каменную: шаг каменной ступени остаётся");
    }

    @Test
    void covered_Pickaxe_DoesNotCoverAxe() {
        assertFalse(
                ToolSet.covered(BoardCase.CRAFT_WOODEN_AXE, Set.of("wooden_pickaxe", "stone_pickaxe")),
                "Кирка (pickaxe) не закрывает топор (axe): совпадение суффикса не делает её топором");
    }

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
        assertTrue(
                !ToolSet.stockKey(boardCase).contains(":"),
                "Ключ запаса — короткий вид: гейт сравнивает его со снимком склада один в один");
    }

    @Test
    void missing_AbsentTool_CraftsLowestAvailableStep() {
        ToolsConfig config = new ToolsConfig(List.of("pickaxe"), "stone", List.of("iron", "diamond"));

        assertEquals(
                List.of("minecraft:stone_pickaxe"),
                ToolSet.missing(config, Map.of(), Set.of("minecraft:wooden_pickaxe", "minecraft:stone_pickaxe")),
                "Дух без кирки берёт старшую ступень, на которую есть материалы: камень при булыжнике, "
                        + "а не дерево");
    }

    @Test
    void missing_NoMaterials_CraftsBaseTool() {
        ToolsConfig config = new ToolsConfig(List.of("pickaxe"), "iron", List.of("iron", "diamond"));

        assertEquals(
                List.of("minecraft:wooden_pickaxe"),
                ToolSet.missing(config, Map.of(), Set.of()),
                "Без материалов дух всё равно получает задание на базовую кирку: гейт доски ждёт дерево, "
                        + "а не оставляет духа без инструмента навсегда");
    }

    @Test
    void missing_BelowTier_UpgradesOnlyWithMaterials() {
        ToolsConfig config = new ToolsConfig(List.of("pickaxe"), "iron", List.of("iron", "diamond"));

        assertEquals(
                List.of("minecraft:iron_pickaxe"),
                ToolSet.missing(config, Map.of("stone_pickaxe", 1), Set.of("minecraft:iron_pickaxe")),
                "Каменная кирка ниже уровня набора: при железе дух делает железную");
        assertEquals(
                List.of(),
                ToolSet.missing(config, Map.of("stone_pickaxe", 1), Set.of()),
                "Без материалов дух остаётся с каменной киркой: задание, которое нельзя выполнить, " + "не ставится");
    }

    @Test
    void missing_TierReached_NoTask() {
        ToolsConfig config = new ToolsConfig(List.of("pickaxe", "axe"), "stone", List.of("iron", "diamond"));

        assertEquals(
                List.of(),
                ToolSet.missing(
                        config,
                        Map.of("stone_pickaxe", 1, "iron_axe", 1),
                        Set.of("minecraft:diamond_pickaxe", "minecraft:diamond_axe")),
                "Инструмент не ниже уровня набора закрывает вид: дух алмазную кирку при каменном наборе "
                        + "не крафтит");
    }

    @Test
    void missing_EveryKindIndependent_OnlyOwnKindCrafted() {
        ToolsConfig config = new ToolsConfig(List.of("pickaxe", "axe", "shovel"), "stone", List.of("iron"));

        assertEquals(
                List.of("minecraft:stone_axe", "minecraft:stone_shovel"),
                ToolSet.missing(
                        config, Map.of("stone_pickaxe", 1), Set.of("minecraft:stone_axe", "minecraft:stone_shovel")),
                "Кирка закрывает только свой вид: топор и лопата ждут крафта, а не чужого инструмента");
    }

    @Test
    void missing_RepeatedKind_ConfigRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolsConfig(List.of("pickaxe", "pickaxe"), "stone", List.of("iron")),
                "Повтор вида в наборе обязан отвергаться: два одинаковых задания на один инструмент");
    }

    @Test
    void missing_UnknownKindOrTier_Rejected() {
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
        return Arbitraries.of(ToolSet.chain());
    }
}
