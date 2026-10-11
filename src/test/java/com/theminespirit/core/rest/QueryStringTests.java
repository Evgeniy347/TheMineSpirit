package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryStringTests {
    @Test
    void parse_NullAndEmpty_ReturnEmptyMap() {
        assertEquals(Map.of(), QueryString.parse(null), "Запрос без строки параметров — пустая карта, а не ошибка");
        assertEquals(Map.of(), QueryString.parse(""), "Пустая строка параметров — пустая карта");
    }

    @Test
    void parse_PairsFlagsAndEncoding_AreDecoded() {
        assertEquals(Map.of("a", "1", "b", "x y", "flag", "", "t", "я"), QueryString.parse("a=1&b=x+y&flag&t=%D1%8F"),
                "Пары name=value, плюс как пробел, имя без значения и UTF-8 в %XX обязаны разбираться");
    }

    @Test
    void parse_DuplicateName_FirstValueWins() {
        assertEquals(Map.of("a", "1"), QueryString.parse("a=1&a=2"),
                "Повтор имени не должен менять значение: побеждает первое, поведение предсказуемо");
    }

    @Test
    void parse_BrokenPercentEncoding_ThrowsInvalidRequest() {
        ApiException error = assertThrows(ApiException.class, () -> QueryString.parse("a=%zz"),
                "Битая процентная запись обязана давать invalid_request, а не необработанное исключение");

        assertEquals(ErrorCode.INVALID_REQUEST, error.error().code(), "Ошибка параметров запроса — invalid_request (400)");
    }
}
