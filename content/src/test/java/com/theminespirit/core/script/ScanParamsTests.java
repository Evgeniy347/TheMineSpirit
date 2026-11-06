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
                16, params.radius().value(), "scan обязан принять радиус из плана: по нему выбирается куб просмотра");
        assertEquals(
                List.of("minecraft:*_ore"),
                params.filter().patterns(),
                "scan обязан превратить список шаблонов в фильтр id блоков");
    }

    @Test
    void from_FilterAbsent_ReturnsUnrestrictedFilter() {
        ScanParams params = ScanParams.from(Map.of("radius", 8));

        assertTrue(
                params.filter().matchesAll(),
                "scan без filter обязан видеть все блоки: пустой фильтр — это «без ограничений», а не «ничего»");
    }

    @Test
    void from_RadiusMissing_NamesParameter() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ScanParams.from(Map.of("filter", List.of("*_ore"))),
                "scan без radius обязан отказывать: умолчание подставляет валидатор плана, а не скрипт");

        assertTrue(
                error.getMessage().contains("radius"),
                "Отказ обязан называть параметр radius. Фактическое: " + error.getMessage());
    }

    @Test
    void from_RadiusAboveLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ScanParams.from(Map.of("radius", 33)),
                "Радиус больше ScanRadius.MAX обязан отвергаться и скриптом: защита не должна зависеть от валидатора");
    }

    @Test
    void from_BadPattern_IsRejectedWithPatternNamed() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ScanParams.from(Map.of("radius", 8, "filter", List.of("Iron Ore"))),
                "Шаблон с пробелом и заглавными обязан отвергаться при создании скрипта");

        assertTrue(
                error.getMessage().contains("Iron Ore"),
                "Отказ обязан называть плохой шаблон. Фактическое: " + error.getMessage());
    }

    @Test
    void constructor_NullFilter_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new ScanParams(ScanRadius.DEFAULT, null),
                "ScanParams без фильтра обязан отвергаться: пустой фильтр задаётся IdFilter.matchAll()");
    }
}
