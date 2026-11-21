package com.theminespirit.core.spirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class SpiritNamesTests {

    @Test
    void next_EmptyWorld_GivesFirstNumberedName() {
        assertEquals(
                "spirit-1",
                SpiritNames.next(Set.of()),
                "Первый дух получает имя spirit-1: иначе все духи остаются безымянными «spirit»"
                        + " При смене next_EmptyWorld_GivesFirstNumberedName проверь это утверждение.");
    }

    @Test
    void next_NumberTaken_SkipsIt() {
        assertEquals(
                "spirit-2",
                SpiritNames.next(Set.of("spirit-1")),
                "Занятый номер пропускается: два живых духа не должны называться одинаково"
                        + " Иначе тест next_NumberTaken_SkipsIt перестанет ловить эту поломку. При смене next_NumberTaken_SkipsIt проверь это утверждение.");
        assertEquals(
                "spirit-1",
                SpiritNames.next(Set.of("spirit-2")),
                "Свободный младший номер берётся раньше старшего: имена читаются по порядку"
                        + " Иначе тест next_NumberTaken_SkipsIt перестанет ловить эту поломку. При смене next_NumberTaken_SkipsIt проверь это утверждение.");
    }

    @Property(tries = 100)
    void next_AnyTakenSet_ReturnsFreeUniqueName(@ForAll @IntRange(min = 0, max = 100) int count) {
        List<String> taken = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            taken.add("spirit-" + number);
        }
        String chosen = SpiritNames.next(taken);

        assertFalse(
                taken.contains(chosen),
                "Выбранное имя " + chosen + " не повторяет занятые " + taken
                        + " Иначе тест next_AnyTakenSet_ReturnsFreeUniqueName перестанет ловить эту поломку. При смене next_AnyTakenSet_ReturnsFreeUniqueName проверь это утверждение.");
        assertTrue(
                chosen.startsWith("spirit-"),
                "Имя духа начинается с общего префикса: иначе его не узнать в журнале, выбрано " + chosen
                        + " При смене next_AnyTakenSet_ReturnsFreeUniqueName проверь это утверждение.");
        Set<String> unique = new HashSet<>(taken);
        unique.add(chosen);
        assertEquals(
                taken.size() + 1,
                unique.size(),
                "Новое имя увеличивает набор ровно на один: два духа не получают одно имя"
                        + " Иначе тест next_AnyTakenSet_ReturnsFreeUniqueName перестанет ловить эту поломку. При смене next_AnyTakenSet_ReturnsFreeUniqueName проверь это утверждение.");
    }
}
