package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockPointParamsTests {
    @Test
    void from_AllCoordinatesPresent_ReturnsPoint() {
        BlockPointParams params = BlockPointParams.from(Map.of("x", 4, "y", 64, "z", 7));

        assertEquals(
                new BlockPointParams(4, 64, 7),
                params,
                "Скрипты use_block/open/close_door обязаны сохранить клетку цели: по ней ищется блок и подход к нему");
    }

    @Test
    void from_MissingCoordinate_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BlockPointParams.from(Map.of("x", 4, "y", 64)),
                "Без координаты задачу к блоку не выполнить: параметр обязан быть обязательным");
    }

    @Test
    void from_XOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BlockPointParams.from(Map.of("x", 30_000_001, "y", 64, "z", 7)),
                "Цель за границей мира обязана отвергаться: туда не подойти и блока там нет");
    }

    @Test
    void from_YOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BlockPointParams.from(Map.of("x", 4, "y", -65, "z", 7)),
                "Клетка ниже мира обязана отвергаться: иначе дух искал бы блок вне мира");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> BlockPointParams.from(null),
                "from(null) обязан отказывать: без карты параметров взять координаты неоткуда");
    }
}
