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
                        + "зачарованного): расхождение значит, что правку не перенесли в файл или в код");
    }

    @Test
    void spent_UnwearingTool_IsFalse() {
        assertFalse(
                ToolReserve.defaults().spent(0, 0, false),
                "Инструмент без прочности (maxUses=0) обязан считаться неизношенным: иначе дух бросил бы работу "
                        + "без причины");
    }

    @Test
    void spent_ZeroRemaining_IsTrue() {
        assertTrue(
                ToolReserve.defaults().spent(0, 251, false),
                "Сломанный инструмент (0 ударов) обязан считаться изношенным: его из руки уже нет");
    }

    @Test
    void spent_BelowUsesThreshold_IsTrue() {
        assertTrue(
                ToolReserve.defaults().spent(3, 251, false),
                "Остаток 3 удара равен порогу reserve_uses=3: инструмент пора менять");
        assertFalse(
                ToolReserve.defaults().spent(14, 251, false),
                "Остаток 14 ударов выше порога (5 % от 251 = 13): менять рано");
    }

    @Test
    void spent_FractionCapWins_OnLongLastingTool() {
        assertTrue(
                ToolReserve.defaults().spent(78, 1562, false),
                "Порог 5 % от 1562 = 79 ударов перекрывает reserve_uses=3: долгий инструмент меняется по доле, "
                        + "а не по трём ударам");
        assertFalse(ToolReserve.defaults().spent(80, 1562, false), "Остаток 80 выше порога 79: менять рано");
    }

    @Test
    void spent_EnchantedLastUse_IsTrueEvenBelowFraction() {
        ToolReserve reserve = new ToolReserve(0.0, 0, true);

        assertTrue(
                reserve.spent(1, 100, true),
                "keep_enchant_last_use=true обязан считать зачарованный инструмент с последним ударом изношенным: "
                        + "последний удар тратить нельзя");
        assertFalse(reserve.spent(2, 100, true), "До последнего удара зачарованный инструмент не изношен");
    }

    @Test
    void spent_EnchantedLastUseIgnored_WhenFlagOff() {
        ToolReserve reserve = new ToolReserve(0.0, 0, false);

        assertFalse(
                reserve.spent(1, 100, true),
                "При keep_enchant_last_use=false последний удар зачарованного инструмента не защищён");
    }

    @Test
    void spent_WoodenPickaxeOneUseLeft_NotSpent() {
        assertFalse(
                ToolReserve.defaults().spent(1, 59, false, "minecraft:wooden_pickaxe", REPAIR_TIERS),
                "Деревянная кирка с последним ударом обязана остаться годной: дерево не чинят, его тратят до поломки");
    }

    @Test
    void spent_IronPickaxeAtUsesThreshold_Spent() {
        assertTrue(
                ToolReserve.defaults().spent(3, 251, false, "minecraft:iron_pickaxe", REPAIR_TIERS),
                "Железная кирка с остатком 3 удара равна порогу reserve_uses=3: уровень из repairTiers берегут под ремонт");
    }

    @Test
    void spent_StonePickaxeZeroRemaining_Spent() {
        assertTrue(
                ToolReserve.defaults().spent(0, 131, false, "minecraft:stone_pickaxe", REPAIR_TIERS),
                "Каменная кирка с нулевым остатком сломана: сломанный инструмент изношен на любом уровне");
    }

    @Test
    void spent_StoneShovelOneUseLeft_NotSpent() {
        assertFalse(
                ToolReserve.defaults().spent(1, 131, false, "minecraft:stone_shovel", REPAIR_TIERS),
                "Каменная лопата с последним ударом обязана остаться годной: камень не чинят, его тратят до поломки");
    }

    @Test
    void spent_GoldenPickaxeNearThreshold_NotSpent() {
        assertFalse(
                ToolReserve.defaults().spent(3, 33, false, "minecraft:golden_pickaxe", REPAIR_TIERS),
                "Золотая кирка с остатком 3 удара обязана остаться годной: золото не чинят, его тратят до поломки");
    }

    @Test
    void spent_NetheriteCountsAsDiamond_Spent() {
        assertTrue(
                ToolReserve.defaults().spent(3, 2032, false, "minecraft:netherite_pickaxe", REPAIR_TIERS),
                "Незеритовая кирка с остатком на пороге обязана беречься: незерит чинят как алмазный уровень");
    }

    @Test
    void spent_IronOutsideRepairTiers_NotSpent() {
        assertFalse(
                ToolReserve.defaults().spent(3, 251, false, "minecraft:iron_pickaxe", List.of("stone")),
                "Железная кирка с остатком 3 удара годна, если железа нет в repairTiers: берегут только уровни из таблицы");
    }

    @Test
    void parse_FractionOutOfRange_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ToolReserve.parse("""
                        {"reserve_fraction": 0.9, "reserve_uses": 3, "keep_enchant_last_use": true}
                        """, ORIGIN),
                "Доля больше половины прочности обязана быть ошибкой: порог 0.9 менял бы почти новый инструмент");

        assertTrue(
                error.getMessage().contains("0.9"),
                "Сообщение обязано называть неверную долю 0.9. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_NegativeUses_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ToolReserve.parse("""
                        {"reserve_fraction": 0.05, "reserve_uses": -1, "keep_enchant_last_use": true}
                        """, ORIGIN),
                "Отрицательный запас ударов обязан быть ошибкой: он не имеет смысла");

        assertTrue(
                error.getMessage().contains("-1"),
                "Сообщение обязано называть неверный запас -1. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_NonBooleanKeepFlag_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ToolReserve.parse("""
                        {"reserve_fraction": 0.05, "reserve_uses": 3, "keep_enchant_last_use": "yes"}
                        """, ORIGIN),
                "Не boolean в keep_enchant_last_use обязан быть ошибкой: иначе флаг молча станет false");

        assertTrue(
                error.getMessage().contains("keep_enchant_last_use"),
                "Сообщение обязано называть поле keep_enchant_last_use. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ToolReserve.parse("""
                        {"reserve_fraction": 0.05, "reserve_uses": 3, "keep_enchant_last_use": true, "extra": 1}
                        """, ORIGIN),
                "Неизвестное поле обязано быть ошибкой: опечатка в имени сделала бы правило мёртвым");

        assertTrue(
                error.getMessage().contains("extra"),
                "Сообщение обязано называть лишнее поле extra. Фактическое: " + error.getMessage());
    }
}
