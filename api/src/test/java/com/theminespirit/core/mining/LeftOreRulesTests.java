package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.LeftOreRules;
import org.junit.jupiter.api.Test;

class LeftOreRulesTests {
    @Test
    void requiredTool_Diamond_NeedsLuckPickaxe() {
        assertEquals(
                LeftOreRules.LUCK_PICKAXE,
                LeftOreRules.requiredTool("minecraft:deepslate_diamond_ore"),
                "оставленный алмаз ждёт кирку с Удачей или Шёлковым касанием — простая кирка снова его потеряет"
                        + " При смене requiredTool_Diamond_NeedsLuckPickaxe проверь это утверждение.");
    }

    @Test
    void requiredTool_IronOre_NeedsStoneTier() {
        assertEquals(
                LeftOreRules.STONE_TIER,
                LeftOreRules.requiredTool("minecraft:iron_ore"),
                "железо, оставленное деревянной киркой, ждёт каменную кирку или лучше"
                        + " Иначе тест requiredTool_IronOre_NeedsStoneTier перестанет ловить эту поломку. При смене requiredTool_IronOre_NeedsStoneTier проверь это утверждение.");
    }

    @Test
    void isToolTier_MarkerKey_True() {
        assertTrue(
                LeftOreRules.isToolTier(LeftOreRules.LUCK_PICKAXE),
                "pickaxe_luck — маркер требования кирки, а не предмет склада: добывать его нельзя"
                        + " Иначе тест isToolTier_MarkerKey_True перестанет ловить эту поломку. При смене isToolTier_MarkerKey_True проверь это утверждение.");
        assertTrue(
                LeftOreRules.isToolTier(LeftOreRules.STONE_TIER),
                "pickaxe_stone_tier — тоже маркер требования, он не должен попадать в цели добычи"
                        + " Иначе тест isToolTier_MarkerKey_True перестанет ловить эту поломку. При смене isToolTier_MarkerKey_True проверь это утверждение.");
    }

    @Test
    void isToolTier_RealItem_False() {
        assertFalse(
                LeftOreRules.isToolTier("minecraft:stone_pickaxe"),
                "Настоящая кирка — предмет: её можно взять со склада или скрафтить"
                        + " Иначе тест isToolTier_RealItem_False перестанет ловить эту поломку. При смене isToolTier_RealItem_False проверь это утверждение.");
        assertFalse(
                LeftOreRules.isToolTier("dirt"),
                "Обычный материал не является маркером инструмента"
                        + " Иначе тест isToolTier_RealItem_False перестанет ловить эту поломку. При смене isToolTier_RealItem_False проверь это утверждение.");
    }

    @Test
    void requiredTool_GoldOre_NeedsIronTier() {
        assertEquals(
                LeftOreRules.IRON_TIER,
                LeftOreRules.requiredTool("minecraft:gold_ore"),
                "золото добывается железной киркой и лучше — задание ждёт её"
                        + " Иначе тест requiredTool_GoldOre_NeedsIronTier перестанет ловить эту поломку. При смене requiredTool_GoldOre_NeedsIronTier проверь это утверждение.");
    }

    @Test
    void itemOf_LeftOres_MapToGraphItems() {
        assertEquals(
                "lapis_lazuli",
                LeftOreRules.itemOf("minecraft:deepslate_lapis_ore"),
                "Оставленный лазурит ищется в графе как lapis_lazuli: иначе узел mine_lapis его не видит"
                        + " При смене itemOf_LeftOres_MapToGraphItems проверь это утверждение.");
        assertEquals(
                "diamond",
                LeftOreRules.itemOf("minecraft:diamond_ore"),
                "Оставленный алмаз — предмет diamond, а не блок руды"
                        + " Иначе тест itemOf_LeftOres_MapToGraphItems перестанет ловить эту поломку. При смене itemOf_LeftOres_MapToGraphItems проверь это утверждение.");
        assertEquals(
                "gold_ingot",
                LeftOreRules.itemOf("minecraft:nether_gold_ore"),
                "Золотая руда даёт gold_ingot в графе: по нему закрывается нехватка"
                        + " Иначе тест itemOf_LeftOres_MapToGraphItems перестанет ловить эту поломку. При смене itemOf_LeftOres_MapToGraphItems проверь это утверждение.");
        assertEquals(
                "redstone",
                LeftOreRules.itemOf("minecraft:redstone_ore"),
                "Редстоун ищется как redstone, а не как redstone_ore"
                        + " Иначе тест itemOf_LeftOres_MapToGraphItems перестанет ловить эту поломку. При смене itemOf_LeftOres_MapToGraphItems проверь это утверждение.");
    }

    @Test
    void itemOf_NotOre_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LeftOreRules.itemOf("minecraft:dirt"),
                "Не руда не может лежать в памяти оставленной руды: молчаливый ANY увёл бы духа к грязи"
                        + " Иначе тест itemOf_NotOre_IsRejected перестанет ловить эту поломку. При смене itemOf_NotOre_IsRejected проверь это утверждение.");
    }
}
