package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScriptArgsTests {
    @Test
    void optionalStringList_MissingKey_ReturnsEmpty() {
        assertEquals(Optional.empty(), ScriptArgs.optionalStringList(Map.of("radius", 8), "filter"),
                "Отсутствующий необязательный список обязан давать Optional.empty(): скрипт тогда берёт «всё»");
    }

    @Test
    void optionalStringList_EmptyList_ReturnsEmptyList() {
        assertEquals(Optional.of(List.of()), ScriptArgs.optionalStringList(Map.of("filter", List.of()), "filter"),
                "Пустой список отличается от отсутствующего параметра: оба значат «без фильтра», но не путаются на уровне типа");
    }

    @Test
    void optionalStringList_IntegerValue_NamesParameterAndType() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ScriptArgs.optionalStringList(Map.of("filter", 5), "filter"),
                "Число вместо списка обязано отвергаться");

        assertTrue(error.getMessage().contains("filter") && error.getMessage().contains("Integer"),
                "Сообщение обязано называть параметр и полученный тип, чтобы найти ошибку в плане. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void requireBoolean_MissingKey_NamesParameter() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ScriptArgs.requireBoolean(Map.of(), "replant"),
                "Отсутствующий обязательный boolean обязан отвергаться: валидатор подставляет умолчание до запуска");

        assertTrue(error.getMessage().contains("replant"),
                "Сообщение обязано называть пропущенный параметр. Фактическое: " + error.getMessage());
    }

    @Test
    void optionalInt_MissingKey_ReturnsEmpty() {
        assertEquals(Optional.empty(), ScriptArgs.optionalInt(Map.of(), "y"),
                "Отсутствующий необязательный int обязан давать Optional.empty(): скрипт тогда берёт умолчание каталога");
    }

    @Test
    void optionalInt_StringValue_NamesParameterAndType() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ScriptArgs.optionalInt(Map.of("y", "12"), "y"),
                "Строка вместо числа обязана отвергаться до запуска");

        assertTrue(error.getMessage().contains("y") && error.getMessage().contains("String"),
                "Сообщение обязано называть параметр и полученный тип, чтобы найти ошибку в плане. Фактическое: "
                        + error.getMessage());
    }
}
