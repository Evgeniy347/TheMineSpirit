package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LeftOreRulesTests {
    @Test
    void requiredTool_Diamond_NeedsLuckPickaxe() {
        assertEquals(
                LeftOreRules.LUCK_PICKAXE,
                LeftOreRules.requiredTool("minecraft:deepslate_diamond_ore"),
                "оставленный алмаз ждёт кирку с Удачей или Шёлковым касанием — простая кирка снова его потеряет");
    }

    @Test
    void requiredTool_IronOre_NeedsStoneTier() {
        assertEquals(
                LeftOreRules.STONE_TIER,
                LeftOreRules.requiredTool("minecraft:iron_ore"),
                "железо, оставленное деревянной киркой, ждёт каменную кирку или лучше");
    }

    @Test
    void requiredTool_GoldOre_NeedsIronTier() {
        assertEquals(
                LeftOreRules.IRON_TIER,
                LeftOreRules.requiredTool("minecraft:gold_ore"),
                "золото добывается железной киркой и лучше — задание ждёт её");
    }
}
