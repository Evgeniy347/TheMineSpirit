package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FillCraterParamsTests {
    @Test
    void from_UuidValue_ParsesById() {
        UUID id = UUID.randomUUID();

        FillCraterParams params = FillCraterParams.from(Map.of("crater", id.toString()));

        assertInstanceOf(FillCraterParams.ById.class, params, "UUID в параметре обязан читаться как воронка по id");
        assertEquals(id, ((FillCraterParams.ById) params).id(), "Id воронки обязан разобраться без потерь");
    }

    @Test
    void from_CoordinateValue_ParsesByPosition() {
        FillCraterParams params = FillCraterParams.from(Map.of("crater", "10,64,-5"));

        FillCraterParams.ByPosition position = assertInstanceOf(
                FillCraterParams.ByPosition.class, params, "Три числа через запятую обязаны читаться как позиция");
        assertEquals(10, position.x(), "X позиции обязан совпасть");
        assertEquals(64, position.y(), "Y позиции обязан совпасть");
        assertEquals(-5, position.z(), "Z позиции обязан совпасть");
    }

    @Test
    void from_MissingParam_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FillCraterParams.from(Map.of()),
                "Без параметра 'crater' скрипт не знает, какую воронку засыпать: ошибка до первого шага");
    }

    @Test
    void from_NotUuidNotPosition_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FillCraterParams.from(Map.of("crater", "воронка")),
                "Строка, не UUID и не три координаты, обязана отвергаться с понятной причиной");
        assertThrows(
                IllegalArgumentException.class,
                () -> FillCraterParams.from(Map.of("crater", "10,64,x")),
                "Нецелая координата обязана отвергаться, а не читаться как 0");
    }
}
