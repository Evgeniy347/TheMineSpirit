package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GotoBlockParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        GotoBlockParams params = GotoBlockParams.from(Map.of("block", "hay_block", "radius", 16, "count", 3));

        assertEquals(
                new GotoBlockParams("hay_block", 16, 3),
                params,
                "goto_block обязан сохранить блок, радиус и число: по ним адаптер ищет ближайший блок вида");
    }

    @Test
    void from_MissingBlock_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GotoBlockParams.from(Map.of("radius", 16, "count", 1)),
                "goto_block без block обязан отказывать: без вида блока скрипт не знает, к чему идти");
    }

    @Test
    void from_BlankBlock_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GotoBlockParams.from(Map.of("block", "  ", "radius", 16, "count", 1)),
                "Пустой блок обязан отвергаться: пробельный id не совпадёт ни с одним блоком мира");
    }

    @Test
    void from_MissingRadius_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GotoBlockParams.from(Map.of("block", "stone", "count", 1)),
                "goto_block без радиуса обязан отказывать: валидатор плана подставляет значение по умолчанию "
                        + "из каталога до запуска, а без него скрипт не знает границу поиска");
    }

    @Test
    void from_RadiusOutOfRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GotoBlockParams.from(Map.of("block", "stone", "radius", 33, "count", 1)),
                "radius больше 32 обязан отвергаться: лимит ограничивает поиск и нагрузку на серверный тик");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> GotoBlockParams.from(null),
                "GotoBlockParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира");
    }
}
