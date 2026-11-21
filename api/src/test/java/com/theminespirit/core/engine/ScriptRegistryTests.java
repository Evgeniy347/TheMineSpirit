package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScriptRegistryTests {
    @Test
    void find_KnownId_ReturnsRegisteredFactory() {
        ScriptFactory factory = TestScripts.succeeding(1);
        ScriptRegistry registry = ScriptRegistry.of(Map.of("mine", factory));

        assertSame(
                factory,
                registry.find("mine").orElseThrow(),
                "find обязан вернуть именно зарегистрированную фабрику: чужой экземпляр исполнит не тот скрипт"
                        + " Иначе тест find_KnownId_ReturnsRegisteredFactory перестанет ловить эту поломку. При смене find_KnownId_ReturnsRegisteredFactory проверь это утверждение.");
    }

    @Test
    void find_UnknownId_IsEmpty() {
        ScriptRegistry registry = ScriptRegistry.of(Map.of("mine", TestScripts.succeeding(1)));

        assertTrue(
                registry.find("ghost").isEmpty(),
                "неизвестный id обязан давать пустой Optional: движок отличит отсутствие скрипта от его наличия"
                        + " Иначе тест find_UnknownId_IsEmpty перестанет ловить эту поломку. При смене find_UnknownId_IsEmpty проверь это утверждение.");
    }

    @Test
    void contains_KnownAndUnknownIds_ReportsPresenceAndAbsence() {
        ScriptRegistry registry = ScriptRegistry.of(Map.of("mine", TestScripts.succeeding(1)));

        assertTrue(
                registry.contains("mine"),
                "contains обязан подтверждать зарегистрированный скрипт"
                        + " Иначе тест contains_KnownAndUnknownIds_ReportsPresenceAndAbsence перестанет ловить эту поломку. При смене contains_KnownAndUnknownIds_ReportsPresenceAndAbsence проверь это утверждение.");
        assertFalse(
                registry.contains("ghost"),
                "contains обязан отрицать отсутствующий скрипт"
                        + " Иначе тест contains_KnownAndUnknownIds_ReportsPresenceAndAbsence перестанет ловить эту поломку. При смене contains_KnownAndUnknownIds_ReportsPresenceAndAbsence проверь это утверждение.");
    }

    @Test
    void of_SourceMapMutatedLater_DoesNotChangeRegistry() {
        Map<String, ScriptFactory> source = new HashMap<>();
        source.put("mine", TestScripts.succeeding(1));
        ScriptRegistry registry = ScriptRegistry.of(source);

        source.clear();

        assertTrue(
                registry.contains("mine"),
                "реестр обязан делать защитную копию: иначе правка исходной карты меняет исполнение на ходу"
                        + " При смене of_SourceMapMutatedLater_DoesNotChangeRegistry проверь это утверждение.");
    }

    @Test
    void of_NullFactory_IsRejected() {
        Map<String, ScriptFactory> source = new HashMap<>();
        source.put("mine", null);

        assertThrows(
                NullPointerException.class,
                () -> ScriptRegistry.of(source),
                "реестр с null-фабрикой обязан отвергаться при создании: иначе падение случится в серверном тике"
                        + " При смене of_NullFactory_IsRejected проверь это утверждение.");
    }

    @Test
    void of_NullMap_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> ScriptRegistry.of(null),
                "реестр без карты обязан отвергаться: пустой реестр задают пустой картой"
                        + " Иначе тест of_NullMap_IsRejected перестанет ловить эту поломку. При смене of_NullMap_IsRejected проверь это утверждение.");
    }

    @Test
    void of_EmptyMap_FindsNothing() {
        ScriptRegistry registry = ScriptRegistry.of(Map.of());

        assertTrue(
                registry.find("mine").isEmpty(),
                "пустой реестр обязан находить ничего: движок тогда провалит неизвестный скрипт, а не упадёт"
                        + " Иначе тест of_EmptyMap_FindsNothing перестанет ловить эту поломку. При смене of_EmptyMap_FindsNothing проверь это утверждение.");
        assertEquals(
                false,
                registry.contains("mine"),
                "пустой реестр не содержит ни одного id"
                        + " Иначе тест of_EmptyMap_FindsNothing перестанет ловить эту поломку. При смене of_EmptyMap_FindsNothing проверь это утверждение.");
    }
}
