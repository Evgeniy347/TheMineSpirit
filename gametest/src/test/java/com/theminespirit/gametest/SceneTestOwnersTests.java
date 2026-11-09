package com.theminespirit.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SceneTestOwnersTests {
    private static final String DIG_OUT = "com/theminespirit/gametest/DigOutGameTests";

    @Test
    void index_SceneTestReferencingStepOwner_MatchesOwnerFilter() {
        Map<String, Set<String>> index = SceneTestOwners.index(
                List.of(new SceneTestOwners.TestClass(
                        "MineScenesGameTests",
                        Set.of("minescene_minearenasteps_allpass"),
                        Set.of(DIG_OUT, "com/theminespirit/core/ModIdentity"))),
                Set.of(DIG_OUT));

        assertEquals(
                Set.of("minescene_minearenasteps_allpass"),
                index.get("digoutgametests"),
                "Фильтр gametest DigOutGameTests обязан находить сцену, которая гоняет его шаги @SceneStep — "
                        + "иначе docs/TESTING.md обещает то, чего нет, а прогон падает на «фильтр не совпал»");
        assertEquals(
                Set.of("minescene_minearenasteps_allpass"),
                index.get("minescenesgametests"),
                "Фильтр по самой сцене работает как раньше: имя её теста остаётся в наборе");
        assertFalse(
                index.containsKey("modidentity"),
                "Класс без @SceneStep не становится фильтром: ссылка на обычный класс не добавляет ложных имён");
    }

    @Test
    void index_ClassWithoutGameTest_IsNotAFilterByItself() {
        assertTrue(
                SceneTestOwners.index(List.of(), Set.of(DIG_OUT)).isEmpty(),
                "Класс из одних @SceneStep сам тестов не несёт: фильтр по нему ищет сцены по ссылкам, "
                        + "а не выдумывает тест с его именем");
    }

    @Test
    void index_SceneWithoutTests_DoesNotAddAnything() {
        assertTrue(
                SceneTestOwners.index(
                                List.of(new SceneTestOwners.TestClass(
                                        "DigOutGameTests",
                                        Set.of(),
                                        Set.of("com/theminespirit/gametest/MineScenesGameTests"))),
                                Set.of(DIG_OUT))
                        .isEmpty(),
                "Класс без @GameTest в индекс не попадает: иначе фильтр по собственному имени сцены превращался бы "
                        + "в пустой набор");
    }

    @Test
    void index_ReferencedOwnerName_MatchesCaseInsensitively() {
        Map<String, Set<String>> index = SceneTestOwners.index(
                List.of(new SceneTestOwners.TestClass("MoveScenesGameTests", Set.of("movescene"), Set.of(DIG_OUT))),
                Set.of(DIG_OUT));

        assertEquals(
                Set.of("movescene"),
                index.get("digoutgametests"),
                "Фильтр приходит в нижнем регистре (parseFilters): ключ индекса обязан быть в нижнем регистре, "
                        + "иначе класс шагов снова не совпадает");
    }
}
