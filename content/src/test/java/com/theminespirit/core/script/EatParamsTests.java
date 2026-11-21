package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EatParamsTests {
    @Test
    void from_NoItem_EmptyOptional() {
        assertEquals(
                Optional.empty(),
                EatParams.from(Map.of()).item(),
                "Без item дух ест лучшее из инвентаря: пустой параметр — это «что найдёшь», а не ошибка"
                        + " Иначе тест from_NoItem_EmptyOptional перестанет ловить эту поломку. При смене from_NoItem_EmptyOptional проверь это утверждение.");
    }

    @Test
    void from_BlankItem_EmptyOptional() {
        assertEquals(
                Optional.empty(),
                EatParams.from(Map.of("item", "  ")).item(),
                "Пустое имя еды равнозначно отсутствию: планировщик может прислать пустую строку"
                        + " Иначе тест from_BlankItem_EmptyOptional перестанет ловить эту поломку. При смене from_BlankItem_EmptyOptional проверь это утверждение.");
    }

    @Test
    void from_NamedItem_KeepsId() {
        assertEquals(
                Optional.of("minecraft:bread"),
                EatParams.from(Map.of("item", "minecraft:bread")).item(),
                "Названный предмет сохраняется: дух ест именно его, а не лучшее"
                        + " Иначе тест from_NamedItem_KeepsId перестанет ловить эту поломку. При смене from_NamedItem_KeepsId проверь это утверждение.");
    }

    @Test
    void from_NonTextItem_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EatParams.from(Map.of("item", 5)),
                "Число вместо id еды отвергается: валидатор плана обязан поймать тип до запуска"
                        + " Иначе тест from_NonTextItem_Throws перестанет ловить эту поломку. При смене from_NonTextItem_Throws проверь это утверждение.");
    }

    @Test
    void constructor_BlankPresent_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new EatParams(Optional.of(" ")),
                "Пустая строка как заданная еда отвергается: иначе дух искал бы предмет с пустым именем"
                        + " При смене constructor_BlankPresent_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("еды"),
                "Сообщение называет еду: " + error.getMessage()
                        + " Иначе тест constructor_BlankPresent_Throws перестанет ловить эту поломку. При смене constructor_BlankPresent_Throws проверь это утверждение.");
    }
}
