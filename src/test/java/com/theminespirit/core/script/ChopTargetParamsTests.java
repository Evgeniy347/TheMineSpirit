package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ChopTargetParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsTargetAndReplant() {
        ChopTargetParams params = ChopTargetParams.from(Map.of("x", 12, "y", 64, "z", -7, "replant", true));

        assertEquals(
                new ChopTargetParams(12, 64, -7, true),
                params,
                "chop_target обязан принять координату ствола и флаг посадки: по ним скрипт знает, какое дерево рубить "
                        + "и восстанавливать ли саженец");
    }

    @Test
    void from_ReplantFalse_KeepsTarget() {
        ChopTargetParams params = ChopTargetParams.from(Map.of("x", 1, "y", 70, "z", 2, "replant", false));

        assertEquals(
                new ChopTargetParams(1, 70, 2, false),
                params,
                "replant=false обязан сохраняться: без него дух сажал бы саженец там, где игрок не просил");
    }

    @Test
    void from_XOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChopTargetParams.from(Map.of("x", 30_000_001, "y", 64, "z", 7, "replant", false)),
                "Координата x за границей мира обязана отвергаться: ствола там нет, а рубка гнала бы духа в никуда");
    }

    @Test
    void from_ZOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChopTargetParams.from(Map.of("x", 7, "y", 64, "z", -30_000_001, "replant", false)),
                "Координата z за границей мира обязана отвергаться: ствола там нет");
    }

    @Test
    void from_YBelowWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChopTargetParams.from(Map.of("x", 7, "y", -65, "z", 7, "replant", false)),
                "Координата y ниже дна мира обязана отвергаться: дерево не растёт под бедроком");
    }

    @Test
    void from_YAboveWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChopTargetParams.from(Map.of("x", 7, "y", 320, "z", 7, "replant", false)),
                "Координата y выше потолка мира обязана отвергаться: там дерева не бывает");
    }

    @Test
    void from_MissingCoordinate_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ChopTargetParams.from(Map.of("x", 7, "y", 64, "replant", false)),
                "chop_target без координаты z обязан отказывать: без клетки ствола дерево не определить");

        assertTrue(
                error.getMessage().contains("z"),
                "Отказ обязан называть недостающий параметр 'z'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_ReplantWrongType_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChopTargetParams.from(Map.of("x", 7, "y", 64, "z", 7, "replant", "yes")),
                "replant строкой обязан быть отклонён: тип bool обеспечивает валидатор плана, "
                        + "а строку нельзя молча превратить во флаг");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> ChopTargetParams.from(null),
                "ChopTargetParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира");
    }
}
