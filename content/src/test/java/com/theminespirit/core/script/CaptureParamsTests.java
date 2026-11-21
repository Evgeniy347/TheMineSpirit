package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.api.Species;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ParamType;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaptureParamsTests {
    @Test
    void captureLimits_ProductionConstants_MatchAgreedBounds() {
        assertEquals(
                1,
                CatalogLimits.CAPTURE_RADIUS_MIN,
                "Минимум радиуса отлова обязан быть 1: ближе животное видно всегда"
                        + " Иначе тест captureLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене captureLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                16,
                CatalogLimits.CAPTURE_RADIUS_MAX,
                "Максимум радиуса отлова обязан быть 16: дальше животное за кормом не поведёшь"
                        + " Иначе тест captureLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене captureLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                8,
                CatalogLimits.CAPTURE_RADIUS_DEFAULT,
                "Радиус отлова по умолчанию обязан быть 8: животное ищут вокруг духа"
                        + " Иначе тест captureLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене captureLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                1200,
                CatalogLimits.CAPTURE_LEAD_TICKS,
                "Предел отлова приманкой — 1200 тиков (минута): за это время животное доводят до загона или "
                        + "называют причину, а не висят в RUNNING"
                        + " Иначе тест captureLimits_ProductionConstants_MatchAgreedBounds перестанет ловить эту поломку. При смене captureLimits_ProductionConstants_MatchAgreedBounds проверь это утверждение.");
    }

    @Test
    void captureParams_RadiusOutsideLimits_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CaptureParams.from(Map.of("species", "sheep", "radius", 0)),
                "capture с радиусом 0 обязан отвергаться: искать животное негде"
                        + " Иначе тест captureParams_RadiusOutsideLimits_IsRejected перестанет ловить эту поломку. При смене captureParams_RadiusOutsideLimits_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> CaptureParams.from(Map.of("species", "sheep", "radius", 17)),
                "capture с радиусом 17 обязан отвергаться: дальше животное не догнать"
                        + " Иначе тест captureParams_RadiusOutsideLimits_IsRejected перестанет ловить эту поломку. При смене captureParams_RadiusOutsideLimits_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> CaptureParams.from(Map.of("species", "dragon", "radius", 8)),
                "capture с неизвестным видом обязан отвергаться: такого животного нет"
                        + " Иначе тест captureParams_RadiusOutsideLimits_IsRejected перестанет ловить эту поломку. При смене captureParams_RadiusOutsideLimits_IsRejected проверь это утверждение.");
        assertEquals(
                new CaptureParams(Species.SHEEP, 8),
                CaptureParams.from(Map.of("species", "sheep", "radius", 8)),
                "capture обязан сохранять вид и радиус: по ним адаптер ищет животное и строит кольцо"
                        + " Иначе тест captureParams_RadiusOutsideLimits_IsRejected перестанет ловить эту поломку. При смене captureParams_RadiusOutsideLimits_IsRejected проверь это утверждение.");
    }

    @Test
    void defaultCatalog_Capture_SpeciesRequiredRadiusBounded() {
        ScriptSpec capture = CoreScripts.defaultCatalog()
                .find("capture")
                .orElseThrow(() ->
                        new AssertionError("В каталоге нет скрипта capture: планировщик не сможет поставить отлов"));

        ParamSpec species = param(capture, "species");
        ParamSpec radius = param(capture, "radius");

        assertEquals(
                ParamType.ENUM,
                species.type(),
                "species в capture — enum: валидатор обязан ловить опечатку до запуска, а не искать несуществующее животное"
                        + " Иначе тест defaultCatalog_Capture_SpeciesRequiredRadiusBounded перестанет ловить эту поломку. При смене defaultCatalog_Capture_SpeciesRequiredRadiusBounded проверь это утверждение.");
        assertEquals(
                Species.names(),
                species.values(),
                "Список допустимых видов обязан совпадать с Species: иначе каталог обещает один набор, а ядро знает другой"
                        + " При смене defaultCatalog_Capture_SpeciesRequiredRadiusBounded проверь это утверждение.");
        assertTrue(
                species.required(),
                "species в capture обязателен: размер кольца зависит от вида животного"
                        + " Иначе тест defaultCatalog_Capture_SpeciesRequiredRadiusBounded перестанет ловить эту поломку. При смене defaultCatalog_Capture_SpeciesRequiredRadiusBounded проверь это утверждение.");
        assertEquals(
                false,
                radius.required(),
                "radius в capture необязателен: умолчание из каталога достаточно для отлова рядом"
                        + " Иначе тест defaultCatalog_Capture_SpeciesRequiredRadiusBounded перестанет ловить эту поломку. При смене defaultCatalog_Capture_SpeciesRequiredRadiusBounded проверь это утверждение.");
        assertEquals(
                CatalogLimits.CAPTURE_RADIUS_DEFAULT,
                radius.defaultValue(),
                "radius в capture по умолчанию обязан совпадать с лимитом: умолчание живёт в одном месте"
                        + " Иначе тест defaultCatalog_Capture_SpeciesRequiredRadiusBounded перестанет ловить эту поломку. При смене defaultCatalog_Capture_SpeciesRequiredRadiusBounded проверь это утверждение.");
        assertEquals(
                (double) CatalogLimits.CAPTURE_RADIUS_MIN,
                radius.min(),
                "Нижняя граница radius в capture обязана совпадать с лимитом: границы заданы в одном месте"
                        + " Иначе тест defaultCatalog_Capture_SpeciesRequiredRadiusBounded перестанет ловить эту поломку. При смене defaultCatalog_Capture_SpeciesRequiredRadiusBounded проверь это утверждение.");
        assertEquals(
                (double) CatalogLimits.CAPTURE_RADIUS_MAX,
                radius.max(),
                "Верхняя граница radius в capture обязана совпадать с лимитом: границы заданы в одном месте"
                        + " Иначе тест defaultCatalog_Capture_SpeciesRequiredRadiusBounded перестанет ловить эту поломку. При смене defaultCatalog_Capture_SpeciesRequiredRadiusBounded проверь это утверждение.");
    }

    private static ParamSpec param(ScriptSpec spec, String name) {
        return spec.params().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("В скрипте capture нет параметра " + name));
    }
}
