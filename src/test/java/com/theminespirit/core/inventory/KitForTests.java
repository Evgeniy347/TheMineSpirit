package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KitForTests {
    private static final ValueTable VALUES = ValueTable.load();
    private static final KitFor KIT_FOR = KitFor.load();

    @Test
    void activityOfScript_MineVein_ReturnsMining() {
        assertEquals(
                "mining",
                KitFor.activityOfScript("mine_vein").orElse(null),
                "mine_vein — горная деятельность: набор шахты держит кирки и факелы");
    }

    @Test
    void activityOfScript_ChopTree_ReturnsWoodcutting() {
        assertEquals(
                "woodcutting",
                KitFor.activityOfScript("chop_tree").orElse(null),
                "chop_tree — лесная деятельность: набор рубки держит топор");
    }

    @Test
    void activityOfScript_PlantCrop_ReturnsFarming() {
        assertEquals(
                "farming",
                KitFor.activityOfScript("plant_crop").orElse(null),
                "plant_crop — фермерская деятельность: набор фермы держит мотыгу и семена");
    }

    @Test
    void activityOfScript_PlaceBlock_ReturnsBuilding() {
        assertEquals(
                "building",
                KitFor.activityOfScript("place_block").orElse(null),
                "place_block — строительная деятельность: набор стройки держит блоки и верстак");
    }

    @Test
    void activityOfScript_NonWorkingScript_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                KitFor.activityOfScript("unload"),
                "unload не рабочая деятельность: набора инструментов у разгрузки нет");
    }

    @Test
    void activityOfPlan_MineVeinThenUnload_ReturnsMining() {
        SequenceNode plan = new SequenceNode(
                List.of(new ScriptCall("mine_vein", Map.of("ore", "iron")), new ScriptCall("unload", Map.of())));
        assertEquals(
                "mining",
                KitFor.activityOfPlan(plan).orElse(null),
                "план шахтёра с разгрузкой в конце остаётся горным: дух в походе обязан хранить кирку");
    }

    @Test
    void activityOfPlan_OnlyNonWorkingScripts_ReturnsEmpty() {
        SequenceNode plan = new SequenceNode(
                List.of(new ScriptCall("wait", Map.of("ticks", 5)), new ScriptCall("unload", Map.of())));
        assertEquals(
                Optional.empty(),
                KitFor.activityOfPlan(plan),
                "план без рабочих скриптов не имеет деятельности: набор духа — только постоянный");
    }

    @Test
    void kitFor_AdultMining_ContainsConstantAndActivityRules() {
        BaseKit kit = KIT_FOR.kit("mining", false);

        assertTrue(covers(kit, "iron_sword"), "меч входит в постоянную часть");
        assertTrue(covers(kit, "bow"), "лук входит в постоянную часть");
        assertTrue(covers(kit, "arrow"), "стрелы входят в постоянную часть");
        assertTrue(covers(kit, "bread"), "еда входит в постоянную часть");
        assertTrue(covers(kit, "iron_pickaxe"), "кирка входит в набор шахты");
        assertFalse(covers(kit, "oak_planks"), "доски вне набора шахты и вне постоянной части");
    }

    @Test
    void kitFor_AdultNoActivity_ContainsOnlyConstant() {
        BaseKit kit = KIT_FOR.kit(null, false);

        assertTrue(covers(kit, "iron_sword"), "меч в постоянной части независимо от деятельности");
        assertFalse(covers(kit, "iron_pickaxe"), "без деятельности кирка вне набора");
        assertFalse(covers(kit, "iron_axe"), "без деятельности топор вне набора");
        assertFalse(covers(kit, "iron_hoe"), "без деятельности мотыга вне набора");
    }

    @Test
    void kitFor_UnknownActivity_ContainsOnlyConstant() {
        BaseKit kit = KIT_FOR.kit("unknown_activity", false);

        assertTrue(covers(kit, "iron_sword"), "меч в постоянной части независимо от деятельности");
        assertFalse(covers(kit, "iron_pickaxe"), "неизвестная деятельность не добавляет правил");
    }

    @Test
    void kitFor_Child_ContainsOnlyFood() {
        BaseKit kit = KIT_FOR.kit(null, true);

        assertTrue(covers(kit, "bread"), "у ребёнка остаётся еда");
        assertFalse(covers(kit, "iron_sword"), "у ребёнка нет меча");
        assertFalse(covers(kit, "bow"), "у ребёнка нет лука");
        assertFalse(covers(kit, "arrow"), "у ребёнка нет стрел");
        assertFalse(covers(kit, "torch"), "у ребёнка нет факелов");
        assertFalse(covers(kit, "iron_pickaxe"), "у ребёнка нет кирки");
    }

    @Test
    void kitFor_ChildWithMining_StillOnlyFood() {
        BaseKit kit = KIT_FOR.kit("mining", true);

        assertTrue(covers(kit, "bread"), "даже в шахте ребёнок хранит еду");
        assertFalse(covers(kit, "iron_pickaxe"), "даже в шахте у ребёнка нет кирки");
        assertFalse(covers(kit, "torch"), "даже в шахте у ребёнка нет факелов");
    }

    @Test
    void kitFor_AdultMining_KeepsBestTwoPickaxes() {
        List<Stack> inventory = List.of(
                new Stack("iron_pickaxe", 0, 1, 1),
                new Stack("stone_pickaxe", 0, 1, 1),
                new Stack("diamond_pickaxe", 0, 1, 1));

        List<Slice> surplus = KIT_FOR.kit("mining", false).surplus(inventory, VALUES);

        assertEquals(
                List.of(new Slice(1, 1)),
                surplus,
                "набор шахты оставляет две лучшие кирки (алмаз 400 и железо 80), каменная (10) уходит");
    }

    @Test
    void kitFor_AdultNoActivity_TorchQuotaIsConstant() {
        List<Stack> inventory = List.of(new Stack("torch", 0, 32, 64));

        List<Slice> surplus = KIT_FOR.kit(null, false).surplus(inventory, VALUES);

        assertEquals(List.of(new Slice(0, 8)), surplus, "без деятельности действует постоянная квота факелов 24");
    }

    @Test
    void kitFor_AdultMining_TorchQuotaExpanded() {
        List<Stack> inventory = List.of(new Stack("torch", 0, 32, 64));

        List<Slice> surplus = KIT_FOR.kit("mining", false).surplus(inventory, VALUES);

        assertEquals(List.of(), surplus, "в шахте действует расширенная квота факелов 32");
    }

    @Test
    void kitFor_AdultMining_ConstantPartNotEvicted() {
        List<Stack> inventory = List.of(
                new Stack("iron_sword", 0, 1, 1),
                new Stack("bow", 0, 1, 1),
                new Stack("arrow", 0, 16, 64),
                new Stack("bread", 0, 16, 64),
                new Stack("torch", 0, 32, 64),
                new Stack("iron_pickaxe", 0, 1, 1));

        List<Slice> surplus = KIT_FOR.kit("mining", false).surplus(inventory, VALUES);

        assertEquals(
                List.of(),
                surplus,
                "набор шахты добавляет кирку и расширяет квоту факелов, но не вытесняет постоянную часть");
    }

    @Test
    void kitFor_ChildFoodInQuota_GivesNothing() {
        List<Stack> inventory = List.of(new Stack("bread", 0, 8, 64), new Stack("cooked_beef", 0, 8, 64));

        List<Slice> surplus = KIT_FOR.kit("mining", true).surplus(inventory, VALUES);

        assertEquals(List.of(), surplus, "у ребёнка вся еда в квоте 16, лишнего нет");
    }

    private static boolean covers(BaseKit kit, String item) {
        return kit.rules().stream().anyMatch(rule -> rule.covers(item, VALUES));
    }
}
