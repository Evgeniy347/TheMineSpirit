package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IdFilterTests {
    @ParameterizedTest(name = "{0} ~ {1} = {2}")
    @CsvSource({
            "*_log, minecraft:oak_log, true",
            "*_log, minecraft:log_cabin, false",
            "*_log, minecraft:oak_log_slab, false",
            "iron_ore, minecraft:iron_ore, true",
            "iron_ore, minecraft:deepslate_iron_ore, false",
            "minecraft:iron_ore, iron_ore, true",
            "*iron*, minecraft:iron_ore, true",
            "*iron*, minecraft:deepslate_iron_ore, true",
            "*iron*, minecraft:gold_ore, false",
            "*_ore, minecraft:coal_ore, true",
            "*_ore, minecraft:deepslate_coal_ore, true",
            "*_ore, minecraft:ore_block, false",
            "oak_*, minecraft:oak_log, true",
            "oak_*, minecraft:spruce_log, false",
            "oak_*, minecraft:oak_, true",
            "*_log, create:fir_log, false",
            "create:*_log, create:fir_log, true",
            "create:*_log, minecraft:oak_log, false",
            "*, create:fir_log, true",
            "*, minecraft:stone, true",
            "o*k_l*g, minecraft:oak_log, true",
            "o*k_l*g, minecraft:oak_logs, false"})
    void matches_PatternAndId_FollowsGlobTable(String pattern, String id, boolean expected) {
        boolean actual = IdFilter.of(List.of(pattern)).matches(id);

        assertEquals(expected, actual,
                "Шаблон '" + pattern + "' против id '" + id + "': фильтр решает, какие блоки и предметы видит дух, "
                        + "ошибка здесь либо прячет нужное, либо тащит лишнее в добычу. Ожидалось " + expected);
    }

    @Test
    void matches_EmptyFilter_AcceptsEveryId() {
        IdFilter filter = IdFilter.of(List.of());

        assertTrue(filter.matches("minecraft:stone") && filter.matches("create:andesite_alloy"),
                "Пустой список шаблонов обязан совпадать со всем: scan без filter возвращает все блоки");
        assertTrue(filter.matchesAll(),
                "Пустой фильтр обязан сообщать, что он без ограничений: по этому признаку не пропускают секции по палитре");
    }

    @Test
    void matchesAll_StarPattern_ReportsUnrestricted() {
        assertTrue(IdFilter.of(List.of("*_log", "*")).matchesAll(),
                "Шаблон '*' среди прочих делает фильтр неограниченным: иначе секции пропускались бы по палитре зря");
    }

    @Test
    void matches_SeveralPatterns_AcceptsIdMatchingAny() {
        IdFilter filter = IdFilter.of(List.of("*_log", "*_ore"));

        assertTrue(filter.matches("minecraft:oak_log") && filter.matches("minecraft:coal_ore"),
                "Фильтр из нескольких шаблонов обязан принимать id, подходящий хотя бы под один");
        assertTrue(!filter.matches("minecraft:stone"),
                "Фильтр обязан отвергать id, не подходящий ни под один шаблон");
    }

    @Test
    void of_PatternWithoutNamespace_IsStoredWithMinecraftPrefix() {
        IdFilter filter = IdFilter.of(List.of("oak_*", "create:andesite"));

        assertEquals(List.of("minecraft:oak_*", "create:andesite"), filter.patterns(),
                "Шаблон без ':' читается как minecraft:<шаблон>: иначе модовые блоки с тем же путём попадут в добычу");
    }

    @Test
    void of_UppercasePattern_IsRejectedWithPatternInMessage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> IdFilter.of(List.of("Oak_Log")),
                "Шаблон вне [a-z0-9_:./*-] обязан отвергаться: id в реестре бывают только строчными");

        assertTrue(error.getMessage().contains("Oak_Log"),
                "Сообщение об ошибке обязано называть плохой шаблон, чтобы автор плана его нашёл. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void of_SpaceInPattern_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> IdFilter.of(List.of("oak log")),
                "Пробел в шаблоне обязан отвергаться: id блока пробелов не содержит, шаблон никогда не совпадёт");
    }

    @Test
    void of_EmptyPattern_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> IdFilter.of(List.of("")),
                "Пустой шаблон обязан отвергаться: тихое «совпадает со всем» или «ни с чем» скрыло бы ошибку плана");
    }

    @Test
    void matches_PathologicalPattern_FinishesWithoutBacktrackingExplosion() {
        String pattern = "*a*a*a*a*a*a*a*a*a*a*a*a*b";
        String id = "minecraft:" + "a".repeat(200);

        assertEquals(false, IdFilter.of(List.of(pattern)).matches(id),
                "Подстановки обязаны сопоставляться без перебора с возвратами: шаблон из плана ИИ не должен вешать тик");
    }
}
