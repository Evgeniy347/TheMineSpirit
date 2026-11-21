package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.farming.FarmPlot;
import com.theminespirit.core.farming.api.CropKind;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildFarmParamsTests {
    @Test
    void from_RectangleAndCrop_ReturnsParams() {
        BuildFarmParams params = BuildFarmParams.from(Map.of("crop", "wheat", "x1", 4, "z1", 4, "x2", 6, "z2", 6));

        assertEquals(
                CropKind.WHEAT,
                params.crop(),
                "Культура обязана сохраниться: по ней дух берёт семена и вид посева"
                        + " Иначе тест from_RectangleAndCrop_ReturnsParams перестанет ловить эту поломку. При смене from_RectangleAndCrop_ReturnsParams проверь это утверждение.");
        assertEquals(
                FarmPlot.of(4, 4, 6, 6),
                params.plot(),
                "Прямоугольник обязан сохраниться: по нему дух вспахивает и сажает"
                        + " Иначе тест from_RectangleAndCrop_ReturnsParams перестанет ловить эту поломку. При смене from_RectangleAndCrop_ReturnsParams проверь это утверждение.");
    }

    @Test
    void from_ReversedCorners_NormalizesPlot() {
        BuildFarmParams params = BuildFarmParams.from(Map.of("crop", "carrot", "x1", 6, "z1", 6, "x2", 4, "z2", 4));

        assertEquals(
                FarmPlot.of(4, 4, 6, 6),
                params.plot(),
                "Углы в любом порядке обязаны приниматься: иначе стратег сортирует их сам"
                        + " При смене from_ReversedCorners_NormalizesPlot проверь это утверждение.");
    }

    @Test
    void from_AnyCrop_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFarmParams.from(Map.of("crop", "any", "x1", 0, "z1", 0, "x2", 2, "z2", 2)),
                "any обязан отвергаться: грядку засевают конкретным видом"
                        + " Иначе тест from_AnyCrop_IsRejected перестанет ловить эту поломку. При смене from_AnyCrop_IsRejected проверь это утверждение.");
    }

    @Test
    void from_SupportCrop_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFarmParams.from(Map.of("crop", "cocoa", "x1", 0, "z1", 0, "x2", 2, "z2", 2)),
                "Какао растёт на стволе, а не на пашне: параметры обязаны отправить за plant_crop"
                        + " Иначе тест from_SupportCrop_IsRejected перестанет ловить эту поломку. При смене from_SupportCrop_IsRejected проверь это утверждение.");
    }

    @Test
    void from_SideAboveLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFarmParams.from(
                        Map.of("crop", "wheat", "x1", 0, "z1", 0, "x2", CatalogLimits.FARM_PLOT_SIDE_MAX, "z2", 0)),
                "Сторона сверх предела обязана отвергаться до первого блока"
                        + " Иначе тест from_SideAboveLimit_IsRejected перестанет ловить эту поломку. При смене from_SideAboveLimit_IsRejected проверь это утверждение.");
    }

    @Test
    void from_CoordinateOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFarmParams.from(Map.of(
                        "crop", "wheat", "x1", CatalogLimits.WORLD_HORIZONTAL_LIMIT + 1, "z1", 0, "x2", 0, "z2", 0)),
                "Координата вне мира обязана отвергаться: там нечего пахать"
                        + " Иначе тест from_CoordinateOutsideWorld_IsRejected перестанет ловить эту поломку. При смене from_CoordinateOutsideWorld_IsRejected проверь это утверждение.");
    }

    @Test
    void catalog_Farm_CropEnumExcludesSupportCrops() {
        ScriptSpec farm = CoreScripts.defaultCatalog().find("build_farm").orElseThrow();
        ParamSpec crop = farm.params().stream()
                .filter(param -> "crop".equals(param.name()))
                .findFirst()
                .orElseThrow();

        assertEquals(
                CropKind.plotNames(),
                crop.values(),
                "Каталог обязан предлагать только культуры открытого грунта: какао на пашне не растёт"
                        + " Иначе тест catalog_Farm_CropEnumExcludesSupportCrops перестанет ловить эту поломку. При смене catalog_Farm_CropEnumExcludesSupportCrops проверь это утверждение.");
    }
}
