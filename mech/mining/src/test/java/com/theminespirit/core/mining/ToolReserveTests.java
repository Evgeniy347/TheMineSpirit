package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolReserveTests {
    private static final String ORIGIN = ToolReserve.RESOURCE;
    private static final List<String> REPAIR_TIERS = List.of("iron", "diamond");

    @Test
    void loadedFile_MatchDefaults_FractionAndUses() {
        ToolReserve loaded = ToolReserve.load();

        assertEquals(
                ToolReserve.defaults(),
                loaded,
                "Файл порога износа обязан совпадать со значениями по умолчанию (5 %, 3 удара, беречь последний удар "
                        + "зачарованного): расхождение значит, что правку не перенесли в файл или в код"
                        + " Иначе тест loadedFile_MatchDefaults_FractionAndUses перестанет ловить эту поломку. При смене loadedFile_MatchDefaults_FractionAndUses проверь это утверждение.");
    }

    @Test
    void spent_UnwearingTool_IsFalse() {
        assertFalse(
                ToolReserve.defaults().spent(0, 0, false),
                "Инструмент без прочности (maxUses=0) обязан считаться неизношенным: иначе дух бросил бы работу "
                        + "без причины" + " При смене spent_UnwearingTool_IsFalse проверь это утверждение.");
    }

    @Test
    void spent_ZeroRemaining_IsTrue() {
        assertTrue(
                ToolReserve.defaults().spent(0, 251, false),
                "Сломанный инструмент (0 ударов) обязан считаться изношенным: его из руки уже нет"
                        + " Иначе тест spent_ZeroRemaining_IsTrue перестанет ловить эту поломку. При смене spent_ZeroRemaining_IsTrue проверь это утверждение.");
    }

    @Test
    void spent_BelowUsesThreshold_IsTrue() {
        assertTrue(
                ToolReserve.defaults().spent(3, 251, false),
                "Остаток 3 удара равен порогу reserve_uses=3: инструмент пора менять"
                        + " Иначе тест spent_BelowUsesThreshold_IsTrue перестанет ловить эту поломку. При смене spent_BelowUsesThreshold_IsTrue проверь это утверждение.");
        assertFalse(
                ToolReserve.defaults().spent(14, 251, false),
                "Остаток 14 ударов выше порога (5 % от 251 = 13): менять рано"
                        + " Иначе тест spent_BelowUsesThreshold_IsTrue перестанет ловить эту поломку. При смене spent_BelowUsesThreshold_IsTrue проверь это утверждение.");
    }

    @Test
    void spent_FractionCapWins_OnLongLastingTool() {
        assertTrue(
                ToolReserve.defaults().spent(78, 1562, false),
                "Порог 5 % от 1562 = 79 ударов перекрывает reserve_uses=3: долгий инструмент меняется по доле, "
                        + "а не по трём ударам"
                        + " Иначе тест spent_FractionCapWins_OnLongLastingTool перестанет ловить эту поломку. При смене spent_FractionCapWins_OnLongLastingTool проверь это утверждение.");
        assertFalse(
                ToolReserve.defaults().spent(80, 1562, false),
                "Остаток 80 выше порога 79: менять рано"
                        + " Иначе тест spent_FractionCapWins_OnLongLastingTool перестанет ловить эту поломку. При смене spent_FractionCapWins_OnLongLastingTool проверь это утверждение.");
    }

    @Test
    void spent_EnchantedLastUse_IsTrueEvenBelowFraction() {
        ToolReserve reserve = new ToolReserve(0.0, 0, true);

        assertTrue(
                reserve.spent(1, 100, true),
                "keep_enchant_last_use=true обязан считать зачарованный инструмент с последним ударом изношенным: "
                        + "последний удар тратить нельзя"
                        + " Иначе тест spent_EnchantedLastUse_IsTrueEvenBelowFraction перестанет ловить эту поломку. При смене spent_EnchantedLastUse_IsTrueEvenBelowFraction проверь это утверждение.");
        assertFalse(
                reserve.spent(2, 100, true),
                "До последнего удара зачарованный инструмент не изношен"
                        + " Иначе тест spent_EnchantedLastUse_IsTrueEvenBelowFraction перестанет ловить эту поломку. При смене spent_EnchantedLastUse_IsTrueEvenBelowFraction проверь это утверждение.");
    }

    @Test
    void spent_EnchantedLastUseIgnored_WhenFlagOff() {
        ToolReserve reserve = new ToolReserve(0.0, 0, false);

        assertFalse(
                reserve.spent(1, 100, true),
                "При keep_enchant_last_use=false последний удар зачарованного инструмента не защищён"
                        + " Иначе тест spent_EnchantedLastUseIgnored_WhenFlagOff перестанет ловить эту поломку. При смене spent_EnchantedLastUseIgnored_WhenFlagOff проверь это утверждение.");
    }

    @Test
    void spent_WoodenPickaxeOneUseLeft_NotSpent() {
        assertFalse(
                ToolReserve.defaults().spent(1, 59, false, "minecraft:wooden_pickaxe", REPAIR_TIERS),
                "Деревянная кирка с последним ударом обязана остаться годной: дерево не чинят, его тратят до поломки"
                        + " Иначе тест spent_WoodenPickaxeOneUseLeft_NotSpent перестанет ловить эту поломку. При смене spent_WoodenPickaxeOneUseLeft_NotSpent проверь это утверждение.");
    }

    @Test
    void spent_IronPickaxeAtUsesThreshold_Spent() {
        assertTrue(
                ToolReserve.defaults().spent(3, 251, false, "minecraft:iron_pickaxe", REPAIR_TIERS),
                "Железная кирка с остатком 3 удара равна порогу reserve_uses=3: уровень из repairTiers берегут под ремонт"
                        + " Иначе тест spent_IronPickaxeAtUsesThreshold_Spent перестанет ловить эту поломку. При смене spent_IronPickaxeAtUsesThreshold_Spent проверь это утверждение.");
    }

    @Test
    void spent_StonePickaxeZeroRemaining_Spent() {
        assertTrue(
                ToolReserve.defaults().spent(0, 131, false, "minecraft:stone_pickaxe", REPAIR_TIERS),
                "Каменная кирка с нулевым остатком сломана: сломанный инструмент изношен на любом уровне"
                        + " Иначе тест spent_StonePickaxeZeroRemaining_Spent перестанет ловить эту поломку. При смене spent_StonePickaxeZeroRemaining_Spent проверь это утверждение.");
    }

    @Test
    void spent_StoneShovelOneUseLeft_NotSpent() {
        assertFalse(
                ToolReserve.defaults().spent(1, 131, false, "minecraft:stone_shovel", REPAIR_TIERS),
                "Каменная лопата с последним ударом обязана остаться годной: камень не чинят, его тратят до поломки"
                        + " Иначе тест spent_StoneShovelOneUseLeft_NotSpent перестанет ловить эту поломку. При смене spent_StoneShovelOneUseLeft_NotSpent проверь это утверждение.");
    }

    @Test
    void spent_GoldenPickaxeNearThreshold_NotSpent() {
        assertFalse(
                ToolReserve.defaults().spent(3, 33, false, "minecraft:golden_pickaxe", REPAIR_TIERS),
                "Золотая кирка с остатком 3 удара обязана остаться годной: золото не чинят, его тратят до поломки"
                        + " Иначе тест spent_GoldenPickaxeNearThreshold_NotSpent перестанет ловить эту поломку. При смене spent_GoldenPickaxeNearThreshold_NotSpent проверь это утверждение.");
    }

    @Test
    void spent_NetheriteCountsAsDiamond_Spent() {
        assertTrue(
                ToolReserve.defaults().spent(3, 2032, false, "minecraft:netherite_pickaxe", REPAIR_TIERS),
                "Незеритовая кирка с остатком на пороге обязана беречься: незерит чинят как алмазный уровень"
                        + " Иначе тест spent_NetheriteCountsAsDiamond_Spent перестанет ловить эту поломку. При смене spent_NetheriteCountsAsDiamond_Spent проверь это утверждение.");
    }

    @Test
    void spent_IronOutsideRepairTiers_NotSpent() {
        assertFalse(
                ToolReserve.defaults().spent(3, 251, false, "minecraft:iron_pickaxe", List.of("stone")),
                "Железная кирка с остатком 3 удара годна, если железа нет в repairTiers: берегут только уровни из таблицы"
                        + " Иначе тест spent_IronOutsideRepairTiers_NotSpent перестанет ловить эту поломку. При смене spent_IronOutsideRepairTiers_NotSpent проверь это утверждение.");
    }

    @Test
    void parse_FractionOutOfRange_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ToolReserve.parse("""
                        {"reserve_fraction": 0.9, "reserve_uses": 3, "keep_enchant_last_use": true}
                        """, ORIGIN),
                "Доля больше половины прочности обязана быть ошибкой: порог 0.9 менял бы почти новый инструмент"
                        + " Иначе тест parse_FractionOutOfRange_Throws перестанет ловить эту поломку. При смене parse_FractionOutOfRange_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("0.9"),
                "Сообщение обязано называть неверную долю 0.9. Фактическое: " + error.getMessage()
                        + " Иначе тест parse_FractionOutOfRange_Throws перестанет ловить эту поломку. При смене parse_FractionOutOfRange_Throws проверь это утверждение.");
    }

    @Test
    void parse_NegativeUses_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ToolReserve.parse("""
                        {"reserve_fraction": 0.05, "reserve_uses": -1, "keep_enchant_last_use": true}
                        """, ORIGIN),
                "Отрицательный запас ударов обязан быть ошибкой: он не имеет смысла"
                        + " Иначе тест parse_NegativeUses_Throws перестанет ловить эту поломку. При смене parse_NegativeUses_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("-1"),
                "Сообщение обязано называть неверный запас -1. Фактическое: " + error.getMessage()
                        + " Иначе тест parse_NegativeUses_Throws перестанет ловить эту поломку. При смене parse_NegativeUses_Throws проверь это утверждение.");
    }

    @Test
    void parse_NonBooleanKeepFlag_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ToolReserve.parse("""
                        {"reserve_fraction": 0.05, "reserve_uses": 3, "keep_enchant_last_use": "yes"}
                        """, ORIGIN),
                "Не boolean в keep_enchant_last_use обязан быть ошибкой: иначе флаг молча станет false"
                        + " При смене parse_NonBooleanKeepFlag_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("keep_enchant_last_use"),
                "Сообщение обязано называть поле keep_enchant_last_use. Фактическое: " + error.getMessage()
                        + " Иначе тест parse_NonBooleanKeepFlag_Throws перестанет ловить эту поломку. При смене parse_NonBooleanKeepFlag_Throws проверь это утверждение.");
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ToolReserve.parse("""
                        {"reserve_fraction": 0.05, "reserve_uses": 3, "keep_enchant_last_use": true, "extra": 1}
                        """, ORIGIN),
                "Неизвестное поле обязано быть ошибкой: опечатка в имени сделала бы правило мёртвым"
                        + " Иначе тест parse_UnknownField_Throws перестанет ловить эту поломку. При смене parse_UnknownField_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("extra"),
                "Сообщение обязано называть лишнее поле extra. Фактическое: " + error.getMessage()
                        + " Иначе тест parse_UnknownField_Throws перестанет ловить эту поломку. При смене parse_UnknownField_Throws проверь это утверждение.");
    }
}
