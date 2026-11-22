package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardJsonTitleTests {
    private static BoardTask task(String title) {
        return new BoardTask(
                "rest:t-1",
                "ACTIVITY_EXTERNAL",
                95,
                "11111111-1111-1111-1111-111111111111",
                "assigned",
                Map.of(),
                Map.of(),
                List.of(),
                0L,
                1,
                "always",
                95,
                List.of(),
                false,
                0L,
                0L,
                0L,
                null,
                null,
                0,
                List.of(),
                Map.of(),
                title);
    }

    @Test
    void toMap_PresentTitle_GoesIntoBoardJson() {
        assertEquals(
                "Поход за деревом",
                BoardJson.toMap(task("Поход за деревом")).get("title"),
                "Название обязано попадать в JSON доски: иначе внешний ИИ и клиент не покажут имя задания, и "
                        + "пользователь видит только имя кейса. При смене toMap в BoardJson проверь непустое "
                        + "название");
        assertNull(
                BoardJson.toMap(task(null)).get("title"),
                "Без названия поле title остаётся null: иначе подмена пустой строкой скрыла бы имя кейса на "
                        + "плитке. При смене toMap в BoardJson проверь отсутствующее название");
    }
}
