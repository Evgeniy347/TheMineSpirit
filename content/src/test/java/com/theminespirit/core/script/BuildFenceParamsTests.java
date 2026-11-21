package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildFenceParamsTests {
    @Test
    void from_PointsAndBlock_ReturnsParams() {
        BuildFenceParams params = BuildFenceParams.from(
                Map.of("block", "minecraft:oak_fence", "points", List.of("0,64,0", "4,64,0", "4,64,4", "0,64,4")));

        assertEquals(
                "minecraft:oak_fence",
                params.block(),
                "Блок забора обязан сохраниться: по нему адаптер ставит столбы"
                        + " Иначе тест from_PointsAndBlock_ReturnsParams перестанет ловить эту поломку. При смене from_PointsAndBlock_ReturnsParams проверь это утверждение.");
        assertEquals(
                "",
                params.gate(),
                "Без калитки остаётся проход: значение по умолчанию — пустая строка, а не случайный блок"
                        + " Иначе тест from_PointsAndBlock_ReturnsParams перестанет ловить эту поломку. При смене from_PointsAndBlock_ReturnsParams проверь это утверждение.");
        assertEquals(
                List.of(
                        new BlockCoord(0, 64, 0),
                        new BlockCoord(4, 64, 0),
                        new BlockCoord(4, 64, 4),
                        new BlockCoord(0, 64, 4)),
                params.points(),
                "Точки контура обязаны читаться в порядке входа: по ним забор замыкается"
                        + " Иначе тест from_PointsAndBlock_ReturnsParams перестанет ловить эту поломку. При смене from_PointsAndBlock_ReturnsParams проверь это утверждение.");
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
                "Блок калитки обязан сохраниться: иначе проход останется пустым вместо двери"
                        + " При смене from_GateBlock_ReturnsGate проверь это утверждение.");
    }

    @Test
    void from_BlankBlock_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFenceParams.from(Map.of("block", " ", "points", List.of("0,64,0", "4,64,0", "4,64,4"))),
                "Пустой блок забора обязан отвергаться: по пробелам материал не найти"
                        + " Иначе тест from_BlankBlock_IsRejected перестанет ловить эту поломку. При смене from_BlankBlock_IsRejected проверь это утверждение.");
    }

    @Test
    void from_TooFewPoints_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFenceParams.from(Map.of("block", "oak_fence", "points", List.of("0,64,0", "4,64,0"))),
                "Двух точек мало для контура: параметры обязаны отвергать линию до старта стройки"
                        + " Иначе тест from_TooFewPoints_IsRejected перестанет ловить эту поломку. При смене from_TooFewPoints_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MalformedPoint_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFenceParams.from(
                        Map.of("block", "oak_fence", "points", List.of("0,64,0", "4;64;0", "4,64,4"))),
                "Точка не в формате x,y,z обязана отвергаться: иначе контур соберётся из мусора"
                        + " При смене from_MalformedPoint_IsRejected проверь это утверждение.");
    }

    @Test
    void from_TrailingCommaPoint_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildFenceParams.from(
                        Map.of("block", "oak_fence", "points", List.of("0,64,0", "4,64,0,", "4,64,4"))),
                "Точка с хвостовой запятой «4,64,0,» — опечатка, а не x,y,z: молча принятая, она прячет "
                        + "пропущенную или лишнюю координату"
                        + " Иначе тест from_TrailingCommaPoint_IsRejected перестанет ловить эту поломку. При смене from_TrailingCommaPoint_IsRejected проверь это утверждение.");
    }

    @Test
    void catalog_Fence_DeclaresEmptyGateDefault() {
        ScriptSpec fence = CoreScripts.defaultCatalog().find("build_fence").orElseThrow();

        assertEquals(
                "",
                fence.params().stream()
                        .filter(param -> "gate".equals(param.name()))
                        .findFirst()
                        .orElseThrow()
                        .defaultValue(),
                "Каталог обязан объявлять пустую калитку умолчанием: ИИ и валидатор видят одно значение"
                        + " Иначе тест catalog_Fence_DeclaresEmptyGateDefault перестанет ловить эту поломку. При смене catalog_Fence_DeclaresEmptyGateDefault проверь это утверждение.");
    }
}
