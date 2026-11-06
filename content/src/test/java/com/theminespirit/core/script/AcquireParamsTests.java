package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AcquireParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        AcquireParams params = AcquireParams.from(Map.of("item", "iron_ingot", "count", 3));

        assertEquals(
                new AcquireParams("iron_ingot", 3),
                params,
                "acquire обязан сохранить предмет и число: по ним скрипт строит подплан добычи");
    }

    @Test
    void from_MissingItem_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("count", 1)),
                "acquire без item обязан отказывать: без имени предмета скрипт не знает, что добыть");
    }

    @Test
    void from_BlankItem_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("item", "   ", "count", 1)),
                "Пустой item обязан отвергаться: пробельный id не совпадёт ни с одним предметом");
    }

    @Test
    void from_MissingCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("item", "stick")),
                "acquire без count обязан отказывать: число обязательно, цель «сколько добыть» обязана быть числовой");
    }

    @Test
    void from_LongCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("item", "stick", "count", 1L)),
                "маппинг обязан принимать только Integer: long означает, что значение не прошло валидатор плана");
    }

    @Test
    void from_CountBelowMinimum_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("item", "stick", "count", 0)),
                "count=0 обязан отвергаться: нулевая цель добычи не имеет смысла");
    }

    @Test
    void from_CountAboveMaximum_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("item", "stick", "count", 257)),
                "count больше 256 обязан отвергаться: лимит каталога не даёт плану разрастись");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> AcquireParams.from(null),
                "AcquireParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира");
    }
}
