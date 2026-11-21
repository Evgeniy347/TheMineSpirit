package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScriptInstanceTests {
    @Test
    void result_DefaultImplementation_ReturnsEmptyOptional() {
        ScriptInstance script = () -> ScriptStatus.RUNNING;

        assertTrue(
                script.result().isEmpty(),
                "Скрипт без содержательного итога обязан отдавать пустой Optional: движок отличает «итога нет» "
                        + "от «итог пустой» и не пишет null в задачу"
                        + " Иначе тест result_DefaultImplementation_ReturnsEmptyOptional перестанет ловить эту поломку. При смене result_DefaultImplementation_ReturnsEmptyOptional проверь это утверждение.");
    }

    @Test
    void result_OverriddenImplementation_ReturnsValue() {
        ScriptResult text = new ScriptResult.Text("срублено 1 из 1");
        ScriptInstance script = new ScriptInstance() {
            @Override
            public ScriptStatus tick() {
                return ScriptStatus.SUCCEEDED;
            }

            @Override
            public Optional<ScriptResult> result() {
                return Optional.of(text);
            }
        };

        assertEquals(
                Optional.of(text),
                script.result(),
                "Скрипт с содержательным итогом обязан возвращать его из result(): итог разносится до REST "
                        + "и стратега только этим каналом"
                        + " Иначе тест result_OverriddenImplementation_ReturnsValue перестанет ловить эту поломку. При смене result_OverriddenImplementation_ReturnsValue проверь это утверждение.");
    }

    @Test
    void result_DataImplementation_FieldsSurviveAsMap() {
        Map<String, Object> fields = Map.of("type", "blocks", "blocks", List.of());
        ScriptResult data = new ScriptResult.Data(fields);
        ScriptInstance script = new ScriptInstance() {
            @Override
            public ScriptStatus tick() {
                return ScriptStatus.SUCCEEDED;
            }

            @Override
            public Optional<ScriptResult> result() {
                return Optional.of(data);
            }
        };

        assertEquals(
                fields,
                script.result().orElseThrow().jsonValue(),
                "Результат восприятия обязан нести поля как карту: REST отдаёт их JSON-объектом, "
                        + "а не строкой с двойным кодированием"
                        + " Иначе тест result_DataImplementation_FieldsSurviveAsMap перестанет ловить эту поломку. При смене result_DataImplementation_FieldsSurviveAsMap проверь это утверждение.");
    }

    @Test
    void result_FailedScript_ReturnsEmptyInsteadOfThrowing() {
        ScriptInstance script = () -> ScriptStatus.FAILED;

        assertTrue(
                script.result().isEmpty(),
                "У провалившегося скрипта итога нет: вызов обязан вернуть пустой Optional, а не бросать — "
                        + "исключение здесь уронило бы серверный тик"
                        + " Иначе тест result_FailedScript_ReturnsEmptyInsteadOfThrowing перестанет ловить эту поломку. При смене result_FailedScript_ReturnsEmptyInsteadOfThrowing проверь это утверждение.");
    }
}
