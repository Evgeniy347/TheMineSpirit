package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryStringTests {
    @Test
    void parse_NullAndEmpty_ReturnEmptyMap() {
        assertEquals(
                Map.of(),
                QueryString.parse(null),
                "Запрос без строки параметров — пустая карта, а не ошибка"
                        + " Иначе тест parse_NullAndEmpty_ReturnEmptyMap перестанет ловить эту поломку. При смене parse_NullAndEmpty_ReturnEmptyMap проверь это утверждение.");
        assertEquals(
                Map.of(),
                QueryString.parse(""),
                "Пустая строка параметров — пустая карта"
                        + " Иначе тест parse_NullAndEmpty_ReturnEmptyMap перестанет ловить эту поломку. При смене parse_NullAndEmpty_ReturnEmptyMap проверь это утверждение.");
    }

    @Test
    void parse_PairsFlagsAndEncoding_AreDecoded() {
        assertEquals(
                Map.of("a", "1", "b", "x y", "flag", "", "t", "я"),
                QueryString.parse("a=1&b=x+y&flag&t=%D1%8F"),
                "Пары name=value, плюс как пробел, имя без значения и UTF-8 в %XX обязаны разбираться"
                        + " Иначе тест parse_PairsFlagsAndEncoding_AreDecoded перестанет ловить эту поломку. При смене parse_PairsFlagsAndEncoding_AreDecoded проверь это утверждение.");
    }

    @Test
    void parse_DuplicateName_FirstValueWins() {
        assertEquals(
                Map.of("a", "1"),
                QueryString.parse("a=1&a=2"),
                "Повтор имени не должен менять значение: побеждает первое, поведение предсказуемо"
                        + " Иначе тест parse_DuplicateName_FirstValueWins перестанет ловить эту поломку. При смене parse_DuplicateName_FirstValueWins проверь это утверждение.");
    }

    @Test
    void parse_BrokenPercentEncoding_ThrowsInvalidRequest() {
        ApiException error = assertThrows(
                ApiException.class,
                () -> QueryString.parse("a=%zz"),
                "Битая процентная запись обязана давать invalid_request, а не необработанное исключение"
                        + " Иначе тест parse_BrokenPercentEncoding_ThrowsInvalidRequest перестанет ловить эту поломку. При смене parse_BrokenPercentEncoding_ThrowsInvalidRequest проверь это утверждение.");

        assertEquals(
                ErrorCode.INVALID_REQUEST,
                error.error().code(),
                "Ошибка параметров запроса — invalid_request (400)"
                        + " Иначе тест parse_BrokenPercentEncoding_ThrowsInvalidRequest перестанет ловить эту поломку. При смене parse_BrokenPercentEncoding_ThrowsInvalidRequest проверь это утверждение.");
    }
}
