package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SwordTierTests {
    private static final Map<String, SwordTier> SWORDS = SwordTier.loadFile();

    @Test
    void swordTiers_ProductionTable_MatchesVanillaOrder() {
        assertEquals(
                SwordTier.WOOD,
                SwordTier.ofItem("minecraft:wooden_sword", SWORDS),
                "Деревянный меч обязан читаться из таблицы: иначе забой разрешит не тот тир и животное бьют "
                        + "слабым мечом впустую. При смене sword.json проверь SwordTier.ofItem на всех тирах");
        assertEquals(
                SwordTier.IRON,
                SwordTier.ofItem("minecraft:iron_sword", SWORDS),
                "Железный меч обязан читаться из таблицы: порог забоя считается по таблице, а не по урону, "
                        + "иначе золотой сойдёт за железный. При смене SwordTier.ofItem проверь строки IRON и GOLD");
        assertEquals(
                SwordTier.NETHERITE,
                SwordTier.ofItem("minecraft:netherite_sword", SWORDS),
                "Незеритовый меч обязан читаться из таблицы: иначе лучшему мечу откажут и забой встанет на "
                        + "ровном месте. При смене таблицы проверь живую попытку забоя незеритовым мечом");
        assertEquals(
                SwordTier.HAND,
                SwordTier.ofItem("minecraft:air", SWORDS),
                "Пустая рука обязана быть ниже дерева: руками стадо не забивают, иначе животное гибнет без "
                        + "меча и мясо теряется. При смене air в sword.json проверь порядок HAND ниже WOOD");
        assertTrue(
                SwordTier.NETHERITE.atLeast(SwordTier.IRON) && !SwordTier.GOLD.atLeast(SwordTier.IRON),
                "Порядок тиров обязан идти от руки к незериту: иначе золотой меч сойдёт за железный и забой "
                        + "разрешится там, где не должен. При смене порядка enum проверь atLeast на парах "
                        + "GOLD/IRON и HAND/WOOD");
        assertThrows(
                IllegalArgumentException.class,
                () -> SwordTier.ofItem("minecraft:diamond_axe", SWORDS),
                "Алмазный топор — не меч: тир считается по таблице, а не по урону, топору обязаны отказать, "
                        + "иначе любой инструмент развяжет забой. При смене SwordTier.ofItem проверь отказ на "
                        + "топоре и принятие настоящего меча");
    }

    @Test
    void swordTable_FileItems_MatchProduction() {
        Map<String, SwordTier> items = SWORDS;

        assertTrue(
                items.get("minecraft:iron_sword") == SwordTier.IRON
                        && items.get("minecraft:diamond_sword") == SwordTier.DIAMOND
                        && items.get("minecraft:netherite_sword") == SwordTier.NETHERITE,
                "Файл sword.json обязан покрывать боевые мечи: без файла забой запрещён вовсе, иначе стадо "
                        + "перерастает загон. При смене sword.json проверь SwordTier.loadFile и живой забой");
        assertTrue(
                items.get("minecraft:air") == SwordTier.HAND,
                "Пустая рука обязана быть в таблице ниже дерева: без записи air отказ руками не отличить от "
                        + "отсутствия меча, иначе правило порога теряет опору. При смене sword.json проверь "
                        + "запись air и SwordTier.HAND");
    }
}
