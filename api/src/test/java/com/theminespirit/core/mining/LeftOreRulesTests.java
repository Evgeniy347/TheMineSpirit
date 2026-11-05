package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void isToolTier_MarkerKey_True() {
        assertTrue(
                LeftOreRules.isToolTier(LeftOreRules.LUCK_PICKAXE),
                "pickaxe_luck — маркер требования кирки, а не предмет склада: добывать его нельзя");
        assertTrue(
                LeftOreRules.isToolTier(LeftOreRules.STONE_TIER),
                "pickaxe_stone_tier — тоже маркер требования, он не должен попадать в цели добычи");
    }

    @Test
    void isToolTier_RealItem_False() {
        assertFalse(
                LeftOreRules.isToolTier("minecraft:stone_pickaxe"),
                "Настоящая кирка — предмет: её можно взять со склада или скрафтить");
        assertFalse(LeftOreRules.isToolTier("dirt"), "Обычный материал не является маркером инструмента");
    }

    @Test
    void requiredTool_GoldOre_NeedsIronTier() {
        assertEquals(
                LeftOreRules.IRON_TIER,
                LeftOreRules.requiredTool("minecraft:gold_ore"),
                "золото добывается железной киркой и лучше — задание ждёт её");
    }

    @Test
    void itemOf_LeftOres_MapToGraphItems() {
        assertEquals(
                "lapis_lazuli",
                LeftOreRules.itemOf("minecraft:deepslate_lapis_ore"),
                "Оставленный лазурит ищется в графе как lapis_lazuli: иначе узел mine_lapis его не видит");
        assertEquals(
                "diamond",
                LeftOreRules.itemOf("minecraft:diamond_ore"),
                "Оставленный алмаз — предмет diamond, а не блок руды");
        assertEquals(
                "gold_ingot",
                LeftOreRules.itemOf("minecraft:nether_gold_ore"),
                "Золотая руда даёт gold_ingot в графе: по нему закрывается нехватка");
        assertEquals(
                "redstone",
                LeftOreRules.itemOf("minecraft:redstone_ore"),
                "Редстоун ищется как redstone, а не как redstone_ore");
    }

    @Test
    void itemOf_NotOre_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LeftOreRules.itemOf("minecraft:dirt"),
                "Не руда не может лежать в памяти оставленной руды: молчаливый ANY увёл бы духа к грязи");
    }
}
