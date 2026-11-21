package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScriptPacksTests {
    private static final ScriptFactory NOOP = params -> () -> ScriptStatus.RUNNING;

    @Test
    void factories_MergePacks_InDeclaredOrder() {
        ScriptPack<String> first = pack("first", "a", "b");
        ScriptPack<String> second = pack("second", "c");

        Map<String, ScriptFactory> merged = ScriptPacks.factories(List.of(first, second), "spirit-1");

        assertEquals(
                List.of("a", "b", "c"),
                List.copyOf(merged.keySet()),
                "Порядок набора задаёт порядок скриптов: перестановка мешает сверке реестра"
                        + " Иначе тест factories_MergePacks_InDeclaredOrder перестанет ловить эту поломку. При смене factories_MergePacks_InDeclaredOrder проверь это утверждение.");
    }

    @Test
    void factories_DuplicateScript_NamesBothPacks() {
        ScriptPack<String> first = pack("first", "a");
        ScriptPack<String> second = pack("second", "a");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ScriptPacks.factories(List.of(first, second), "spirit-1"),
                "Дубль id между наборами обязан падать: иначе реестр молча возьмёт скрипт чужой механики"
                        + " При смене factories_DuplicateScript_NamesBothPacks проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("first") && error.getMessage().contains("second"),
                "При дубле скрипта сообщение обязано называть оба набора: иначе не понять, какой оставить. Текст: "
                        + error.getMessage()
                        + " При смене factories_DuplicateScript_NamesBothPacks проверь это утверждение.");
    }

    @Test
    void factories_DeclaredAndProducedMismatch_IsReported() {
        ScriptPack<String> broken = new ScriptPack<>() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public Set<String> scripts() {
                return Set.of("a", "b");
            }

            @Override
            public Map<String, ScriptFactory> factories(String owner) {
                return Map.of("a", NOOP);
            }
        };

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ScriptPacks.factories(List.of(broken), "spirit-1"),
                "Набор, объявивший скрипты и не собравший их, обязан падать: иначе граф вызовет пустое действие"
                        + " При смене factories_DeclaredAndProducedMismatch_IsReported проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("broken"),
                "Расхождение объявленных и собранных скриптов обязано называть набор. Текст: " + error.getMessage()
                        + " Иначе тест factories_DeclaredAndProducedMismatch_IsReported перестанет ловить эту поломку. При смене factories_DeclaredAndProducedMismatch_IsReported проверь это утверждение.");
    }

    @Test
    void factories_NullOwner_IsReported() {
        assertThrows(
                NullPointerException.class,
                () -> ScriptPacks.factories(List.of(pack("first", "a")), null),
                "Без владельца фабрики не замкнуть: null обязан падать, а не давать пустой реестр"
                        + " Иначе тест factories_NullOwner_IsReported перестанет ловить эту поломку. При смене factories_NullOwner_IsReported проверь это утверждение.");
    }

    @Test
    void ids_DuplicateAcrossPacks_IsReported() {
        assertThrows(
                IllegalStateException.class,
                () -> ScriptPacks.ids(List.of(pack("first", "a"), pack("second", "a"))),
                "Список id до сборки реестра тоже обязан ловить дубль: по нему строится каталог"
                        + " Иначе тест ids_DuplicateAcrossPacks_IsReported перестанет ловить эту поломку. При смене ids_DuplicateAcrossPacks_IsReported проверь это утверждение.");
    }

    @Test
    void ids_EmptyPackList_IsEmptyNotBroken() {
        assertTrue(
                ScriptPacks.ids(List.of()).isEmpty(),
                "Пустой список наборов — пустой реестр, а не ошибка: механика без скриптов возможна"
                        + " Иначе тест ids_EmptyPackList_IsEmptyNotBroken перестанет ловить эту поломку. При смене ids_EmptyPackList_IsEmptyNotBroken проверь это утверждение.");
    }

    @Test
    void factories_MultiplePacks_ContainsEveryScript() {
        Map<String, ScriptFactory> factories =
                ScriptPacks.factories(List.of(pack("first", "a", "b"), pack("second", "c")), "spirit-1");

        assertTrue(
                factories.keySet().containsAll(List.of("a", "b", "c")),
                "Сборка обязана знать каждый скрипт из наборов"
                        + " Иначе тест factories_MultiplePacks_ContainsEveryScript перестанет ловить эту поломку. При смене factories_MultiplePacks_ContainsEveryScript проверь это утверждение.");
        assertFalse(
                factories.containsKey("missing"),
                "Сборка не выдумывает скрипты, которых нет в наборах"
                        + " Иначе тест factories_MultiplePacks_ContainsEveryScript перестанет ловить эту поломку. При смене factories_MultiplePacks_ContainsEveryScript проверь это утверждение.");
    }

    private static ScriptPack<String> pack(String packId, String... ids) {
        return new ScriptPack<>() {
            @Override
            public String id() {
                return packId;
            }

            @Override
            public Set<String> scripts() {
                return Set.of(ids);
            }

            @Override
            public Map<String, ScriptFactory> factories(String owner) {
                Map<String, ScriptFactory> factories = new LinkedHashMap<>();
                for (String id : ids) {
                    factories.put(id, NOOP);
                }
                return factories;
            }
        };
    }
}
