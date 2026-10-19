package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmPlot;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildFarmParamsTests {
    @Test
    void from_RectangleAndCrop_ReturnsParams() {
        BuildFarmParams params = BuildFarmParams.from(Map.of("crop", "wheat", "x1", 4, "z1", 4, "x2", 6, "z2", 6));

        assertEquals(
                CropKind.WHEAT, params.crop(), "Культура обязана сохраниться: по ней дух берёт семена и вид посева");
        assertEquals(
                FarmPlot.of(4, 4, 6, 6),
                params.plot(),
                "Прямоугольник обязан сохраниться: по нему дух вспахивает и сажает");
    }

    @Test
    void from_ReversedCorners_NormalizesPlot() {
        BuildFarmParams params = BuildFarmParams.from(Map.of("crop", "carrot", "x1", 6, "z1", 6, "x2", 4, "z2", 4));

        assertEquals(
                FarmPlot.of(4, 4, 6, 6),
                params.plot(),
                "Углы в любом порядке обязаны приниматься: иначе стратег сортирует их сам");
    }

    @Test
    void from_AnyCrop_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFarmParams.from(Map.of("crop", "any", "x1", 0, "z1", 0, "x2", 2, "z2", 2)),
                "any обязан отвергаться: грядку засевают конкретным видом");
    }

    @Test
    void from_SupportCrop_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFarmParams.from(Map.of("crop", "cocoa", "x1", 0, "z1", 0, "x2", 2, "z2", 2)),
                "Какао растёт на стволе, а не на пашне: параметры обязаны отправить за plant_crop");
    }

    @Test
    void from_SideAboveLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFarmParams.from(
                        Map.of("crop", "wheat", "x1", 0, "z1", 0, "x2", CatalogLimits.FARM_PLOT_SIDE_MAX, "z2", 0)),
                "Сторона сверх предела обязана отвергаться до первого блока");
    }

    @Test
    void from_CoordinateOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFarmParams.from(Map.of(
                        "crop", "wheat", "x1", CatalogLimits.WORLD_HORIZONTAL_LIMIT + 1, "z1", 0, "x2", 0, "z2", 0)),
                "Координата вне мира обязана отвергаться: там нечего пахать");
    }

    @Test
    void catalog_Farm_CropEnumExcludesSupportCrops() {
        ScriptSpec farm = CoreScripts.defaultCatalog().find("build_farm").orElseThrow();
        ParamSpec crop = farm.params().stream()
                .filter(param -> param.name().equals("crop"))
                .findFirst()
                .orElseThrow();

        assertEquals(
                CropKind.plotNames(),
                crop.values(),
                "Каталог обязан предлагать только культуры открытого грунта: какао на пашне не растёт");
    }
}
