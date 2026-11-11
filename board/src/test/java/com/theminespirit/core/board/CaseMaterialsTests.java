package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import java.util.List;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class CaseMaterialsTests {

    private static final MaterialSource FAKE = new MaterialSource() {
        @Override
        public int outputCount(String item) {
            return switch (item) {
                case "chest" -> 1;
                case "stick" -> 4;
                case "crafting_table" -> 1;
                default -> 1;
            };
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return switch (item) {
                case "chest" -> List.of(new Ingredient("oak_planks", 8));
                case "stick" -> List.of(new Ingredient("oak_planks", 2));
                case "crafting_table" -> List.of(new Ingredient("minecraft:oak_planks", 4));
                default -> List.of();
            };
        }

        @Override
        public Map<String, Integer> schematicBlocks(String file) {
            return "fence".equals(file) ? Map.of("minecraft:oak_fence", 32) : Map.of();
        }
    };

    private static TaskNode craft(String item, int count) {
        return new ScriptCall("craft", Map.of("item", item, "count", count));
    }

    private static TaskNode gotoStep() {
        return new ScriptCall("goto", Map.of("x", 1, "y", 2, "z", 3));
    }

    @Test
    void craftNeed_PlanWithOtherCrafts_CountsOnlyItemTimesCrafts() {
        TaskNode plan = new SequenceNode(List.of(craft("stick", 4), gotoStep(), craft("chest", 2)));

        assertEquals(
                Map.of("oak_planks", 16),
                CaseMaterials.craftNeed(plan, FAKE, "minecraft:chest"),
                "Передача крафтеру — ингредиенты всех крафтов предмета, без чужих шагов: набор на один крафт "
                        + "ронял «craft torch ×8: coal 1 из 2»");
    }

    @Test
    void need_CraftCase_MultipliesIngredientsByCrafts() {
        Map<String, Integer> need = CaseMaterials.need(new SequenceNode(List.of(gotoStep(), craft("chest", 2))), FAKE);

        assertEquals(
                Map.of("oak_planks", 16),
                need,
                "Сундук крафтится по одному за крафт из 8 досок: на два нужно 16 досок, иначе "
                        + "гейт пропустит кейс без половины материала");
    }

    @Test
    void need_CraftWithOutput_RoundsCraftsUp() {
        Map<String, Integer> need = CaseMaterials.need(craft("stick", 5), FAKE);

        assertEquals(
                Map.of("oak_planks", 4),
                need,
                "Палки идут по 4 за крафт из 2 досок: на 5 нужно два крафта и 4 доски, "
                        + "иначе не хватит на последнюю палку");
    }

    @Test
    void need_Schematic_CountsBlocks() {
        Map<String, Integer> need = CaseMaterials.need(
                new ScriptCall("build_schematic", Map.of("file", "fence", "x", 0, "y", 0, "z", 0)), FAKE);

        assertEquals(
                Map.of("oak_fence", 32),
                need,
                "Забор строится схемой: гейт считает материалы схемы, а не число в коде");
    }

    @Test
    void need_PlaceBlock_CountsEveryPlacement() {
        TaskNode plan = new SequenceNode(List.of(
                new ScriptCall("place_block", Map.of("block", "minecraft:torch", "x", 1, "y", 1, "z", 1)),
                new ScriptCall("place_block", Map.of("block", "minecraft:torch", "x", 2, "y", 1, "z", 1))));

        assertEquals(
                Map.of("torch", 2),
                CaseMaterials.need(plan, FAKE),
                "Каждая постановка факела требует по факелу: имя приводится к короткому виду "
                        + "как ключ снимка запасов");
    }

    @Test
    void need_Fill_CountsWholeArea() {
        TaskNode plan = new ScriptCall(
                "fill",
                Map.of(
                        "block", "minecraft:dirt",
                        "x1", 0,
                        "y1", 64,
                        "z1", 0,
                        "x2", 2,
                        "y2", 64,
                        "z2", 3));

        assertEquals(
                Map.of("dirt", 12),
                CaseMaterials.need(plan, FAKE),
                "Заделка пролома площадью — один fill: материалов нужно по числу клеток области, иначе "
                        + "граф не поднимет 12 блоков и задание упадёт на нехватке материала");
    }

    @Test
    void need_FillBrokenArea_IsReported() {
        TaskNode plan = new ScriptCall(
                "fill",
                Map.of(
                        "block", "minecraft:dirt",
                        "x1", 2,
                        "y1", 64,
                        "z1", 0,
                        "x2", 0,
                        "y2", 64,
                        "z2", 3));

        assertThrows(
                IllegalArgumentException.class,
                () -> CaseMaterials.need(plan, FAKE),
                "Перевёрнутая область fill — ошибка плана, а не ноль материалов: молчаливый 0 снова уронит "
                        + "ремонт в нехватку блока");
    }

    @Test
    void need_FallbackAlternatives_RequiresPrimaryOnly() {
        TaskNode fallback = new FallbackNode(List.of(
                new ScriptCall("acquire", Map.of("item", "coal", "count", 1)),
                new ScriptCall("acquire", Map.of("item", "charcoal", "count", 1))));

        assertEquals(
                Map.of("coal", 1),
                CaseMaterials.need(fallback, FAKE),
                "Ветки fallback — замена друг другу: доска требует только первую (уголь), а не обе. "
                        + "Иначе древесный уголь (обжиг, не добыча) появляется в requiredItems и добыча "
                        + "проваливает сад 0/24 (наблюдение 7)");
    }

    @Test
    void need_FallbackFirstWithoutMaterial_UsesNextBranch() {
        TaskNode fallback = new FallbackNode(List.of(
                new ScriptCall("chop_tree", Map.of("species", "oak", "count", 1)),
                new ScriptCall("acquire", Map.of("item", "oak_log", "count", 1))));

        assertEquals(
                Map.of("oak_log", 1),
                CaseMaterials.need(fallback, FAKE),
                "Если первая ветка ничего не берёт со склада (рубка), нужду задаёт следующая: иначе "
                        + "добыча брёвен теряла бы материал из гейта");
    }

    @Test
    void need_AcquireStep_CountsItem() {
        Map<String, Integer> need =
                CaseMaterials.need(new ScriptCall("acquire", Map.of("item", "dirt", "count", 5)), FAKE);

        assertEquals(Map.of("dirt", 5), need, "Шаг acquire требует предмет со склада: гейт обязан его учесть");
    }

    @Test
    void need_UnknownSteps_Ignored() {
        TaskNode plan = new SequenceNode(List.of(
                new ScriptCall("clear_area", Map.of("x1", 0, "x2", 1)),
                new ScriptCall("dig_descent", Map.of("direction", "south"))));

        assertEquals(
                Map.of(),
                CaseMaterials.need(plan, FAKE),
                "Шаги без материалов (зачистка, спуск) не рождают нужды: пустой гейт — " + "задание выполнимо сразу");
    }

    @Test
    void need_PrefixedIngredient_IsNormalized() {
        Map<String, Integer> need = CaseMaterials.need(craft("crafting_table", 1), FAKE);

        assertEquals(
                Map.of("oak_planks", 4),
                need,
                "Ингредиент с полным id приводится к короткому виду: иначе гейт не увидит "
                        + "склад, ключи которого короткие, и будет требовать доски вечно");
    }

    @Test
    void need_NullPlan_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> CaseMaterials.need(null, FAKE),
                "Без плана нечего считать: null обязан падать, а не молча давать пустой гейт");
    }

    @Test
    void need_NullSource_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> CaseMaterials.need(craft("chest", 1), null),
                "Без источника рецепт не прочитать: null обязан падать, а не молча давать пустой гейт");
    }

    @Test
    void gate_FullStock_LeavesNothingMissing() {
        Map<String, Integer> need = CaseMaterials.need(craft("chest", 2), FAKE);

        assertEquals(
                Map.of(),
                ResourceGate.missing(new StockNeed(need), new StockView(Map.of("oak_planks", 16))),
                "Полный запас закрывает нужду кейса: добыча не ставится, шаг берётся в работу сразу");
    }

    @Test
    void gate_PartialStock_ReportsShortage() {
        Map<String, Integer> need = CaseMaterials.need(craft("chest", 2), FAKE);

        assertEquals(
                Map.of("oak_planks", 6),
                ResourceGate.missing(new StockNeed(need), new StockView(Map.of("oak_planks", 10))),
                "Недостача — разница нужды и запаса: нужно 16 досок, есть 10 — добыть 6");
    }

    @Test
    void need_CraftWithoutKnownRecipe_Ignored() {
        Map<String, Integer> need = CaseMaterials.need(craft("unknown_thing", 3), FAKE);

        assertEquals(
                Map.of(),
                need,
                "Неизвестный рецепт не даёт материалов: молчаливая догадка подставила бы "
                        + "чужую нужду, а несостоявшийся рецепт ничего не требует");
    }

    @Property
    void need_AnyCraft_StaysPositive(@ForAll @IntRange(min = 1, max = 64) int count) {
        int table = CaseMaterials.need(craft("crafting_table", count), FAKE).getOrDefault("oak_planks", 0);
        int chest = CaseMaterials.need(craft("chest", count), FAKE).getOrDefault("oak_planks", 0);

        assertTrue(
                table >= 4 && chest >= 8,
                "Нужда в досках положительна при любом числе крафтов: стол — 4 доски, " + "сундук — 8 (получено "
                        + table + " и " + chest + ")");
        assertTrue(chest >= table, "Сундук не может требовать меньше досок, чем стол того же числа крафтов");
    }
}
