package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.ScanRadius;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScanParamsTests {
    @Test
    void from_RadiusAndFilter_ReturnsParsedValues() {
        ScanParams params = ScanParams.from(Map.of("radius", 16, "filter", List.of("*_ore")));

        assertEquals(
                16,
                params.radius().value(),
                "scan обязан принять радиус из плана: по нему выбирается куб просмотра"
                        + " Иначе тест from_RadiusAndFilter_ReturnsParsedValues перестанет ловить эту поломку. При смене from_RadiusAndFilter_ReturnsParsedValues проверь это утверждение.");
        assertEquals(
                List.of("minecraft:*_ore"),
                params.filter().patterns(),
                "scan обязан превратить список шаблонов в фильтр id блоков"
                        + " Иначе тест from_RadiusAndFilter_ReturnsParsedValues перестанет ловить эту поломку. При смене from_RadiusAndFilter_ReturnsParsedValues проверь это утверждение.");
    }

    @Test
    void from_FilterAbsent_ReturnsUnrestrictedFilter() {
        ScanParams params = ScanParams.from(Map.of("radius", 8));

        assertTrue(
                params.filter().matchesAll(),
                "scan без filter обязан видеть все блоки: пустой фильтр — это «без ограничений», а не «ничего»"
                        + " Иначе тест from_FilterAbsent_ReturnsUnrestrictedFilter перестанет ловить эту поломку. При смене from_FilterAbsent_ReturnsUnrestrictedFilter проверь это утверждение.");
    }

    @Test
    void from_RadiusMissing_NamesParameter() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ScanParams.from(Map.of("filter", List.of("*_ore"))),
                "scan без radius обязан отказывать: умолчание подставляет валидатор плана, а не скрипт"
                        + " Иначе тест from_RadiusMissing_NamesParameter перестанет ловить эту поломку. При смене from_RadiusMissing_NamesParameter проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("radius"),
                "Отказ обязан называть параметр radius. Фактическое: " + error.getMessage()
                        + " Иначе тест from_RadiusMissing_NamesParameter перестанет ловить эту поломку. При смене from_RadiusMissing_NamesParameter проверь это утверждение.");
    }

    @Test
    void from_RadiusAboveLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ScanParams.from(Map.of("radius", 33)),
                "Радиус больше ScanRadius.MAX обязан отвергаться и скриптом: защита не должна зависеть от валидатора"
                        + " Иначе тест from_RadiusAboveLimit_IsRejected перестанет ловить эту поломку. При смене from_RadiusAboveLimit_IsRejected проверь это утверждение.");
    }

    @Test
    void from_BadPattern_IsRejectedWithPatternNamed() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ScanParams.from(Map.of("radius", 8, "filter", List.of("Iron Ore"))),
                "Шаблон с пробелом и заглавными обязан отвергаться при создании скрипта"
                        + " Иначе тест from_BadPattern_IsRejectedWithPatternNamed перестанет ловить эту поломку. При смене from_BadPattern_IsRejectedWithPatternNamed проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Iron Ore"),
                "Отказ обязан называть плохой шаблон. Фактическое: " + error.getMessage()
                        + " Иначе тест from_BadPattern_IsRejectedWithPatternNamed перестанет ловить эту поломку. При смене from_BadPattern_IsRejectedWithPatternNamed проверь это утверждение.");
    }

    @Test
    void constructor_NullFilter_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new ScanParams(ScanRadius.DEFAULT, null),
                "ScanParams без фильтра обязан отвергаться: пустой фильтр задаётся IdFilter.matchAll()"
                        + " Иначе тест constructor_NullFilter_IsRejected перестанет ловить эту поломку. При смене constructor_NullFilter_IsRejected проверь это утверждение.");
    }
}
