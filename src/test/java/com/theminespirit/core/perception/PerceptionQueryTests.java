package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PerceptionQueryTests {
    static Stream<Arguments> queries() {
        return Stream.of(
                Arguments.of(PerceptionType.BLOCKS, List.of("*_ore"), ScanRadius.MIN, true),
                Arguments.of(PerceptionType.BLOCKS, List.of("minecraft:diamond_ore"), ScanRadius.MAX, true),
                Arguments.of(PerceptionType.BLOCKS, List.of(), ScanRadius.DEFAULT.value(), true),
                Arguments.of(PerceptionType.BLOCKS, List.of("*"), 8, true),
                Arguments.of(PerceptionType.ENTITIES, List.of("minecraft:cow"), 8, true),
                Arguments.of(PerceptionType.ENTITIES, List.of("*"), ScanRadius.MAX, true),
                Arguments.of(PerceptionType.HAZARDS, List.of(), ScanRadius.MIN, true),
                Arguments.of(PerceptionType.HAZARDS, List.of("*"), 8, true),
                Arguments.of(PerceptionType.HAZARDS, List.of("lava"), 8, false),
                Arguments.of(PerceptionType.BLOCKS, List.of("*_ore"), ScanRadius.MIN - 1, false),
                Arguments.of(PerceptionType.ENTITIES, List.of("*"), ScanRadius.MAX + 1, false));
    }

    @ParameterizedTest(name = "{0} {1} r={2} -> принят={3}")
    @MethodSource("queries")
    void constructor_TypeFilterRadius_AcceptedOnlyWhenConsistent(PerceptionType type, List<String> patterns,
            int radius, boolean accepted) {
        if (!accepted) {
            assertThrows(IllegalArgumentException.class,
                    () -> new PerceptionQuery(type, IdFilter.of(patterns), new ScanRadius(radius)),
                    "запрос " + type + " с фильтром " + patterns + " и радиусом " + radius
                            + " обязан отклоняться: иначе скрипт получит запрос, который не может исполнить");
            return;
        }
        PerceptionQuery query = new PerceptionQuery(type, IdFilter.of(patterns), new ScanRadius(radius));

        assertEquals(type, query.type(), "тип запроса обязан сохраняться как задан: по нему скрипт выбирает сканер");
        assertEquals(radius, query.radius().value(), "радиус запроса обязан сохраняться как задан");
        assertEquals(IdFilter.of(patterns).patterns(), query.filter().patterns(),
                "фильтр запроса обязан сохраняться как задан: иначе скан вернёт лишние блоки");
    }

    @Test
    void factories_EachType_BuildQueryOfThatType() {
        ScanRadius radius = ScanRadius.of(5);

        assertEquals(PerceptionType.BLOCKS, PerceptionQuery.blocks(IdFilter.matchAll(), radius).type(),
                "фабрика blocks обязана строить запрос BLOCKS");
        assertEquals(PerceptionType.ENTITIES, PerceptionQuery.entities(IdFilter.matchAll(), radius).type(),
                "фабрика entities обязана строить запрос ENTITIES");
        PerceptionQuery hazards = PerceptionQuery.hazards(radius);
        assertEquals(PerceptionType.HAZARDS, hazards.type(), "фабрика hazards обязана строить запрос HAZARDS");
        assertTrue(hazards.filter().matchesAll(),
                "запрос опасностей обязан нести фильтр «всё»: опасности им не отбираются");
    }

    @Test
    void constructor_NullPart_NamesMissingPart() {
        NullPointerException noType = assertThrows(NullPointerException.class,
                () -> new PerceptionQuery(null, IdFilter.matchAll(), ScanRadius.DEFAULT),
                "запрос без типа обязан отклоняться");
        assertTrue(noType.getMessage().contains("type"), "сообщение обязано называть поле type: " + noType.getMessage());
        assertThrows(NullPointerException.class,
                () -> new PerceptionQuery(PerceptionType.BLOCKS, null, ScanRadius.DEFAULT),
                "запрос без фильтра обязан отклоняться: «всё» задаётся IdFilter.matchAll()");
        assertThrows(NullPointerException.class,
                () -> new PerceptionQuery(PerceptionType.BLOCKS, IdFilter.matchAll(), null),
                "запрос без радиуса обязан отклоняться");
    }
}
