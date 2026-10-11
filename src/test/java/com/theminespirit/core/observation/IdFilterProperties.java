package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class IdFilterProperties {
    @Provide
    Arbitrary<String> ids() {
        Arbitrary<String> namespace = Arbitraries.strings().withCharRange('a', 'z').numeric().withChars('_')
                .ofMinLength(1).ofMaxLength(8);
        Arbitrary<String> path = Arbitraries.strings().withCharRange('a', 'z').numeric().withChars('_', '/', '.', '-')
                .ofMinLength(1).ofMaxLength(16);
        return Combinators.combine(namespace, path).as((ns, p) -> ns + ":" + p);
    }

    @Property
    void matches_PatternWithoutStar_MatchesOnlyItself(@ForAll("ids") String pattern, @ForAll("ids") String other) {
        IdFilter filter = IdFilter.of(List.of(pattern));

        assertEquals(pattern.equals(other), filter.matches(other),
                "Шаблон без '*' обязан совпадать только с самим собой: '" + pattern + "' против '" + other + "'");
        assertEquals(true, filter.matches(pattern),
                "Шаблон без '*' обязан совпадать с собственным id: иначе точное имя в плане ничего не найдёт");
    }

    @Property
    void matches_SingleStar_MatchesAnyId(@ForAll("ids") String id) {
        assertEquals(true, IdFilter.of(List.of("*")).matches(id),
                "Шаблон '*' обязан совпадать с любым id: '" + id + "' не прошёл");
    }

    @Property
    void matches_PrefixStar_MatchesExactlyIdsWithPrefix(@ForAll("ids") String prefix, @ForAll("ids") String id) {
        IdFilter filter = IdFilter.of(List.of(prefix + "*"));

        assertEquals(id.startsWith(prefix), filter.matches(id),
                "Шаблон '" + prefix + "*' обязан совпадать ровно с id, начинающимися на префикс; id '" + id + "'");
    }

    @Property
    void matches_PrefixStar_MatchesIdBuiltFromPrefix(@ForAll("ids") String prefix, @ForAll("ids") String tail) {
        String id = prefix + tail;

        assertEquals(true, IdFilter.of(List.of(prefix + "*")).matches(id),
                "Шаблон '" + prefix + "*' обязан совпадать с любым id, начинающимся на префикс: '" + id + "' не прошёл");
    }
}
