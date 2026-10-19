package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ApproachParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        ApproachParams params = ApproachParams.from(Map.of("x", 4, "y", 64, "z", 7));

        assertEquals(
                new ApproachParams(4, 64, 7),
                params,
                "approach обязан сохранить клетку цели: по ней выбирается стоянка в досягаемости");
    }

    @Test
    void from_MissingCoordinate_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ApproachParams.from(Map.of("x", 4, "y", 64)),
                "approach без координаты обязан отказывать: без клетки цели стоянку не выбрать");
    }

    @Test
    void from_XOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ApproachParams.from(Map.of("x", 30_000_001, "y", 64, "z", 7)),
                "Цель за границей мира обязана отвергаться: подойти туда нельзя");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> ApproachParams.from(null),
                "ApproachParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира");
    }
}
