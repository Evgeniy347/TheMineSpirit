package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.Species;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaptureParamsTests {
    @Test
    void captureLimits_ProductionConstants_MatchAgreedBounds() {
        assertEquals(1, CatalogLimits.CAPTURE_RADIUS_MIN,
                "Минимум радиуса отлова обязан быть 1: ближе животное видно всегда");
        assertEquals(16, CatalogLimits.CAPTURE_RADIUS_MAX,
                "Максимум радиуса отлова обязан быть 16: дальше животное за кормом не поведёшь");
        assertEquals(8, CatalogLimits.CAPTURE_RADIUS_DEFAULT,
                "Радиус отлова по умолчанию обязан быть 8: животное ищут вокруг духа");
        assertEquals(1200, CatalogLimits.CAPTURE_LEAD_TICKS,
                "Предел отлова приманкой — 1200 тиков (минута): за это время животное доводят до загона или "
                        + "называют причину, а не висят в RUNNING");
    }

    @Test
    void captureParams_RadiusOutsideLimits_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CaptureParams.from(Map.of("species", "sheep", "radius", 0)),
                "capture с радиусом 0 обязан отвергаться: искать животное негде");
        assertThrows(IllegalArgumentException.class,
                () -> CaptureParams.from(Map.of("species", "sheep", "radius", 17)),
                "capture с радиусом 17 обязан отвергаться: дальше животное не догнать");
        assertThrows(IllegalArgumentException.class,
                () -> CaptureParams.from(Map.of("species", "dragon", "radius", 8)),
                "capture с неизвестным видом обязан отвергаться: такого животного нет");
        assertEquals(new CaptureParams(Species.SHEEP, 8),
                CaptureParams.from(Map.of("species", "sheep", "radius", 8)),
                "capture обязан сохранять вид и радиус: по ним адаптер ищет животное и строит кольцо");
    }

    @Test
    void defaultCatalog_Capture_SpeciesRequiredRadiusBounded() {
        ScriptSpec capture = CoreScripts.defaultCatalog().find("capture").orElseThrow(() -> new AssertionError(
                "В каталоге нет скрипта capture: планировщик не сможет поставить отлов"));

        ParamSpec species = param(capture, "species");
        ParamSpec radius = param(capture, "radius");

        assertEquals(ParamType.ENUM, species.type(),
                "species в capture — enum: валидатор обязан ловить опечатку до запуска, а не искать несуществующее животное");
        assertEquals(Species.names(), species.values(),
                "Список допустимых видов обязан совпадать с Species: иначе каталог обещает один набор, а ядро знает другой");
        assertTrue(species.required(),
                "species в capture обязателен: размер кольца зависит от вида животного");
        assertEquals(false, radius.required(),
                "radius в capture необязателен: умолчание из каталога достаточно для отлова рядом");
        assertEquals(CatalogLimits.CAPTURE_RADIUS_DEFAULT, radius.defaultValue(),
                "radius в capture по умолчанию обязан совпадать с лимитом: умолчание живёт в одном месте");
        assertEquals((double) CatalogLimits.CAPTURE_RADIUS_MIN, radius.min(),
                "Нижняя граница radius в capture обязана совпадать с лимитом: границы заданы в одном месте");
        assertEquals((double) CatalogLimits.CAPTURE_RADIUS_MAX, radius.max(),
                "Верхняя граница radius в capture обязана совпадать с лимитом: границы заданы в одном месте");
    }

    private static ParamSpec param(ScriptSpec spec, String name) {
        return spec.params().stream().filter(candidate -> candidate.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("В скрипте capture нет параметра " + name));
    }
}
