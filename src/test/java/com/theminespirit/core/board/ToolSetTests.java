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
import net.jqwik.api.constraints.IntRange;
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
    void replacedNotRepaired_WoodenAndStone_ReplacedIronRepaired() {
        for (String item : List.of("minecraft:wooden_pickaxe", "minecraft:stone_axe", "stone_shovel")) {
            assertTrue(
                    ToolSet.replacedNotRepaired(item),
                    item + " не чинится, а крафтится заново (владелец 23.10): ремонт дороже нового");
        }
        for (String item : List.of("minecraft:iron_pickaxe", "minecraft:diamond_sword", "minecraft:shears")) {
            assertFalse(ToolSet.replacedNotRepaired(item), item + " чинится: новый дороже ремонта");
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
    void count_PerSpirit_MultipliesBySpirits() {
        assertEquals(
                3,
                ToolSet.count(Map.of("perSpirit", 1, "minSets", 1), 3),
                "По умолчанию один комплект на духа: три духа поселения — три комплекта");
        assertEquals(
                1,
                ToolSet.count(Map.of("perSpirit", 1, "minSets", 1), 0),
                "Поселение без духов всё равно собирает минимум один комплект");
    }

    @Test
    void count_Fixed_IgnoresSpirits() {
        assertEquals(
                5,
                ToolSet.count(Map.of("perSpirit", 0, "minSets", 5), 3),
                "Фиксированный набор: perSpirit 0 при minSets 5 даёт 5 комплектов на любое число духов");
    }

    @Test
    void count_Default_IsPerSpiritFloorOne() {
        assertEquals(
                4,
                ToolSet.count(Map.of(), 4),
                "Пустая таблица — значения по умолчанию: комплект на духа, но не меньше одного");
    }

    @Test
    void count_NegativeSpirits_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ToolSet.count(Map.of(), -1),
                "Отрицательное число духов обязано отвергаться: комплектов не может быть отрицательно");
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

    @Property
    void count_AnySpirits_AtLeastOne(@ForAll @IntRange(min = 0, max = 64) int spirits) {
        assertTrue(
                ToolSet.count(Map.of("perSpirit", 1, "minSets", 1), spirits) >= 1,
                "Комплектов всегда не меньше одного: ноль комплектов оставил бы поселение без инструментов");
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
