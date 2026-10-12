package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.pickup.OverflowMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollectDropsParamsTests {
    @Test
    void from_RadiusAndFilter_ReturnsParsedValues() {
        CollectDropsParams params = CollectDropsParams.from(Map.of("radius", 6, "filter", List.of("*_log")));

        assertEquals(6, params.radius(), "collect_drops обязан принять радиус из плана");
        assertTrue(params.items().matches("minecraft:oak_log") && !params.items().matches("minecraft:cobblestone"),
                "filter collect_drops обязан отбирать предметы по id: булыжник при фильтре *_log подбирать нельзя");
    }

    @Test
    void from_FilterAbsent_AcceptsEveryItem() {
        CollectDropsParams params = CollectDropsParams.from(Map.of("radius", 8));

        assertTrue(params.items().matchesAll(),
                "collect_drops без filter обязан подбирать всё: пустой фильтр не означает «ничего»");
    }

    @Test
    void constructor_RadiusOutsideLimits_IsRejectedOnBothSides() {
        assertThrows(IllegalArgumentException.class, () -> CollectDropsParams.from(Map.of("radius", 0)),
                "Радиус 0 обязан отвергаться: границы каталога DROP_RADIUS_MIN..MAX защищают и сам скрипт");
        assertThrows(IllegalArgumentException.class, () -> CollectDropsParams.from(Map.of("radius", 17)),
                "Радиус 17 обязан отвергаться: подбор дальше 16 блоков гонит духа через полкарты");
    }

    @Test
    void from_OverflowAbsent_DefaultsToByValue() {
        CollectDropsParams params = CollectDropsParams.from(Map.of("radius", 6));

        assertEquals(OverflowMode.BY_VALUE, params.overflow(),
                "collect_drops без overflow обязан брать ценное, выбрасывая дешёвое: умолчание by_value "
                        + "задано каталогом, а прямой вызов без валидатора обязан давать то же");
    }

    @Test
    void from_OverflowFail_ParsesExplicitRefusal() {
        CollectDropsParams params = CollectDropsParams.from(Map.of("radius", 6, "overflow", "fail"));

        assertEquals(OverflowMode.FAIL, params.overflow(),
                "Явный overflow fail обязан сохранять прежнее поведение отказа при полном инвентаре");
    }

    @Test
    void from_UnknownOverflow_NamesAllowedValues() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CollectDropsParams.from(Map.of("radius", 6, "overflow", "cheapest")),
                "Неизвестный режим переполнения обязан отклоняться: иначе дух молча брал бы поведение по умолчанию");

        assertTrue(error.getMessage().contains("fail") && error.getMessage().contains("by_value"),
                "Отказ обязан перечислять допустимые значения. Фактическое: " + error.getMessage());
    }

    @Test
    void from_RadiusMissing_NamesParameter() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CollectDropsParams.from(Map.of()),
                "collect_drops без radius обязан отказывать: умолчание подставляет валидатор плана");

        assertTrue(error.getMessage().contains("radius"),
                "Отказ обязан называть параметр radius. Фактическое: " + error.getMessage());
    }
}
