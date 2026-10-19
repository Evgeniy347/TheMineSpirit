package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildFenceParamsTests {
    @Test
    void from_PointsAndBlock_ReturnsParams() {
        BuildFenceParams params = BuildFenceParams.from(
                Map.of("block", "minecraft:oak_fence", "points", List.of("0,64,0", "4,64,0", "4,64,4", "0,64,4")));

        assertEquals(
                "minecraft:oak_fence", params.block(), "Блок забора обязан сохраниться: по нему адаптер ставит столбы");
        assertEquals(
                "",
                params.gate(),
                "Без калитки остаётся проход: значение по умолчанию — пустая строка, а не случайный блок");
        assertEquals(
                List.of(
                        new BlockCoord(0, 64, 0),
                        new BlockCoord(4, 64, 0),
                        new BlockCoord(4, 64, 4),
                        new BlockCoord(0, 64, 4)),
                params.points(),
                "Точки контура обязаны читаться в порядке входа: по ним забор замыкается");
    }

    @Test
    void from_GateBlock_ReturnsGate() {
        BuildFenceParams params = BuildFenceParams.from(Map.of(
                "block", "minecraft:oak_fence",
                "gate", "minecraft:oak_fence_gate",
                "points", List.of("0,64,0", "4,64,0", "4,64,4")));

        assertEquals(
                "minecraft:oak_fence_gate",
                params.gate(),
                "Блок калитки обязан сохраниться: иначе проход останется пустым вместо двери");
    }

    @Test
    void from_BlankBlock_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFenceParams.from(Map.of("block", " ", "points", List.of("0,64,0", "4,64,0", "4,64,4"))),
                "Пустой блок забора обязан отвергаться: по пробелам материал не найти");
    }

    @Test
    void from_TooFewPoints_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFenceParams.from(Map.of("block", "oak_fence", "points", List.of("0,64,0", "4,64,0"))),
                "Двух точек мало для контура: параметры обязаны отвергать линию до старта стройки");
    }

    @Test
    void from_MalformedPoint_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFenceParams.from(
                        Map.of("block", "oak_fence", "points", List.of("0,64,0", "4;64;0", "4,64,4"))),
                "Точка не в формате x,y,z обязана отвергаться: иначе контур соберётся из мусора");
    }

    @Test
    void catalog_FenceAndShelter_DeclareSafeDefaults() {
        ScriptSpec fence = CoreScripts.defaultCatalog().find("build_fence").orElseThrow();
        ScriptSpec shelter = CoreScripts.defaultCatalog().find("build_shelter").orElseThrow();

        assertEquals(
                "",
                fence.params().stream()
                        .filter(param -> param.name().equals("gate"))
                        .findFirst()
                        .orElseThrow()
                        .defaultValue(),
                "Каталог обязан объявлять пустую калитку умолчанием: ИИ и валидатор видят одно значение");
        assertEquals(
                CatalogLimits.SHELTER_SIDE_MAX,
                shelter.params().stream()
                        .filter(param -> param.name().equals("side"))
                        .findFirst()
                        .orElseThrow()
                        .defaultValue(),
                "Каталог обязан объявлять сторону укрытия по умолчанию 4: план без size строит 4×4");
    }
}
