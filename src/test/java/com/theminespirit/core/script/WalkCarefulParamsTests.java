package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WalkCarefulParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        WalkCarefulParams params = WalkCarefulParams.from(Map.of("x", 10, "y", 64, "z", -3));

        assertEquals(
                new WalkCarefulParams(10, 64, -3),
                params,
                "walk_careful обязан сохранить точку: по ней осторожный шаг ведёт духа, проверяя обрывы");
    }

    @Test
    void from_MissingCoordinate_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WalkCarefulParams.from(Map.of("x", 10, "y", 64)),
                "walk_careful без координаты обязан отказывать: без точки скрипт не знает, куда вести духа");
    }

    @Test
    void from_YOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WalkCarefulParams.from(Map.of("x", 10, "y", 320, "z", -3)),
                "Точка выше границы мира обязана отвергаться: осторожному шагу туда не дойти");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> WalkCarefulParams.from(null),
                "WalkCarefulParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира");
    }
}
