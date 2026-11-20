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
                "mine_vein — горная деятельность: набор шахты держит кирки и факелы, иначе шахтёр "
                        + "останется без инструмента. При смене activityOfScript проверь mine_vein");
    }

    @Test
    void activityOfScript_ChopTree_ReturnsWoodcutting() {
        assertEquals(
                "woodcutting",
                KitFor.activityOfScript("chop_tree").orElse(null),
                "chop_tree — лесная деятельность: набор рубки держит топор, иначе дерево не срубить. "
                        + "При смене activityOfScript проверь chop_tree");
    }

    @Test
    void activityOfScript_PlantCrop_ReturnsFarming() {
        assertEquals(
                "farming",
                KitFor.activityOfScript("plant_crop").orElse(null),
                "plant_crop — фермерская деятельность: набор фермы держит мотыгу и семена, иначе ферма "
                        + "встанет. При смене activityOfScript проверь plant_crop");
    }

    @Test
    void activityOfScript_PlaceBlock_ReturnsBuilding() {
        assertEquals(
                "building",
                KitFor.activityOfScript("place_block").orElse(null),
                "place_block — строительная деятельность: набор стройки держит блоки и верстак, иначе "
                        + "стройка встанет. При смене activityOfScript проверь place_block");
    }

    @Test
    void activityOfScript_NonWorkingScript_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                KitFor.activityOfScript("unload"),
                "unload не рабочая деятельность: набора инструментов у разгрузки нет, иначе дух понесёт "
                        + "лишнее на склад. При смене activityOfScript проверь unload");
    }

    @Test
    void activityOfPlan_MineVeinThenUnload_ReturnsMining() {
        SequenceNode plan = new SequenceNode(
                List.of(new ScriptCall("mine_vein", Map.of("ore", "iron")), new ScriptCall("unload", Map.of())));
        assertEquals(
                "mining",
                KitFor.activityOfPlan(plan).orElse(null),
                "план шахтёра с разгрузкой в конце остаётся горным: дух в походе обязан хранить кирку, "
                        + "иначе шахта встанет. При смене activityOfPlan проверь mining и unload");
    }

    @Test
    void activityOfPlan_OnlyNonWorkingScripts_ReturnsEmpty() {
        SequenceNode plan = new SequenceNode(
                List.of(new ScriptCall("wait", Map.of("ticks", 5)), new ScriptCall("unload", Map.of())));
        assertEquals(
                Optional.empty(),
                KitFor.activityOfPlan(plan),
                "план без рабочих скриптов не имеет деятельности: набор духа — только постоянный, иначе "
                        + "появятся чужие правила. При смене activityOfPlan проверь wait и unload");
    }

    @Test
    void kitFor_AdultMining_ContainsConstantAndActivityRules() {
        BaseKit kit = KIT_FOR.kit("mining", false);

        assertTrue(
                covers(kit, "iron_sword"),
                "меч входит в постоянную часть, иначе дух пойдёт в бой без защиты и потеряет снаряжение. "
                        + "При смене kit проверь правило iron_sword в base_kit.json");
        assertTrue(
                covers(kit, "bow"),
                "лук входит в постоянную часть, иначе дух не добудет еду издалека и останется голодным. "
                        + "При смене kit проверь правило bow в base_kit.json");
        assertTrue(
                covers(kit, "arrow"),
                "стрелы входят в постоянную часть, иначе лук бесполезен и охота не принесёт еды. "
                        + "При смене kit проверь правило arrow в base_kit.json");
        assertTrue(
                covers(kit, "bread"),
                "еда входит в постоянную часть, иначе дух голодает в походе и теряет здоровье. "
                        + "При смене kit проверь категорию еды в base_kit.json");
        assertTrue(
                covers(kit, "iron_pickaxe"),
                "кирка входит в набор шахты, иначе шахта встанет и руда останется в земле. "
                        + "При смене kit проверь правило кирки для mining");
        assertFalse(
                covers(kit, "oak_planks"),
                "доски вне набора шахты и вне постоянной части, иначе дух потащит лишнее в шахту. "
                        + "При смене kit проверь, что oak_planks не покрыт правилами");
    }

    @Test
    void kitFor_AdultNoActivity_ContainsOnlyConstant() {
        BaseKit kit = KIT_FOR.kit(null, false);

        assertTrue(
                covers(kit, "iron_sword"),
                "меч в постоянной части независимо от деятельности, иначе дух пойдёт в поход без защиты. "
                        + "При смене kit проверь постоянную часть без деятельности");
        assertFalse(
                covers(kit, "iron_pickaxe"),
                "без деятельности кирка вне набора, иначе дух потащит в поход лишний инструмент. "
                        + "При смене kit проверь, что кирка не покрыта без mining");
        assertFalse(
                covers(kit, "iron_axe"),
                "без деятельности топор вне набора, иначе дух потащит в поход лишний инструмент. "
                        + "При смене kit проверь, что топор не покрыт без woodcutting");
        assertFalse(
                covers(kit, "iron_hoe"),
                "без деятельности мотыга вне набора, иначе дух потащит в поход лишний инструмент. "
                        + "При смене kit проверь, что мотыга не покрыта без farming");
    }

    @Test
    void kitFor_UnknownActivity_ContainsOnlyConstant() {
        BaseKit kit = KIT_FOR.kit("unknown_activity", false);

        assertTrue(
                covers(kit, "iron_sword"),
                "меч в постоянной части независимо от деятельности, иначе дух пойдёт в поход без защиты. "
                        + "При смене kit проверь постоянную часть при неизвестной деятельности");
        assertFalse(
                covers(kit, "iron_pickaxe"),
                "неизвестная деятельность не добавляет правил, иначе дух потащит чужой инструмент. "
                        + "При смене kit проверь, что неизвестное имя не расширяет набор");
    }

    @Test
    void kitFor_Child_ContainsOnlyFood() {
        BaseKit kit = KIT_FOR.kit(null, true);

        assertTrue(
                covers(kit, "bread"),
                "у ребёнка остаётся еда, иначе он голодает и не растёт. При смене детского набора проверь категорию еды у ребёнка в base_kit.json");
        assertFalse(
                covers(kit, "iron_sword"),
                "у ребёнка нет меча, иначе он пойдёт в бой вместо взрослого. При смене детского набора проверь правило меча у ребёнка в base_kit.json");
        assertFalse(
                covers(kit, "bow"),
                "у ребёнка нет лука, иначе он понесёт оружие на плечах. При смене детского набора проверь правило лука у ребёнка в base_kit.json");
        assertFalse(
                covers(kit, "arrow"),
                "у ребёнка нет стрел, иначе он понесёт боеприпасы без лука. При смене детского набора проверь правило стрел у ребёнка в base_kit.json");
        assertFalse(
                covers(kit, "torch"),
                "у ребёнка нет факелов, иначе он понесёт припасы шахты в деревне. При смене детского набора проверь правило факелов у ребёнка в base_kit.json");
        assertFalse(
                covers(kit, "iron_pickaxe"),
                "у ребёнка нет кирки, иначе он пойдёт в шахту без взрослого. При смене детского набора проверь правило кирки у ребёнка в base_kit.json");
    }

    @Test
    void kitFor_ChildWithMining_StillOnlyFood() {
        BaseKit kit = KIT_FOR.kit("mining", true);

        assertTrue(
                covers(kit, "bread"),
                "даже в шахте ребёнок хранит еду, иначе он голодает под землёй. При смене детского набора с шахтой проверь еду у ребёнка в base_kit.json");
        assertFalse(
                covers(kit, "iron_pickaxe"),
                "даже в шахте у ребёнка нет кирки, иначе он пойдёт в забой без взрослого. При смене детского набора с шахтой проверь кирку");
        assertFalse(
                covers(kit, "torch"),
                "даже в шахте у ребёнка нет факелов, иначе он понесёт припасы без нужды. При смене детского набора с шахтой проверь факелы");
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
                "набор шахты оставляет две лучшие кирки (алмаз 400 и железо 80), каменная (10) уходит, "
                        + "иначе дух потащит третью кирку. При смене kit проверь квоту кирок");
    }

    @Test
    void kitFor_AdultNoActivity_TorchQuotaIsConstant() {
        List<Stack> inventory = List.of(new Stack("torch", 0, 32, 64));

        List<Slice> surplus = KIT_FOR.kit(null, false).surplus(inventory, VALUES);

        assertEquals(
                List.of(new Slice(0, 8)),
                surplus,
                "без деятельности действует постоянная квота факелов 24, иначе лишние уйдут на склад. "
                        + "При смене kit проверь квоту факелов");
    }

    @Test
    void kitFor_AdultMining_TorchQuotaExpanded() {
        List<Stack> inventory = List.of(new Stack("torch", 0, 32, 64));

        List<Slice> surplus = KIT_FOR.kit("mining", false).surplus(inventory, VALUES);

        assertEquals(
                List.of(),
                surplus,
                "в шахте действует расширенная квота факелов 32, иначе шахтёр останется в темноте. "
                        + "При смене kit проверь расширенную квоту");
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
                "набор шахты добавляет кирку и расширяет квоту факелов, но не вытесняет постоянную "
                        + "часть, иначе дух останется без снаряжения. При смене kit проверь вытеснение");
    }

    @Test
    void kitFor_ChildFoodInQuota_GivesNothing() {
        List<Stack> inventory = List.of(new Stack("bread", 0, 8, 64), new Stack("cooked_beef", 0, 8, 64));

        List<Slice> surplus = KIT_FOR.kit("mining", true).surplus(inventory, VALUES);

        assertEquals(
                List.of(),
                surplus,
                "у ребёнка вся еда в квоте 16, лишнего нет, иначе ребёнок отдаст паёк на склад и останется голодным. "
                        + "При смене детского набора проверь квоту еды");
    }

    private static boolean covers(BaseKit kit, String item) {
        return kit.rules().stream().anyMatch(rule -> rule.covers(item, VALUES));
    }
}
