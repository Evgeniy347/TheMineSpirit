package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SetCampParamsTests {
    @Test
    void from_EmptyMap_DefaultsToSpiritPositionAndDefaultRadius() {
        SetCampParams params = SetCampParams.from(Map.of());

        assertEquals(Optional.empty(), params.x(),
                "set_camp без x обязан ставить лагерь на месте духа: пустая ось — позиция духа, а не ноль мира");
        assertEquals(Optional.empty(), params.y(),
                "set_camp без y обязан ставить лагерь на месте духа: иначе лагерь уедет на дно мира");
        assertEquals(Optional.empty(), params.z(),
                "set_camp без z обязан ставить лагерь на месте духа: иначе лагерь уедет от духа");
        assertEquals(CatalogLimits.CAMP_RADIUS_DEFAULT, params.radius(),
                "set_camp без radius обязан брать умолчание каталога: разнобой радиусов лишит лагерь смысла");
    }

    @Test
    void from_FullMap_KeepsAllValues() {
        SetCampParams params = SetCampParams.from(Map.of("x", 10, "y", 64, "z", -5, "radius", 8));

        assertEquals(Optional.of(10), params.x(), "set_camp обязан сохранить x дословно: сдвиг уведёт лагерь от цели");
        assertEquals(Optional.of(64), params.y(), "set_camp обязан сохранить y дословно: сдвиг закопает лагерь");
        assertEquals(Optional.of(-5), params.z(), "set_camp обязан сохранить z дословно: сдвиг уведёт лагерь от цели");
        assertEquals(8, params.radius(), "set_camp обязан сохранить radius: иначе спокойная зона не та");
    }

    @Test
    void from_RadiusOutOfBounds_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SetCampParams.from(Map.of("radius", CatalogLimits.CAMP_RADIUS_MIN - 1)),
                "set_camp с радиусом меньше минимума обязан отказывать: нулевая зона не держит оборону");
        assertThrows(IllegalArgumentException.class,
                () -> SetCampParams.from(Map.of("radius", CatalogLimits.CAMP_RADIUS_MAX + 1)),
                "set_camp с радиусом больше максимума обязан отказывать: огромная зона — не лагерь, а провинция");
    }

    @Test
    void campRadiusBounds_MatchOwnerDecision_WithinMinMaxDefault() {
        assertEquals(1, CatalogLimits.CAMP_RADIUS_MIN,
                "Минимум радиуса лагеря — 1 (решение владельца H1): меньше — не зона, а точка");
        assertEquals(32, CatalogLimits.CAMP_RADIUS_MAX,
                "Максимум радиуса лагеря — 32 (решение владельца H1): больше — цена тика и смысла");
        assertEquals(16, CatalogLimits.CAMP_RADIUS_DEFAULT,
                "Радиус лагеря по умолчанию — 16 (решение владельца H1, как DEFENSE_RADIUS): менять — с согласия");
    }

    @Test
    void from_WrongType_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SetCampParams.from(Map.of("x", "10")),
                "set_camp со строковым x обязан отказывать: строка не превращается в координату молча");

        assertTrue(error.getMessage().contains("x"),
                "Отказ обязан называть параметр 'x'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_CoordinateOutsideWorld_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SetCampParams.from(Map.of("x", CatalogLimits.WORLD_HORIZONTAL_LIMIT + 1)),
                "set_camp с x вне мира обязан отказывать: лагерь вне мира некуда ставить");
        assertThrows(IllegalArgumentException.class,
                () -> SetCampParams.from(Map.of("y", CatalogLimits.WORLD_Y_MAX + 1)),
                "set_camp с y выше мира обязан отказывать: лагерь выше строительства некуда ставить");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> SetCampParams.from(null),
                "SetCampParams.from(null) обязан отказывать: без карты параметров маппинг только читает план");
    }
}
